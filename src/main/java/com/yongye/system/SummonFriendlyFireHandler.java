package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.player.PlayerEntity;

/**
 * m299 召唤物免友伤(作者:「召唤师的攻击会对召唤物造成伤害,这个得改。然后再看下分身术」)。
 * m453 召唤师彻底移除后,在册召唤物只剩术士·暗影分身 WarlockCloneEntity,本钩子照旧护它。
 *
 * 病根:范围技(回旋斩/蓄力重斩)目标过滤本就只认 Monster/精英,不误伤;漏的是**原版路径**——
 * 横扫之刃对弧内一切 LivingEntity 结算、近战误点、弹射物,这些都直接走 damage()。
 * 修法:挂 ALLOW_DAMAGE,凡受击者是己方召唤物、且伤害可归因到玩家(直接近战/弹射物 getAttacker=射手)
 * 一律取消。不做「只豁免主人」:召唤物只打怪从不打玩家,任何玩家对它们的伤害都只可能是误伤
 * (联机队友的横扫同理)——按全体玩家免最稳。
 * 环境伤害(岩浆/摔落/无主爆炸)与怪物攻击照常生效,寿命到点自散不受影响。开关 summonFriendlyFireImmune。
 */
public final class SummonFriendlyFireHandler {
    private SummonFriendlyFireHandler() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!YongyeConfig.get().summonFriendlyFireImmune) return true;
            boolean ours = entity instanceof com.yongye.entity.WarlockCloneEntity;
            if (!ours) return true;
            return !(source.getAttacker() instanceof PlayerEntity); // 玩家致伤 = 误伤,取消;其余照常
        });
        Yongye.LOGGER.info("[夜蚀] 召唤物免友伤已挂载(暗影分身)");
    }
}
