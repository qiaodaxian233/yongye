package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 动态对位缩放:按「附近最强玩家」的攻击/最大生命,把怪物的血量与伤害等比拔高(只增不减)。
 * 目的:后期玩家攻击/血量飙升后,新生成的怪物也随之水涨船高,保证「打得有来有回」,
 * 而不是被一刀秒、或怪物挠痒痒。这是玩家成长 → 怪物成长这条对位线的核心实现。
 *
 * 设计要点:
 *   - 血量目标 = 玩家每击基础攻击 × targetHits(期望击杀次数)。怪血不足目标就乘上去;已超过则不动。
 *   - 伤害目标 = 玩家最大生命 ÷ surviveHits(期望被打多少下才致命)。怪伤不足目标就乘上去;已超过则不动。
 *   - 用 ADD_MULTIPLIED_TOTAL 叠在「基础 × 其它倍率」之上,保证在精英/永夜深渊等倍率之后仍能补到对位线。
 *   - 只在怪物生成时(ENTITY_LOAD / BOSS 化)结算一次,取生成那一刻最近玩家的强度。
 */
public final class DynamicScaling {
    private DynamicScaling() {}

    private static final Identifier ID_DYN_HP  = Identifier.of(Yongye.MOD_ID, "dyn_hp");
    private static final Identifier ID_DYN_ATK = Identifier.of(Yongye.MOD_ID, "dyn_atk");

    /**
     * 按最近玩家强度对位缩放该怪物。
     * @param targetHits   期望玩家砍多少下才杀死(越大怪越肉)。≤0 跳过血量对位。
     * @param surviveHits  期望玩家被怪打多少下才致命(越大怪越软)。≤0 跳过伤害对位。
     * @param scanRadius   搜索最近玩家的半径。
     */
    public static void scaleToNearestPlayer(MobEntity mob, double targetHits, double surviveHits, double scanRadius) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableDynamicMobScaling) return;
        PlayerEntity p = mob.getWorld().getClosestPlayer(mob, scanRadius);
        if (p == null) return;

        double pAtk = p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        double pHp  = p.getMaxHealth();
        // 难度倍率:整局统一的世界难度(由房主/OP 设定),放大「对位目标」(只增不减,故低难度≈接近原版)
        double diffMult = com.yongye.system.DifficultyManager.mobMult();

        // —— 血量对位:目标 = 玩家每击攻击 × 期望击杀次数 × 难度,只增不减 ——
        // m148:血量对位改为只在「永夜 V·灭世」(永夜等级 ≥ 5)才开。此前(m147)挂在世界难度「困难+」上仍太难;
        //       改挂到「永夜等级」这条会随游戏推进/任务失败往上爬的线——前中期(永夜<5)怪不按攻击拔血,
        //       只有世界沉入永夜 V 之后才开始堆血(永夜≥5 含其后「深渊 N 层」)。伤害对位仍未受此门约束(玩家未点名改它)。
        boolean hpScalingOn = com.yongye.system.NightfallManager.getLevel() >= 5;  // 5 = 永夜 V·灭世
        EntityAttributeInstance hpInst = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hpScalingOn && hpInst != null && targetHits > 0 && pAtk > 0) {
            double curHp = hpInst.getValue();
            double targetHp = pAtk * targetHits * diffMult;
            if (curHp > 0 && targetHp > curHp) {
                hpInst.removeModifier(ID_DYN_HP);
                hpInst.addPersistentModifier(new EntityAttributeModifier(
                        ID_DYN_HP, targetHp / curHp - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }

        // —— 伤害对位:目标 = 玩家最大生命 ÷ 期望承受次数 × 难度,只增不减 ——
        EntityAttributeInstance atkInst = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (atkInst != null && surviveHits > 0) {
            double curDmg = atkInst.getValue();
            double targetDmg = (pHp / surviveHits) * diffMult;
            if (curDmg > 0 && targetDmg > curDmg) {
                atkInst.removeModifier(ID_DYN_ATK);
                atkInst.addPersistentModifier(new EntityAttributeModifier(
                        ID_DYN_ATK, targetDmg / curDmg - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }

        // 缩放后补满血量(避免显示成残血)
        mob.setHealth(mob.getMaxHealth());
    }
}
