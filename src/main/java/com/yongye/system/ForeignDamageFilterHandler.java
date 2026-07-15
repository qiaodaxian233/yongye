package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                if (cfg.foreignDamageFilterHint && player instanceof ServerPlayerEntity sp) {
                    sp.sendMessage(Text.literal("外来模组武器对怪物无效(只认原版 / 永夜武器)")
                            .formatted(Formatting.GRAY), true);
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
}
