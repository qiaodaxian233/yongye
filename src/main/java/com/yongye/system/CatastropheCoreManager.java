package com.yongye.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.SkillBookItem;
import com.yongye.item.SkillType;
import com.yongye.registry.ModBlocks;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 灾厄核心管理器(文档 9.3)。
 *  - 永夜达到一定等级时,在玩家附近自然生成核心方块。
 *  - 玩家靠近时,核心持续在周围刷敌对怪(精英化交给 EliteHandler)。
 *  - 核心被摧毁(方块消失)→ 掉落裂界残片等奖励,并通知任务系统。
 *  - 仅在主世界运作;位置持久化到存档。
 */
public final class CatastropheCoreManager {
    private CatastropheCoreManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path savePath;
    private static final Set<Long> cores = new HashSet<>();
    private static int tick = 0;

    private static final EntityType<?>[] SPAWN_POOL = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER
    };

    private static class State { long[] cores; }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            savePath = server.getSavePath(WorldSavePath.ROOT).resolve("yongye_cores.json");
            load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!YongyeConfig.get().enableCatastropheCore) return;
            if (++tick < 40) return; // 每 2 秒一次
            tick = 0;
            ServerWorld world = server.getOverworld();
            if (world == null) return;
            tickCores(world);
            maybeNaturalSpawn(server, world);
        });
        Yongye.LOGGER.info("[永夜] 灾厄核心系统已挂载");
    }

    private static void tickCores(ServerWorld world) {
        YongyeConfig cfg = YongyeConfig.get();
        List<Long> snapshot = new ArrayList<>(cores);
        for (long key : snapshot) {
            BlockPos pos = BlockPos.fromLong(key);
            // 只处理玩家附近(已加载)的核心,避免强制加载区块
            PlayerEntity near = world.getClosestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 128, false);
            if (near == null) continue;

            if (!world.getBlockState(pos).isOf(ModBlocks.CATASTROPHE_CORE)) {
                // 被摧毁
                cores.remove(key);
                onDestroyed(world, pos, near instanceof ServerPlayerEntity sp ? sp : null);
                continue;
            }

            // 粒子提示
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    6, 0.3, 0.3, 0.3, 0.01);

            // 玩家足够近 → 刷怪
            if (near.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    <= (double) cfg.coreMobSpawnRadius * cfg.coreMobSpawnRadius) {
                int nearby = world.getEntitiesByClass(HostileEntity.class,
                        new Box(pos).expand(cfg.coreMobSpawnRadius), e -> true).size();
                long global = world.getEntitiesByClass(HostileEntity.class,
                        new Box(pos).expand(cfg.globalHostileRadius), e -> e.isAlive()).size();
                if (nearby < cfg.coreMobMaxNearby && global < cfg.globalMaxHostilesNearby
                        && world.getRandom().nextDouble() < cfg.coreMobSpawnChance) {
                    spawnMob(world, pos);
                }
            }
        }
    }

    private static void spawnMob(ServerWorld world, BlockPos core) {
        var r = world.getRandom();
        int dx = r.nextInt(7) - 3;
        int dz = r.nextInt(7) - 3;
        int x = core.getX() + dx, z = core.getZ() + dz;
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        EntityType<?> type = SPAWN_POOL[r.nextInt(SPAWN_POOL.length)];
        type.spawn(world, new BlockPos(x, y, z), SpawnReason.SPAWNER);
    }

    private static void maybeNaturalSpawn(MinecraftServer server, ServerWorld world) {
        YongyeConfig cfg = YongyeConfig.get();
        if (cores.size() >= cfg.coreMaxActive) return;
        if (NightfallManager.getLevel() < cfg.coreMinNightfall) return;
        if (world.getRandom().nextDouble() >= cfg.coreNaturalSpawnChance) return;
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;
        ServerPlayerEntity p = players.get(world.getRandom().nextInt(players.size()));
        BlockPos pos = pickSpawnPos(world, p.getBlockPos());
        if (pos != null) spawnCore(world, pos, true);
    }

    private static BlockPos pickSpawnPos(ServerWorld world, BlockPos around) {
        YongyeConfig cfg = YongyeConfig.get();
        var r = world.getRandom();
        double ang = r.nextDouble() * Math.PI * 2;
        int dist = cfg.coreSpawnDistanceMin + r.nextInt(Math.max(1, cfg.coreSpawnDistanceMax - cfg.coreSpawnDistanceMin + 1));
        int x = around.getX() + (int) (Math.cos(ang) * dist);
        int z = around.getZ() + (int) (Math.sin(ang) * dist);
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        return new BlockPos(x, y, z);
    }

    /** 放置一个核心并登记。 */
    public static void spawnCore(ServerWorld world, BlockPos pos, boolean announce) {
        world.setBlockState(pos, ModBlocks.CATASTROPHE_CORE.getDefaultState());
        cores.add(pos.asLong());
        if (announce && world.getServer() != null) {
            world.getServer().getPlayerManager().broadcast(
                    Text.literal("【灾厄核心】黑暗在 " + pos.getX() + ", " + pos.getZ() + " 凝聚……前去摧毁它")
                            .formatted(Formatting.DARK_RED), false);
        }
    }

    /** 在玩家附近生成一个核心,返回其位置(供任务/指令使用)。 */
    public static BlockPos spawnCoreNear(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return null;
        BlockPos pos = pickSpawnPos(world, player.getBlockPos());
        spawnCore(world, pos, true);
        return pos;
    }

    private static void onDestroyed(ServerWorld world, BlockPos pos, ServerPlayerEntity by) {
        var r = world.getRandom();
        drop(world, pos, new ItemStack(ModItems.RIFT_FRAGMENT, 1 + r.nextInt(2)));
        drop(world, pos, new ItemStack(ModItems.LIFE_CRYSTAL, 1 + r.nextInt(2)));
        SkillType st = SkillType.values()[r.nextInt(SkillType.values().length)];
        drop(world, pos, SkillBookItem.create(st, 1 + r.nextInt(3)));
        if (r.nextDouble() < 0.3) drop(world, pos, new ItemStack(ModItems.LIFE_CORE));

        if (world.getServer() != null) {
            world.getServer().getPlayerManager().broadcast(
                    Text.literal("【灾厄核心】一处核心被摧毁,黑暗稍退").formatted(Formatting.AQUA), false);
            if (YongyeConfig.get().coreDestroyRedeems) {
                NightfallManager.redeem(world.getServer());
            }
        }
    }

    /** 给定位置最近的核心(用于掘墓罗盘)。 */
    public static BlockPos getNearest(BlockPos from) {
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (long key : cores) {
            BlockPos p = BlockPos.fromLong(key);
            double d = p.getSquaredDistance(from);
            if (d < bestSq) { bestSq = d; best = p; }
        }
        return best;
    }

    private static void drop(ServerWorld world, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity ie = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
        ie.setToDefaultPickupDelay();
        world.spawnEntity(ie);
    }

    private static void load() {
        try {
            cores.clear();
            if (savePath != null && Files.exists(savePath)) {
                State s = GSON.fromJson(Files.readString(savePath), State.class);
                if (s != null && s.cores != null) {
                    for (long c : s.cores) cores.add(c);
                }
            }
        } catch (Exception e) {
            Yongye.LOGGER.error("[永夜] 读取灾厄核心存档失败", e);
        }
    }

    private static void save() {
        try {
            if (savePath == null) return;
            State s = new State();
            s.cores = cores.stream().mapToLong(Long::longValue).toArray();
            Files.writeString(savePath, GSON.toJson(s));
        } catch (Exception e) {
            Yongye.LOGGER.error("[永夜] 保存灾厄核心存档失败", e);
        }
    }
}
