package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.ArtifactType;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
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
    // 搭塔冷却:每只怪上次搭方块的 age
    private static final Map<MobEntity, Integer> LAST_PILLAR_AGE = new WeakHashMap<>();
    private static int tickCounter = 0;
    private static int teleportsThisTick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enablePursuit) return;
            if (++tickCounter < 2) return; // 每 2 tick 跑一次,兼顾响应与开销
            tickCounter = 0;
            teleportsThisTick = 0;

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

                    // —— 嵌墙兜底:怪整只卡在实心方块里 → 能在玩家身边找到落点就传送脱困;找不到则下方挖墙脱困 ——
                    if (!anchor && cfg.pursuitTeleportWallStuck && isStuckInWall(world, mob)) {
                        if (teleportsThisTick < cfg.pursuitMaxTeleportsPerTick
                                && teleportNear(world, mob, player, cfg)) {
                            teleportsThisTick++;
                            STUCK.remove(mob);
                            mob.setTarget(player);
                            continue;
                        }
                    }

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
                    // 前方是否有"墙":必须是真有碰撞箱的实心块才算。
                    // 旧实现只判 !isAir(),把草/花/雪层/麦子等无碰撞植被也当成墙,
                    // 导致怪走在草地上每 tick 都触发起跳翻越 → 原地一跳一跳(回归 bug)。
                    boolean wallAhead = hasCollision(world, base.offset(dir))
                            || hasCollision(world, base.up().offset(dir));

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
                                || (cfg.pursuitTeleportWallStuck && stuckLong && wallAhead))) {
                            if (teleportsThisTick < cfg.pursuitMaxTeleportsPerTick
                                    && teleportNear(world, mob, player, cfg)) {
                                teleportsThisTick++;
                                st[0] = mob.squaredDistanceTo(player);
                                st[1] = mob.age;
                                mob.setTarget(player);
                                continue; // 本 tick 不再挖/爬
                            }
                        }
                    }

                    // —— 高柱兜底(m151):玩家近乎正上方且高出阈值、怪长时间拉不近 → 直接传到玩家所在格 ——
                    // 专治玩家用 1×1 细柱躲正上方:此时玩家四周同高度全空气、底下无实心块,
                    // teleportNear 找不到落脚点而失败;这里不找地面、不依赖墙/解锁日,直接落到玩家脚下方块(柱顶)。
                    // 触发要求"持续无进展达 pursuitStuckTicks",先给搭塔/爬墙留出机会再兜底。
                    if (!anchor && cfg.pursuitTeleportPillarCheese) {
                        double horiz = Math.sqrt(dx * dx + dz * dz);
                        double dyUp = player.getY() - mob.getY();
                        double pcDistSq = mob.squaredDistanceTo(player);
                        double[] pcSt = STUCK.computeIfAbsent(mob, k -> new double[]{pcDistSq, mob.age});
                        if (pcDistSq < pcSt[0] - 0.5) { pcSt[0] = pcDistSq; pcSt[1] = mob.age; }
                        boolean pcStuckLong = mob.age - pcSt[1] > cfg.pursuitStuckTicks;
                        if (horiz <= cfg.pillarCheeseMaxHorizontal && dyUp >= cfg.pillarCheeseMinHeight && pcStuckLong) {
                            if (teleportsThisTick < cfg.pursuitMaxTeleportsPerTick
                                    && teleportOntoPlayer(world, mob, player)) {
                                teleportsThisTick++;
                                // 把玩家从细柱上撞下去:水平冲量(柱仅1格宽,中等力度即可推出边缘坠落)+ 轻微上抬脱离柱顶;
                                // 玩家移动是客户端权威,必须显式发 EntityVelocityUpdateS2CPacket 速度才真正生效。pillarCheeseKnockback=0 可关。
                                if (cfg.pillarCheeseKnockback > 0) {
                                    double ang = mob.getRandom().nextDouble() * Math.PI * 2;
                                    double kb = cfg.pillarCheeseKnockback;
                                    player.setVelocity(Math.cos(ang) * kb, 0.2, Math.sin(ang) * kb);
                                    player.velocityModified = true;
                                    player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
                                }
                                pcSt[0] = mob.squaredDistanceTo(player);
                                pcSt[1] = mob.age;
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
                            if (ProgressionManager.canMobsDig(world) && tryDig(world, mob, dir, maxHardness)) {
                                LAST_DIG_AGE.put(mob, mob.age);
                            }
                        }
                    }

                    // —— 起跳翻越 —— 撞 1~2 格低墙且在地面:给一次起跳冲量,帮怪翻过去(配合挖墙/搭塔,替代瞬移)
                    if (!anchor && cfg.pursuitJumpWalls && wallAhead && mob.isOnGround()
                            && world.getBlockState(base.up(2).offset(dir)).isAir()) {
                        Vec3d jv = mob.getVelocity();
                        mob.setVelocity(jv.x + dir.getOffsetX() * 0.1, 0.42, jv.z + dir.getOffsetZ() * 0.1);
                        mob.velocityModified = true;
                    }

                    // —— 搭方块爬塔 —— 反制玩家用单格高塔躲在怪够不着的正上方:
                    // 玩家近乎正上方且高出若干格时,怪原地搭方块柱子逐格上移(比纯爬墙更可靠)。
                    boolean pillared = false;
                    if (!anchor && cfg.mobPillarUp && ProgressionManager.canMobsDig(world)
                            && player.getY() - mob.getY() > cfg.pillarMinHeightDiff
                            && Math.sqrt(dx * dx + dz * dz) <= cfg.pillarMaxHorizontal
                            && mob.isOnGround()) {
                        int lastP = LAST_PILLAR_AGE.getOrDefault(mob, -100000);
                        if (mob.age - lastP >= cfg.pillarCooldownTicks && tryPillarUp(world, mob, cfg)) {
                            LAST_PILLAR_AGE.put(mob, mob.age);
                            pillared = true;
                        }
                    }

                    // —— 爬墙 —— 正前方有墙且玩家更高时,持续向上推(没搭方块时才走;蜘蛛之外的怪也能爬)
                    if (!pillared && !anchor && (elite || boss || nf >= 3) && wallAhead
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

        Yongye.LOGGER.info("[永夜] 追杀系统已挂载");
    }

    /**
     * 该位置是否有"真实碰撞箱"的方块(能挡住怪)。
     * 用碰撞箱(getCollisionShape 非空)而非 !isAir 判断:草/花/雪层/麦子等装饰植被碰撞箱为空,
     * 不会被误判成墙——这是修"怪在草地上一跳一跳"的关键。
     */
    private static boolean hasCollision(ServerWorld world, BlockPos pos) {
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
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

    /**
     * 高柱兜底专用传送:直接把怪传到玩家所在格(站到玩家脚下方块=柱顶上)。
     * 与 teleportNear 不同——不在玩家四周找可站立地面,因此玩家站 1×1 细柱顶(四周无实心块)时也能上去。
     */
    private static boolean teleportOntoPlayer(ServerWorld world, MobEntity mob, ServerPlayerEntity player) {
        if (mob.hasVehicle()) mob.stopRiding();
        world.spawnParticles(ParticleTypes.PORTAL, mob.getX(), mob.getBodyY(0.5), mob.getZ(),
                20, 0.3, 0.5, 0.3, 0.4);
        mob.getNavigation().stop();
        mob.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), mob.getYaw(), mob.getPitch());
        mob.setVelocity(Vec3d.ZERO);
        mob.velocityModified = true;
        mob.fallDistance = 0.0f;
        world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 0.5, player.getZ(),
                20, 0.3, 0.5, 0.3, 0.4);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.HOSTILE, 0.8f, 1.0f);
        return true;
    }

    /** 怪是否整只嵌在实心方块里(脚部或眼部所在方块会致憋闷)。 */
    private static boolean isStuckInWall(ServerWorld world, MobEntity mob) {
        BlockPos feet = mob.getBlockPos();
        BlockPos eye = BlockPos.ofFloored(mob.getX(), mob.getEyeY(), mob.getZ());
        return suffocates(world, feet) || suffocates(world, eye);
    }

    private static boolean suffocates(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).shouldSuffocate(world, pos);
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

    /** 怪原地搭一格方块往上爬(像玩家搭柱子):先上移 1 格,再在原脚位填方块。成功返回 true。 */
    private static boolean tryPillarUp(ServerWorld world, MobEntity mob, YongyeConfig cfg) {
        BlockPos feet = mob.getBlockPos();
        // 上移后怪会占据 feet.up(1)+feet.up(2),需 feet.up(2) 为空;脚位本应是空气(怪站在 feet.down 上)
        if (!world.getBlockState(feet.up(2)).isAir()) return false;
        if (!world.getBlockState(feet).isAir()) return false;
        BlockState block = pillarBlockState(cfg);
        mob.getNavigation().stop();
        mob.refreshPositionAndAngles(feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5, mob.getYaw(), mob.getPitch());
        world.setBlockState(feet, block);
        mob.setVelocity(Vec3d.ZERO);
        mob.velocityModified = true;
        mob.fallDistance = 0.0f;
        world.spawnParticles(ParticleTypes.CLOUD, feet.getX() + 0.5, feet.getY() + 0.5, feet.getZ() + 0.5,
                5, 0.2, 0.1, 0.2, 0.0);
        world.playSound(null, feet, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.HOSTILE, 0.6f, 1.0f);
        return true;
    }

    /** 解析配置里的搭塔方块 id(非法/缺失则回退圆石)。 */
    private static BlockState pillarBlockState(YongyeConfig cfg) {
        Identifier id = Identifier.tryParse(cfg.pillarBlock);
        if (id != null) {
            var b = Registries.BLOCK.get(id);
            if (b != Blocks.AIR) return b.getDefaultState();
        }
        return Blocks.COBBLESTONE.getDefaultState();
    }
}
