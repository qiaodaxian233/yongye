package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;

/**
 * 永夜尸潮:永夜 ≥1 时,在每个玩家周围持续补刷敌对怪,维持高密度蜂拥。
 * 目标怪量 = min(base × 永夜等级, max)——永夜 I=base(默认100),II=翻倍(200),封顶 max 护住 TPS。
 * 刷出的怪走正常增强(ENTITY_LOAD)+ 追杀锁定(永夜≥1),自然蜂拥追杀。世界锚石范围内不刷(据点庇护)。
 */
public final class NightfallHordeHandler {
    private NightfallHordeHandler() {}

    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableNightfallHorde) return;
            int nf = NightfallManager.getLevel();
            if (nf < 1) return;
            if (++tick < Math.max(1, cfg.nightfallHordeIntervalTicks)) return;
            tick = 0;

            int target = Math.min(cfg.nightfallHordeBase * nf, cfg.nightfallHordeMax);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                GameMode gm = player.interactionManager.getGameMode();
                if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) continue;
                if (!(player.getWorld() instanceof ServerWorld world)) continue;
                if (ArtifactManager.getActiveLevel(player, com.yongye.item.ArtifactType.WORLD_ANCHOR) > 0) continue;

                Box box = player.getBoundingBox().expand(cfg.nightfallHordeRadius);
                int existing = world.getEntitiesByClass(MobEntity.class, box,
                        m -> m.isAlive() && m instanceof Monster).size();
                int want = Math.min(cfg.nightfallHordeBatch, target - existing);
                if (want <= 0) continue;

                int spawned = 0;
                // 多试几次找合法落点;最多 want×3 次,避免在不可刷处空转
                for (int i = 0; i < want * 3 && spawned < want; i++) {
                    if (trySpawnOne(world, player, cfg)) spawned++;
                }
            }
        });
        Yongye.LOGGER.info("[永夜] 永夜尸潮系统已挂载");
    }

    private static boolean trySpawnOne(ServerWorld world, ServerPlayerEntity player, YongyeConfig cfg) {
        var r = world.getRandom();
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = cfg.nightfallHordeMinDistance
                + r.nextDouble() * Math.max(1.0, cfg.nightfallHordeRadius - cfg.nightfallHordeMinDistance);
        int x = (int) Math.floor(player.getX() + Math.cos(ang) * dist);
        int z = (int) Math.floor(player.getZ() + Math.sin(ang) * dist);
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        // 落点需:脚下实心、身位两格空气
        if (world.getBlockState(pos.down()).isAir()) return false;
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) return false;

        MobEntity mob = pickMob(world);
        if (mob == null) return false;
        mob.refreshPositionAndAngles(x + 0.5, y, z + 0.5, r.nextFloat() * 360, 0);
        world.spawnEntity(mob);
        mob.setTarget(player); // 出生即锁定该玩家,直接蜂拥追杀
        return true;
    }

    /** 尸潮成分:僵尸 / 尸壳(不怕日晒)/ 蜘蛛,纯近战蜂拥。用显式构造(与 PainBoss 同款,最稳)。 */
    private static MobEntity pickMob(ServerWorld world) {
        int roll = world.getRandom().nextInt(100);
        if (roll < 60) return new ZombieEntity(EntityType.ZOMBIE, world);
        if (roll < 90) return new HuskEntity(EntityType.HUSK, world);   // 尸壳不怕日晒
        return new SpiderEntity(EntityType.SPIDER, world);
    }
}
