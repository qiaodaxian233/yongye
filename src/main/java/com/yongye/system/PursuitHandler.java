package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.ArtifactType;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
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
    // 卡住跟踪:{最近距玩家最小平方距离, 取得该最小值时的 age}
    private static final Map<MobEntity, double[]> STUCK = new WeakHashMap<>();
    private static int tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enablePursuit) return;
            if (++tickCounter < 2) return; // 每 2 tick 跑一次,兼顾响应与开销
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
                        m -> m.isAlive() && m instanceof Monster
                                && !m.getAttachedOrElse(ModAttachments.IS_HIM, false));

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

                    // 朝玩家的水平主方向
                    double dx = player.getX() - mob.getX();
                    double dz = player.getZ() - mob.getZ();
                    Direction dir = Math.abs(dx) >= Math.abs(dz)
                            ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                            : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
                    BlockPos base = mob.getBlockPos();
                    boolean wallAhead = !world.getBlockState(base.offset(dir)).isAir()
                            || !world.getBlockState(base.up().offset(dir)).isAir();

                    // —— 卡住兜底:船卡/水/岩浆/挖不动的墙后,传送到玩家身边 ——
                    if (!anchor && cfg.pursuitTeleportStuck) {
                        double distSq = mob.squaredDistanceTo(player);
                        double[] st = STUCK.computeIfAbsent(mob, k -> new double[]{distSq, mob.age});
                        if (distSq < st[0] - 0.5) { st[0] = distSq; st[1] = mob.age; } // 有进展则刷新
                        boolean stuckLong = mob.age - st[1] > cfg.pursuitStuckTicks;
                        boolean inFluid = mob.isInLava() || mob.isSubmergedInWater();
                        boolean riding = mob.hasVehicle();
                        double minSq = cfg.pursuitTeleportMinDist * cfg.pursuitTeleportMinDist;

                        if (distSq > minSq && (riding || (inFluid && distSq > 16)
                                || (stuckLong && wallAhead))) {
                            if (teleportNear(world, mob, player, cfg)) {
                                st[0] = mob.squaredDistanceTo(player);
                                st[1] = mob.age;
                                mob.setTarget(player);
                                continue; // 本 tick 不再挖/爬
                            }
                        }
                    }

                    // —— 挖墙 ——
                    if (!anchor && wallAhead) {
                        double maxHardness = boss ? cfg.digMaxHardnessBoss
                                : elite ? cfg.digMaxHardnessElite : cfg.digMaxHardnessNormal;
                        int last = LAST_DIG_AGE.getOrDefault(mob, -100000);
                        if (mob.age - last >= cfg.digCooldownTicks) {
                            if (tryDig(world, mob, dir, maxHardness)) {
                                LAST_DIG_AGE.put(mob, mob.age);
                            }
                        }
                    }

                    // —— 爬墙 —— 正前方有墙且玩家更高时,持续向上推(蜘蛛之外的怪也能爬)
                    if (!anchor && (elite || boss || nf >= 3) && wallAhead
                            && player.getY() > mob.getY() + 0.6) {
                        Vec3d v = mob.getVelocity();
                        double pushX = dir.getOffsetX() * 0.12;
                        double pushZ = dir.getOffsetZ() * 0.12;
                        mob.setVelocity(v.x * 0.5 + pushX, cfg.climbSpeed, v.z * 0.5 + pushZ);
                        mob.velocityModified = true;
                        mob.fallDistance = 0.0f;
                    }
                }
            }
        });

        Yongye.LOGGER.info("[亡途荒夜] 追杀系统已挂载");
    }

    /** 在玩家附近(相近高度)找一个可站立的安全点把怪传过去。成功返回 true。 */
    private static boolean teleportNear(ServerWorld world, MobEntity mob, ServerPlayerEntity player, YongyeConfig cfg) {
        if (mob.hasVehicle()) mob.stopRiding();
        var rnd = mob.getRandom();
        int py = MathHelper.floor(player.getY());
        int[] dyTry = {0, -1, 1, -2, 2};
        for (int i = 0; i < 14; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double d = cfg.pursuitTeleportMinDist + rnd.nextDouble() * cfg.pursuitTeleportRadius;
            int x = MathHelper.floor(player.getX() + Math.cos(ang) * d);
            int z = MathHelper.floor(player.getZ() + Math.sin(ang) * d);
            for (int dy : dyTry) {
                BlockPos feet = new BlockPos(x, py + dy, z);
                BlockPos below = feet.down();
                if (!world.getBlockState(below).isSolidBlock(world, below)) continue;
                if (!world.getBlockState(feet).isAir() || !world.getBlockState(feet.up()).isAir()) continue;
                if (!world.getFluidState(feet).isEmpty() || !world.getFluidState(feet.up()).isEmpty()) continue;

                world.spawnParticles(ParticleTypes.PORTAL, mob.getX(), mob.getBodyY(0.5), mob.getZ(),
                        20, 0.3, 0.5, 0.3, 0.4);
                mob.getNavigation().stop();
                mob.refreshPositionAndAngles(x + 0.5, py + dy, z + 0.5, mob.getYaw(), mob.getPitch());
                mob.setVelocity(Vec3d.ZERO);
                mob.velocityModified = true;
                mob.fallDistance = 0.0f;
                world.spawnParticles(ParticleTypes.PORTAL, x + 0.5, py + dy + 0.5, z + 0.5,
                        20, 0.3, 0.5, 0.3, 0.4);
                world.playSound(null, feet, SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                        SoundCategory.HOSTILE, 0.8f, 1.0f);
                return true;
            }
        }
        return false;
    }

    private static boolean tryDig(ServerWorld world, MobEntity mob, Direction dir, double maxHardness) {
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
