package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 怪物伤害来源检测(m189)——外模组伤害不作数,只认原版和永夜的武器。
 *
 * <p>规则(只对「怪物」= Monster 生效,玩家/动物/村民不受影响):
 * <ul>
 *   <li>无攻击者的环境伤害(摔落 / 岩浆 / 仙人掌 / 药水残留 / {@code /kill})一律放行——那是原版机制。</li>
 *   <li>攻击者是<b>玩家</b>:看造成这次伤害的<b>武器</b>(1.21 伤害源自带武器栈,拿不到就兜底主手物品)。
 *       命名空间是 {@code minecraft} / {@code yongye}(或配置里额外放行的)才作数,否则伤害整个取消,
 *       并给玩家 action bar 提示(可关)。空手 = {@code minecraft:air},照常有效。</li>
 *   <li>攻击者是<b>非玩家实体</b>(外模组的召唤物 / 宠物 / 炮塔等):看攻击者实体类型的命名空间,同样只认
 *       原版 / 永夜 / 白名单。原版狼、铁傀儡、怪物内斗不受影响。</li>
 * </ul>
 *
 * <p>已知取舍(有意为之 / 记录在案):
 * <ul>
 *   <li>玩家<b>手持外模组武器</b>期间,连职业技能反伤这类「借玩家名义」的伤害也会被判无效
 *       (伤害源武器 = 主手)——与「拿外模组武器这刀就不算」的规则一致。</li>
 *   <li>极少数「借玩家名义、空手也能造成伤害」的外模组法术会被当成空手放行;要堵死得再查伤害类型
 *       命名空间,等实测确有需要再加。</li>
 * </ul>
 */
public final class ForeignDamageFilterHandler {
    private ForeignDamageFilterHandler() {}

    /**
     * m190:外模组武器打怪被判无效时,怪物开口嘲讽的内置台词池(聊天栏,随机抽一句)。
     * 风格照作者示例「哎呦喂,您拿前朝的剑,斩本朝的官?」;
     * 想追加台词不用改代码——配置 foreignDamageTauntExtraLines 用竖线 | 分隔即可。
     */
    private static final String[] TAUNTS = {
            "哎呦喂,您这是拿前朝的剑,斩本朝的官呐?",
            "此兵器没上永夜的户口,恕不接招。",
            "客官,您这家伙什海关都没过,就想通关我?",
            "外来的和尚好念经,外来的刀可砍不动我。",
            "啧,异界的破铜烂铁也敢往我身上招呼?",
            "水土不服啊朋友,这武器一进永夜就蔫了。",
            "别费劲了,这玩意儿在永夜连烧火棍都不如。",
            "拿错剧本了吧?那是隔壁世界的道具。",
            "我身披永夜结界,专防三无兵器。",
            "就这?我痒痒肉都没被你找着。",
            "您这刀锋利是锋利,可惜签证过期了。",
            "永夜规矩:兵器不认,伤害不算。",
            "好家伙,跨服砍人呢?这里不兴这个。",
            "嘶——好凉快,原来是你在给我扇风啊。",
            "大侠,先去铁匠铺把这铁片子落个户再来。",
            "别敲了别敲了,跟拿羽毛挠我似的。",
            "异界神兵?到了永夜也就是根牙签。",
            "劝你换把本地的家伙,这把是真不疼。",
            "您这一下,比蚊子叮还温柔。",
            "回去问问你那武器:知道这是谁的地盘吗?"
    };

    /** 嘲讽冷却:每玩家上次被嘲讽的世界时间(transient,套路照 ClassSkillHandler.lastCombat)。 */
    private static final Map<UUID, Long> LAST_TAUNT = new HashMap<>();

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableForeignDamageFilter) return true;
            if (!(entity instanceof Monster)) return true;   // 只管怪物(判法同 LootHandler/MobEnhancementHandler)
            Entity attacker = source.getAttacker();
            if (attacker == null) return true;               // 环境伤害不拦

            // ① 玩家出手:看武器命名空间。
            //   getWeaponStack()【待编译验证】= yarn 1.21.1 官方 mapping method_60948,近战/弹射物都会填武器;
            //   拿不到(老式伤害源)就兜底看主手。
            if (attacker instanceof PlayerEntity player) {
                ItemStack weapon = source.getWeaponStack();
                if (weapon == null || weapon.isEmpty()) {
                    weapon = player.getMainHandStack();
                }
                String ns = Registries.ITEM.getId(weapon.getItem()).getNamespace();
                if (isAllowedNamespace(cfg, ns)) return true;
                if (player instanceof ServerPlayerEntity sp) {
                    if (cfg.foreignDamageTaunt) taunt(cfg, entity, sp);       // m190:怪物开口嘲讽(聊天栏,带冷却)
                    if (cfg.foreignDamageFilterHint) {
                        sp.sendMessage(Text.literal("外来模组武器对怪物无效(只认原版 / 永夜武器)")
                                .formatted(Formatting.GRAY), true);
                    }
                }
                return false;
            }

            // ② 非玩家实体出手:看攻击者实体类型命名空间(写法同 EliteHandler/MobBossHandler 的自家怪判定)。
            String ns = Registries.ENTITY_TYPE.getId(attacker.getType()).getNamespace();
            return isAllowedNamespace(cfg, ns);
        });
        Yongye.LOGGER.info("[永夜] 怪物伤害来源检测已挂载(外模组伤害不作数,只认原版/永夜武器)");
    }

    /** minecraft / yongye 恒放行;配置 foreignDamageFilterExtraNamespaces 里逗号分隔的命名空间额外放行。 */
    private static boolean isAllowedNamespace(YongyeConfig cfg, String ns) {
        if ("minecraft".equals(ns) || Yongye.MOD_ID.equals(ns)) return true;
        String extra = cfg.foreignDamageFilterExtraNamespaces;
        if (extra != null && !extra.isBlank()) {
            for (String s : extra.split(",")) {
                if (ns.equals(s.trim())) return true;
            }
        }
        return false;
    }

    /**
     * m190:怪物开口嘲讽——外模组武器这一刀被判无效后,怪物在聊天栏对攻击者说一句风凉话。
     * 格式:「怪物名」+ 台词;名字走 mob.getName()(与 BOSS 化改名兼容,BOSS 版会带【BOSS】前缀说话)。
     * 每玩家带冷却(foreignDamageTauntCooldownTicks,默 60t = 3 秒),防连点刷屏;
     * action bar 那条灰字机制提示与本嘲讽双轨并存,各自有开关。
     */
    private static void taunt(YongyeConfig cfg, LivingEntity mob, ServerPlayerEntity sp) {
        long now = sp.getWorld().getTime();
        Long last = LAST_TAUNT.get(sp.getUuid());
        if (last != null && now - last < Math.max(0, cfg.foreignDamageTauntCooldownTicks)) return;
        LAST_TAUNT.put(sp.getUuid(), now);

        // 台词池 = 内置 20 条 + 配置追加(竖线 | 分隔;台词里含中文逗号/问号无碍,别用竖线即可)
        List<String> pool = new ArrayList<>(Arrays.asList(TAUNTS));
        String extra = cfg.foreignDamageTauntExtraLines;
        if (extra != null && !extra.isBlank()) {
            for (String s : extra.split("\\|")) {
                String t = s.trim();
                if (!t.isEmpty()) pool.add(t);
            }
        }
        String line = pool.get(sp.getRandom().nextInt(pool.size()));

        sp.sendMessage(Text.literal("「").formatted(Formatting.DARK_GRAY)
                .append(mob.getName().copy().formatted(Formatting.RED))
                .append(Text.literal("」").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(" " + line).formatted(Formatting.YELLOW)), false);
    }
}
