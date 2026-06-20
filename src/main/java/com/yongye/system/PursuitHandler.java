package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.ArtifactType;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 追杀系统(文档第 8 章):锁定、挖墙、爬墙。全部由服务端 tick 驱动,公开 API 实现,无需 mixin。
 *  - 锁定:永夜激活,怪物在锁定半径内主动把玩家设为目标。
 *  - 挖墙:有目标且撞墙时,按档位(普通/精英/Boss)破坏前方一定硬度内的方块。
 *  - 爬墙:精英/Boss 或永夜 III+ 时,撞墙且目标在上方则向上加速。
 */
public final class PursuitHandler {
    private PursuitHandler() {}

    private static final Map<MobEntity, Integer> LAST_DIG_AGE = new WeakHashMap<>();
    private static int tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enablePursuit) return;
            if (++tickCounter < 4) return; // 每 4 tick 跑一次,降开销
            tickCounter = 0;

            int nf = NightfallManager.getLevel();
            double lockRadius = NightfallManager.getLockRadius();

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.interactionManager.getGameMode() == GameMode.CREATIVE
                        || player.interactionManager.getGameMode() == GameMode.SPECTATOR) continue;
                if (!(player.getWorld() instanceof ServerWorld world)) continue;

                double r = cfg.pursuitRadius;
                Box box = player.getBoundingBox().expand(r);
                List<MobEntity> mobs = world.getEntitiesByClass(MobEntity.class, box,
                        m -> m.isAlive() && m instanceof Monster);

                boolean anchor = ArtifactManager.getActiveLevel(player, ArtifactType.WORLD_ANCHOR) > 0;
                int eyeLevel = ArtifactManager.getActiveLevel(player, ArtifactType.NIGHTFALL_EYE);
                double effLock = lockRadius * (eyeLevel > 0 ? 0.6 : 1.0);

                for (MobEntity mob : mobs) {
                    boolean elite = mob.getAttachedOrElse(ModAttachments.IS_ELITE, false);
                    boolean boss = mob.getAttachedOrElse(ModAttachments.IS_BOSS, false);

                    // —— 锁定 ——
                    if (nf >= 1 && effLock > 0 && mob.getTarget() == null
                            && mob.squaredDistanceTo(player) <= effLock * effLock) {
                        mob.setTarget(player);
                    }

                    boolean chasingThisPlayer = mob.getTarget() == player;
                    if (!chasingThisPlayer) continue;

                    // —— 挖墙 ——
                    if (!anchor && mob.horizontalCollision) {
                        double maxHardness = boss ? cfg.digMaxHardnessBoss
                                : elite ? cfg.digMaxHardnessElite : cfg.digMaxHardnessNormal;
                        int last = LAST_DIG_AGE.getOrDefault(mob, -100000);
                        if (mob.age - last >= cfg.digCooldownTicks) {
                            if (tryDig(world, mob, player, maxHardness)) {
                                LAST_DIG_AGE.put(mob, mob.age);
                            }
                        }
                    }

                    // —— 爬墙 ——
                    if (!anchor && (elite || boss || nf >= 3) && mob.horizontalCollision
                            && player.getY() > mob.getY() + 0.6) {
                        Vec3d v = mob.getVelocity();
                        mob.setVelocity(v.x, cfg.climbSpeed, v.z);
                        mob.velocityModified = true;
                    }
                }
            }
        });

        Yongye.LOGGER.info("[亡途荒夜] 追杀系统已挂载");
    }

    private static boolean tryDig(ServerWorld world, MobEntity mob, ServerPlayerEntity player, double maxHardness) {
        double dx = player.getX() - mob.getX();
        double dz = player.getZ() - mob.getZ();
        Direction dir = Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);

        BlockPos base = mob.getBlockPos();
        BlockPos[] candidates = { base.offset(dir), base.up().offset(dir) };
        for (BlockPos pos : candidates) {
            var state = world.getBlockState(pos);
            if (state.isAir()) continue;
            float hardness = state.getHardness(world, pos);
            if (hardness < 0) continue;            // 不可破坏(如基岩)
            if (hardness > maxHardness) continue;  // 超过本档可破坏硬度
            world.breakBlock(pos, true, mob);
            return true;
        }
        return false;
    }
}
