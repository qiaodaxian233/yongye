package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 怪物伤害来源检测(m189 起)——外模组伤害不作数,只认原版和永夜的武器。
 *
 * <p>规则(只对「怪物」= Monster 生效,玩家/动物/村民不受影响):
 * <ul>
 *   <li>无攻击者的原版环境伤害(摔落 / 岩浆 / 仙人掌 / 药水残留 / {@code /kill})一律放行。</li>
 *   <li>攻击者是<b>玩家</b>:看造成这次伤害的<b>武器</b>(1.21 伤害源自带武器栈,拿不到就兜底主手)。
 *       命名空间是 {@code minecraft} / {@code yongye}(或配置额外放行的)才作数,否则伤害整个取消,
 *       action bar 提示 + 怪物开口嘲讽(均可关)。空手 = {@code minecraft:air},照常有效。</li>
 *   <li>攻击者是<b>非玩家实体</b>(外模组召唤物 / 宠物 / 炮塔):看攻击者实体类型的命名空间,同样只认
 *       原版 / 永夜 / 白名单。原版狼、铁傀儡、怪物内斗不受影响。</li>
 *   <li>另看<b>伤害类型命名空间</b>(如 AvaritiaNeo 的 {@code avaritia:infinity}):外模组自定义伤害类型
 *       即便攻击者判不出,也按外来处理。</li>
 * </ul>
 *
 * <p><b>m191 关键修复——秒杀类武器绕过 {@code damage()} 的问题</b>:
 * 经核实 AvaritiaNeo「无限剑」的击杀链是
 * {@code entity.hurt(源, Float.MAX); entity.setHealth(0); entity.die(源);}——
 * 后两句<b>直接改血 / 直接触发死亡,根本不走 {@code damage()}</b>,所以只挂 {@code ALLOW_DAMAGE} 拦不住
 * (伤害那句被我取消了,可怪照样被 setHealth(0)+die() 弄死)。故本类<b>同时挂 {@code ALLOW_DEATH}</b>:
 * 怪物因外模组来源死亡时取消死亡,并把血抬回(套路照 {@link EndDragonHandler} 三命复活;回调内必须把血弄到 &gt;0)。
 *
 * <p>已知取舍(有意为之 / 记录在案):
 * <ul>
 *   <li>玩家<b>手持外模组武器</b>期间,连职业技能反伤这类「借玩家名义」的伤害也判无效(伤害源武器=主手),
 *       与「拿外模组武器这刀就不算」的规则一致。</li>
 *   <li>怪物因外模组来源「被强杀」时会满血/回原血复活,这是刻意行为——外模组武器杀不死永夜的怪。</li>
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

    /**
     * m191:被外模组秒杀类武器强杀前的血量快照。
     * 秒杀链里 {@code hurt()}(→ ALLOW_DAMAGE 记快照)紧接着 {@code setHealth(0)+die()}(→ ALLOW_DEATH 读快照复原),
     * 同一 tick 内完成,故用「tick + 血量」快照把血抬回被打前的值(保留此前的合法伤害),而不是一律满血。
     */
    private record HpSnapshot(long tick, float health) {}
    private static final Map<UUID, HpSnapshot> PRE_KILL_HP = new HashMap<>();

    public static void register() {
        // ⓪ 近战前置(m193,照作者「看手持是不是原版/永夜就行」的思路):
        //    玩家左键攻击怪物的瞬间就看主手——不是原版/永夜/白名单,直接 FAIL 掉整次攻击。
        //    好处:连无限剑那种「自定义击杀逻辑(setHealth(0)+die())」都还没来得及跑,比 ①② 事后补救更干净。
        //    注意:只挡「近战左键」;弓/枪/法术发射的投射物不走这里,仍靠 ①(伤害)②(死亡)兜底。
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableForeignDamageFilter) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity living) || !(entity instanceof Monster)) return ActionResult.PASS;
            String ns = Registries.ITEM.getId(player.getMainHandStack().getItem()).getNamespace();
            if (isAllowedNamespace(cfg, ns)) return ActionResult.PASS;   // 原版/永夜/白名单 → 放行正常攻击
            // 外模组手持:取消这次近战。服务端补嘲讽 + 提示(客户端只负责取消预测)。
            if (!world.isClient && player instanceof ServerPlayerEntity sp) {
                if (cfg.foreignDamageTaunt) taunt(cfg, living, sp);
                if (cfg.foreignDamageFilterHint) {
                    sp.sendMessage(Text.literal("外来模组武器对怪物无效(只认原版 / 永夜武器)")
                            .formatted(Formatting.GRAY), true);
                }
            }
            return ActionResult.FAIL;
        });

        // ① 伤害路径:凡走 damage() 的外模组伤害,直接取消(m189;弓箭/法术/自定义伤害类型都在这拦)。
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableForeignDamageFilter) return true;
            if (!(entity instanceof Monster)) return true;         // 只管怪物(判法同 LootHandler/MobEnhancementHandler)
            if (!isForeignToMonster(cfg, source)) return true;     // 原版 / 永夜 / 白名单 → 放行

            // 是外模组伤害:记录当前血量(供 ② 复原),提示 + 嘲讽,取消这次伤害。
            PRE_KILL_HP.put(entity.getUuid(), new HpSnapshot(entity.getWorld().getTime(), entity.getHealth()));
            if (source.getAttacker() instanceof ServerPlayerEntity sp) {
                if (cfg.foreignDamageTaunt) taunt(cfg, entity, sp);
                if (cfg.foreignDamageFilterHint) {
                    sp.sendMessage(Text.literal("外来模组武器对怪物无效(只认原版 / 永夜武器)")
                            .formatted(Formatting.GRAY), true);
                }
            }
            return false;
        });

        // ② 死亡路径(m191 修复):AvaritiaNeo 无限剑这类「setHealth(0)+die() 绕过 damage()」的秒杀,
        //    ① 拦不住,必须在死亡回调里拦。返回 false 取消死亡并把血抬回(回调内必须让血 >0,套路照 EndDragonHandler 三命)。
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableForeignDamageFilter) return true;
            if (!(entity instanceof Monster)) return true;
            if (!isForeignToMonster(cfg, source)) return true;     // 原版/永夜致死正常演出

            // 复原血量:优先用同一 tick 的伤害快照(保留此前合法伤害),否则兜底满血。
            long now = entity.getWorld().getTime();
            HpSnapshot snap = PRE_KILL_HP.remove(entity.getUuid());
            float restore = (snap != null && now - snap.tick() <= 2 && snap.health() > 0f)
                    ? snap.health() : entity.getMaxHealth();
            entity.setHealth(Math.max(1.0f, restore));

            // 嘲讽照旧(冷却自动与 ① 去重,一刀只响一次);action bar 提示不重发,免同 tick 闪烁。
            if (cfg.foreignDamageTaunt && source.getAttacker() instanceof ServerPlayerEntity sp) {
                taunt(cfg, entity, sp);
            }
            return false;
        });

        Yongye.LOGGER.info("[永夜] 怪物伤害来源检测已挂载(伤害+死亡双拦,外模组伤害/秒杀均不作数)");
    }

    /**
     * 判定「这次对怪物的伤害 / 死亡是否来自外模组」。四路信号,任一判为外来即返回 true:
     * <ol>
     *   <li><b>伤害类型命名空间</b>(如 avaritia:infinity / tacz:bullet)——【待编译验证】getTypeRegistryEntry() 为 yarn 1.21.1 官方 mapping;</li>
     *   <li><b>直接来源实体</b>(枪械子弹 / 投射物本体,如 tacz 的子弹实体)命名空间——【待编译验证】getSource();</li>
     *   <li>玩家出手 → 造成伤害的<b>武器</b>(伤害源武器栈,拿不到兜底主手)的命名空间;</li>
     *   <li>非玩家实体出手 → <b>攻击者实体类型</b>命名空间(外模组召唤物 / 炮塔)。</li>
     * </ol>
     * 无攻击者且伤害类型为原版(摔落 / 岩浆 / 仙人掌等)→ 判为非外来,照常放行(不让怪物对环境免疫)。
     */
    private static boolean isForeignToMonster(YongyeConfig cfg, DamageSource source) {
        // (1) 伤害类型命名空间【待编译验证:getTypeRegistryEntry()】
        if (!isAllowedNamespace(cfg, damageTypeNamespace(source))) return true;

        // (2) 直接来源实体(m192):枪械子弹 / 外模组投射物本体——即便攻击者(射手)判不出也拦。
        //     【待编译验证】getSource() = yarn 官方 mapping(直接实体);玩家本体不在此判(走下面武器分支)。
        Entity direct = source.getSource();
        if (direct != null && !(direct instanceof PlayerEntity)
                && !isAllowedNamespace(cfg, Registries.ENTITY_TYPE.getId(direct.getType()).getNamespace())) {
            return true;
        }

        // (3) 攻击者:玩家看武器 / 非玩家看实体类型
        Entity attacker = source.getAttacker();
        if (attacker == null) return false;   // 无攻击者 + 原版伤害类型 = 环境伤害,不算外来

        if (attacker instanceof PlayerEntity player) {
            ItemStack weapon = source.getWeaponStack();   // 【待编译验证】method_60948;拿不到兜底主手
            if (weapon == null || weapon.isEmpty()) weapon = player.getMainHandStack();
            return !isAllowedNamespace(cfg, Registries.ITEM.getId(weapon.getItem()).getNamespace());
        }
        return !isAllowedNamespace(cfg, Registries.ENTITY_TYPE.getId(attacker.getType()).getNamespace());
    }

    /** 伤害类型的命名空间;取不到时按原版({@code minecraft})处理,避免误伤环境伤害。 */
    private static String damageTypeNamespace(DamageSource source) {
        return source.getTypeRegistryEntry().getKey()
                .map(k -> k.getValue().getNamespace())
                .orElse("minecraft");
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
     * 每玩家带冷却(foreignDamageTauntCooldownTicks,默 60t = 3 秒),防连点刷屏,同时天然去重 ①②两路的重复触发。
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
