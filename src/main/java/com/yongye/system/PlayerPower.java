package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.item.SkillType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 玩家强度评估 + 动态爆率倍率。
 * 强度 = 所有技能书已学等级之和 + 当前装备(主手/副手/四甲)强化等级之和 × 权重。
 * 倍率随强度反比衰减:玩家越强,掉率越低,减缓滚雪球,让怪物成长追得上,保持「有来有回」。
 */
public final class PlayerPower {
    private PlayerPower() {}

    private static final EquipmentSlot[] GEAR = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** 玩家综合强度分(技能书等级总和 + 装备强化等级 × 权重)。 */
    public static double score(ServerPlayerEntity p) {
        double skill = PlayerSkillManager.getLearnedHealth(p);
        for (SkillType t : SkillType.values()) skill += SkillEffectManager.getLearnedLevel(p, t);
        double enhance = 0;
        for (EquipmentSlot slot : GEAR) enhance += EquipmentEnhancer.getLevel(p.getEquippedStack(slot));
        return skill + enhance * YongyeConfig.get().dynamicLootEnhanceWeight;
    }

    /** 动态掉率倍率(0~1):m = max(下限, 1 / (1 + 强度/K))。关闭时恒为 1。 */
    public static double lootMultiplier(ServerPlayerEntity p) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableDynamicLoot) return 1.0;
        double m = 1.0 / (1.0 + score(p) / Math.max(1.0, cfg.dynamicLootK));
        return Math.max(cfg.dynamicLootFloor, m);
    }
}
