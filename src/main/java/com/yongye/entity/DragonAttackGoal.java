package com.yongye.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.PathAwareEntity;

/**
 * 末影龙专用近战 goal:覆盖 MeleeAttackGoal 的「canAttack」判定,让它能从更远处出手。
 *
 * <p>Minecraft 1.21 重构了近战 AI:getSquaredMaxAttackDistance 已被移除,
 * 取而代之的是 canAttack(LivingEntity target) → boolean。
 * reach 以「格」为单位,判定距离 = reach² + target.getWidth() 以兼容宽体目标。
 */
public class DragonAttackGoal extends MeleeAttackGoal {

    private final double reach;

    public DragonAttackGoal(PathAwareEntity mob, double speed, boolean pauseWhenMobIdle, double reach) {
        super(mob, speed, pauseWhenMobIdle);
        this.reach = reach;
    }

    @Override
    protected boolean canAttack(LivingEntity target) {
        double squaredDist = this.mob.squaredDistanceTo(target);
        double maxSqDist = this.reach * this.reach + target.getWidth();
        return squaredDist <= maxSqDist;
    }
}
