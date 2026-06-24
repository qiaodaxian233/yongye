package com.yongye.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.SkillBookItem;
import com.yongye.item.SkillType;
import com.yongye.registry.ModBlocks;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
        // 玩家死亡 → 若世界存在祭坛,激发最近的祭坛"凝聚完毕":消耗它 + 永夜提升一层
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity dead)) return;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableCatastropheCore || !cfg.coreDeathRaisesNightfall) return;
            if (cores.isEmpty()) return;
            if (!(dead.getWorld() instanceof ServerWorld world)) return;
            completeAltarOnDeath(world, dead.getBlockPos());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!YongyeConfig.get().enableCatastropheCore) return;
            if (++tick < 40) return; // 每 2 秒一次
            tick = 0;
            ServerWorld world = server.getOverworld();
            if (world == null) return;
            tickCores(world);
            maybeNaturalSpawn(server, world);
            sendLocators(server, world);
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

    /** 放置一个核心(带祭坛结构)并登记。pos 为地面基准点;核心实际落在基座顶部。 */
    public static void spawnCore(ServerWorld world, BlockPos pos, boolean announce) {
        BlockPos corePos = buildAltar(world, pos);
        cores.add(corePos.asLong());
        if (announce && world.getServer() != null) {
            YongyeConfig cfg = YongyeConfig.get();
            // ① 全服暗红聊天(带坐标),保持原行为
            world.getServer().getPlayerManager().broadcast(
                    Text.literal("【灾厄核心】黑暗在 " + corePos.getX() + ", " + corePos.getZ() + " 凝聚……前去摧毁它")
                            .formatted(Formatting.DARK_RED), false);

            // ② 提示增强:给通知半径内玩家 音效 + 屏幕中央标题(比一行会滚走的聊天更醒目)
            if (cfg.coreSpawnTitle) {
                Text titleText = Text.literal("灾厄核心降临").formatted(Formatting.DARK_RED, Formatting.BOLD);
                Text subText = Text.literal("一处核心在 " + corePos.getX() + ", " + corePos.getZ() + " 凝聚——前去摧毁")
                        .formatted(Formatting.RED);
                double rSq = (double) cfg.coreSpawnNotifyRadius * cfg.coreSpawnNotifyRadius;
                for (ServerPlayerEntity sp : world.getServer().getPlayerManager().getPlayerList()) {
                    if (sp.getWorld() != world) continue;
                    if (sp.squaredDistanceTo(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5) > rSq) continue;
                    // 标题动画:淡入 10t、停留 60t、淡出 20t;先发动画参数再发标题/副标题
                    sp.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
                    sp.networkHandler.sendPacket(new TitleS2CPacket(titleText));
                    sp.networkHandler.sendPacket(new SubtitleS2CPacket(subText));
                    // 低沉来袭音效(用原版凋灵降临声,带"重大事件"感)
                    sp.playSoundToPlayer(SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.6f, 0.7f);
                }
            }
        }
    }

    /**
     * 建造灾厄祭坛并返回核心方块的实际位置。
     * 结构:5×5 磨制黑石砖底座 + 四角哭泣黑曜石立柱(顶灵魂灯)+ 中央基座,核心置于基座顶(base.up(2))。
     * 关掉 coreAltarStructure 时退回旧行为:核心直接放在地面 pos。
     */
    private static BlockPos buildAltar(ServerWorld world, BlockPos base) {
        if (!YongyeConfig.get().coreAltarStructure) {
            world.setBlockState(base, ModBlocks.CATASTROPHE_CORE.getDefaultState());
            return base;
        }
        // 5×5 底座 + 清理上方,避免被地形/树木埋住
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = base.add(dx, 0, dz);
                world.setBlockState(p, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
                for (int dy = 1; dy <= 3; dy++) world.setBlockState(p.up(dy), Blocks.AIR.getDefaultState());
            }
        }
        // 四角立柱(哭泣黑曜石,2 高)+ 顶部灵魂灯
        int[][] corners = {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}};
        for (int[] c : corners) {
            BlockPos col = base.add(c[0], 1, c[1]);
            world.setBlockState(col, Blocks.CRYING_OBSIDIAN.getDefaultState());
            world.setBlockState(col.up(), Blocks.CRYING_OBSIDIAN.getDefaultState());
            world.setBlockState(col.up(2), Blocks.SOUL_LANTERN.getDefaultState());
        }
        // 中央基座 + 核心(核心位于 base.up(2),四周立柱环绕)
        world.setBlockState(base.up(1), Blocks.CRYING_OBSIDIAN.getDefaultState());
        BlockPos corePos = base.up(2);
        world.setBlockState(corePos, ModBlocks.CATASTROPHE_CORE.getDefaultState());
        return corePos;
    }

    /**
     * 给每个玩家下发"距离最近的灾厄核心位置"(在 coreLocatorRange 内),供客户端 HUD 画方向箭头。
     * 关掉功能时下发 has=false,客户端随即清掉箭头。
     */
    private static void sendLocators(MinecraftServer server, ServerWorld world) {
        YongyeConfig cfg = YongyeConfig.get();
        boolean on = cfg.enableCoreLocator;
        double rangeSq = (double) cfg.coreLocatorRange * cfg.coreLocatorRange;
        for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
            if (sp.getWorld() != world) {
                com.yongye.network.YongyeNet.sendCoreLocator(sp, false, 0, 0, 0);
                continue;
            }
            BlockPos best = null;
            double bestSq = Double.MAX_VALUE;
            if (on) {
                for (long key : cores) {
                    BlockPos p = BlockPos.fromLong(key);
                    double d = sp.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                    if (d < bestSq) { bestSq = d; best = p; }
                }
            }
            if (best != null && bestSq <= rangeSq) {
                com.yongye.network.YongyeNet.sendCoreLocator(sp, true, best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5);
            } else {
                com.yongye.network.YongyeNet.sendCoreLocator(sp, false, 0, 0, 0);
            }
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

    /**
     * 玩家死亡激发:消耗距死亡点最近的核心(直接移除登记 + 把核心块换成哭泣黑曜石残骸,
     * 绕过 onDestroyed 的赎夜逻辑),并让永夜提升一层。需要世界中已有祭坛才触发。
     */
    private static void completeAltarOnDeath(ServerWorld world, BlockPos deathPos) {
        // 找最近的核心
        long bestKey = 0;
        double bestSq = Double.MAX_VALUE;
        boolean found = false;
        for (long key : cores) {
            BlockPos p = BlockPos.fromLong(key);
            double d = p.getSquaredDistance(deathPos);
            if (d < bestSq) { bestSq = d; bestKey = key; found = true; }
        }
        if (!found) return;
        BlockPos corePos = BlockPos.fromLong(bestKey);
        cores.remove(bestKey); // 先摘登记,避免 tickCores 走 onDestroyed(那会赎夜,与升级冲突)
        // 把核心块换成残骸(若该区块已加载);未加载也无妨,反正已不在登记表里
        if (world.getBlockState(corePos).isOf(ModBlocks.CATASTROPHE_CORE)) {
            world.setBlockState(corePos, Blocks.CRYING_OBSIDIAN.getDefaultState());
        }
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, corePos.getX() + 0.5, corePos.getY() + 1.0, corePos.getZ() + 0.5,
                60, 0.6, 0.8, 0.6, 0.05);
        if (world.getServer() != null) {
            world.getServer().getPlayerManager().broadcast(
                    Text.literal("【灾厄核心】有人倒下,祭坛凝聚完毕——黑暗更深一层").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
            NightfallManager.escalate(world.getServer());
        }
    }
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
