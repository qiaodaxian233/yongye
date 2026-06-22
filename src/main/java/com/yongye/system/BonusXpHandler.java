package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * 精英及以上的怪死亡时额外掉经验,加快升级(应需求加)。
 * 分档:长门 > 怪物BOSS版 > 原版Boss > 精英;取最高适用档。
 * 纯 AFTER_DEATH 事件,经验用原版 ExperienceOrbEntity.spawn(自动拆成若干小球),无 mixin、无新依赖。
 */
public final class BonusXpHandler {
    private BonusXpHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableBonusXp) return;
            if (!(entity instanceof MobEntity mob)) return;
            if (!(mob.getWorld() instanceof ServerWorld world)) return;

            int bonus = 0;
            if (mob.getAttachedOrElse(ModAttachments.IS_PAIN, false)) {
                bonus = cfg.xpBonusPain;
            } else if (mob.getAttachedOrElse(ModAttachments.IS_MOB_BOSS, false)) {
                bonus = cfg.xpBonusMobBoss;                       // 怪物BOSS版(也带 IS_BOSS,故先判它)
            } else if (mob.getAttachedOrElse(ModAttachments.IS_BOSS, false) || BossHandler.isBoss(mob)) {
                bonus = cfg.xpBonusVanillaBoss;                   // 原版 Boss(在原版自带经验之上再加)
            } else if (mob.getAttachedOrElse(ModAttachments.IS_ELITE, false)) {
                bonus = cfg.xpBonusElite;
            }

            if (bonus > 0) {
                ExperienceOrbEntity.spawn(world, mob.getPos(), bonus);
            }
        });
        Yongye.LOGGER.info("[亡途荒夜] 精英+ 额外经验系统已挂载");
    }
}
