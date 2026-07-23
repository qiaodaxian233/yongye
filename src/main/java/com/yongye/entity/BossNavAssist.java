package com.yongye.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;

/**
 * 大体型 BOSS 防转圈助手(m267)。
 *
 * <p><b>转圈根因:</b>原版寻路(LandPathNodeMaker)按实体宽度取整算通道——
 * 阿努比斯宽 2.5、红蜘蛛/巨蟹宽 3.0,需要 3 格宽的无障碍走廊才算"有路",
 * 野外地形几乎处处寻路失败;MeleeAttackGoal 每秒重找路 → MoveControl 不停朝
 * 失败节点拧身子 → 表现为原地转圈。贴脸时另一个来源:目标坐标在自己巨大
 * hitbox 边上,路径瞬间完成又瞬间重找,同样拧圈。
 *
 * <p><b>两板斧(每 tick 调用,零新实体/零 mixin):</b><br>
 * ① 近身(≤体宽×2+1 格):停掉寻路,锁头+锁身面向目标——贴脸不再拧;<br>
 * ② 寻路失败(navigation.isIdle 且目标在追击距离内):绕过寻路,直接
 * MoveControl 直线压上——大体型不再依赖"3 格宽走廊",配合 m267 新加的
 * 跨步高度属性(GENERIC_STEP_HEIGHT)可以直接踏上 1 格高低差,穷追不舍。
 */
public final class BossNavAssist {
    private BossNavAssist() {}

    public static void tick(MobEntity mob) {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;

        double reach = mob.getWidth() * 2.0 + 1.0;
        double d = mob.distanceTo(target);

        if (d <= reach) {
            // ① 贴脸:别再找路,站定面向目标(出手判定走 Goal.canAttack,与寻路无关)
            mob.getNavigation().stop();
            mob.getLookControl().lookAt(target, 30.0f, 30.0f);
            mob.setBodyYaw(mob.getHeadYaw());
        } else if (mob.getNavigation().isIdle()) {
            // ② 找不到路:直线压上(MoveControl 不走路径图,飞行怪同样适用)
            mob.getMoveControl().moveTo(target.getX(), target.getY(), target.getZ(), 1.2);
            mob.getLookControl().lookAt(target, 30.0f, 30.0f);
        }
    }
}
