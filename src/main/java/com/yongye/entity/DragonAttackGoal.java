package com.yongye.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.PathAwareEntity;

/**
 * 末影龙专用近战 goal:覆盖 MeleeAttackGoal 的「最大攻击判定距离」,让它能从更远处出手,
 * 不必贴到身上才打(原版 Vindicator/守卫者也是靠子类覆盖这个方法来改攻击距离的)。
 *
 * <p>reach 以「格」为单位;父类用平方距离比较,故返回 reach²(再加目标宽度让大目标也合理)。
 * tryAttack 直接结算伤害,不做距离二次判定,所以这里放大判定距离即等于「攻击距离变远」。
 */
public class DragonAttackGoal extends MeleeAttackGoal {

    private final double reach;

    public DragonAttackGoal(PathAwareEntity mob, double speed, boolean pauseWhenMobIdle, double reach) {
        super(mob, speed, pauseWhenMobIdle);
        this.reach = reach;
    }

    @Override
    protected double getSquaredMaxAttackDistance(LivingEntity entity) {
        return this.reach * this.reach + entity.getWidth();
    }
}
