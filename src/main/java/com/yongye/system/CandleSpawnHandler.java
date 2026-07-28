package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Heightmap;

import java.util.List;

/**
 * m305 烛之维度刷怪(作者:「刷怪速度是外面的一百倍,但是要做实体优化」):
 * 原版地表刷怪≈每玩家每 400t 一波,这里=每玩家每 4t 一波(candleDimSpawnIntervalTicks,快一百倍),
 * 每波 1~3 只,落点玩家四周 12~candleDimSpawnRadius 环带地表,无视亮度(固定正午照刷)。
 * <b>实体优化三闸</b>(NightfallHordeHandler m? 无全局闸拖崩 TPS 的教训,照 HardcoreSurvival 口径):
 *  ① 每玩家 48 格内敌对 ≥ candleDimMaxNearbyHostiles 停刷;
 *  ② 全维度敌对 ≥ candleDimGlobalMaxHostiles 停刷(硬预算);
 *  ③ 每 100t 清一次离所有玩家 > candleDimCleanupDistance 的敌对怪(直接 discard,不掉落)。
 * 亡灵出生戴皮革帽(掉率 0):固定正午会点燃亡灵,帽子扛晒(原版 husk 口径,帽子耐久替它烧)。
 */
public final class CandleSpawnHandler {
    private CandleSpawnHandler() {}

    private static final EntityType<?>[] POOL = {
            EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE,
            EntityType.SKELETON, EntityType.SKELETON, EntityType.SKELETON,
            EntityType.SPIDER, EntityType.SPIDER,
            EntityType.CREEPER, EntityType.CREEPER,
            EntityType.ENDERMAN
    };

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableCandleDimension) return;
            ServerWorld world = server.getWorld(CandleDimension.WORLD_KEY);
            if (world == null || world.getPlayers().isEmpty()) return;

            // ③ 距离清理(实体优化):每 100t 扫一次
            if (server.getTicks() % 100 == 0) {
                double cd = Math.max(48, cfg.candleDimCleanupDistance);
                double cd2 = cd * cd;
                for (HostileEntity h : world.getEntitiesByClass(HostileEntity.class,
                        fullBounds(world), e -> e.isAlive())) {
                    boolean nearAnyone = false;
                    for (ServerPlayerEntity p : world.getPlayers()) {
                        if (p.squaredDistanceTo(h) <= cd2) { nearAnyone = true; break; }
                    }
                    if (!nearAnyone) h.discard();
                }
            }

            int interval = Math.max(1, cfg.candleDimSpawnIntervalTicks);
            if (server.getTicks() % interval != 0) return;

            // ② 全维度硬预算
            long global = world.getEntitiesByClass(HostileEntity.class, fullBounds(world), e -> e.isAlive()).size();
            if (global >= cfg.candleDimGlobalMaxHostiles) return;

            for (ServerPlayerEntity p : List.copyOf(world.getPlayers())) {
                if (p.isSpectator()) continue;
                // ① 每玩家局部闸
                long nearby = world.getEntitiesByClass(HostileEntity.class,
                        p.getBoundingBox().expand(48), e -> e.isAlive()).size();
                if (nearby >= cfg.candleDimMaxNearbyHostiles) continue;

                var r = world.getRandom();
                int burst = LagGuard.scale(world.getServer(), 1 + r.nextInt(3));  // m335 卡顿护栏
                if (burst <= 0) continue;
                for (int i = 0; i < burst; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double dist = 12 + r.nextDouble() * Math.max(1, cfg.candleDimSpawnRadius - 12);
                    int sx = p.getBlockX() + (int) (Math.cos(ang) * dist);
                    int sz = p.getBlockZ() + (int) (Math.sin(ang) * dist);
                    int sy = world.getTopY(Heightmap.Type.MOTION_BLOCKING, sx, sz);
                    if (sy <= world.getBottomY()) continue;
                    EntityType<?> type = POOL[r.nextInt(POOL.length)];
                    if (!(type.create(world) instanceof MobEntity mob)) continue;
                    mob.refreshPositionAndAngles(sx + 0.5, sy, sz + 0.5, r.nextFloat() * 360f, 0f);
                    // 亡灵扛晒帽(固定正午):皮革帽,掉率 0(照 EliteHandler equipStack 口径)
                    if (type == EntityType.ZOMBIE || type == EntityType.SKELETON) {
                        mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                        mob.setEquipmentDropChance(EquipmentSlot.HEAD, 0.0f);
                    }
                    world.spawnEntity(mob);
                    mob.setTarget(p);
                }
            }
        });
        Yongye.LOGGER.info("[夜蚀] 烛之维度刷怪已挂载(百倍节奏+三重实体闸)");
    }

    private static net.minecraft.util.math.Box fullBounds(ServerWorld world) {
        // 以玩家群为中心的大包围盒(维度级统计;烛维度里玩家不会散太远,足够代表全维度)
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (ServerPlayerEntity p : world.getPlayers()) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new net.minecraft.util.math.Box(minX - 160, world.getBottomY(), minZ - 160,
                maxX + 160, world.getTopY(), maxZ + 160);
    }
}
