package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.entity.AnubisEntity;
import com.yongye.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * 阿努比斯 BOSS 自然降临(m175):游戏第 anubisMinDay 天起,每隔一段时间检定,
 * 有 anubisSpawnChance 概率在某个玩家附近的地表刷出一只阿努比斯(出生即锁定该玩家)。
 *
 * <p>模板 = m165 WildDragonSpawnHandler(全服存活上限 + 天数门槛 + 概率检定 + 播报),
 * 差异:阿努比斯是<b>地面 BOSS</b>,落点用 getTopY(WORLD_SURFACE) 找地表
 * (PainBossHandler / NightfallHordeHandler 同款 proven 写法),不刷高空。
 *
 * <p>全服同时存活数受 anubisMaxAlive 限制(默认 1,稀有 BOSS 事件)。
 */
public final class AnubisSpawnHandler {
    private AnubisSpawnHandler() {}

    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableAnubisSpawn) return;
            if (++tick < Math.max(1, cfg.anubisCheckIntervalTicks)) return;
            tick = 0;

            // 全服存活阿努比斯计数(复用 m155 已验证的 getWorlds()/iterateEntities())
            int alive = 0;
            for (ServerWorld w : server.getWorlds()) {
                for (Entity e : w.iterateEntities()) {
                    if (e instanceof AnubisEntity a && a.isAlive()) alive++;
                }
            }
            if (alive >= Math.max(1, cfg.anubisMaxAlive)) return;

            // 收集合法玩家:生存/冒险 + 在 ServerWorld + 已到达天数门槛
            List<ServerPlayerEntity> eligible = new ArrayList<>();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                GameMode gm = player.interactionManager.getGameMode();
                if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) continue;
                if (!(player.getWorld() instanceof ServerWorld world)) continue;
                if (ProgressionManager.gameDay(world) < cfg.anubisMinDay) continue;
                eligible.add(player);
            }
            if (eligible.isEmpty()) return;

            // 概率检定(每次检定全服一次机会)
            if (server.getOverworld().getRandom().nextDouble() >= cfg.anubisSpawnChance) return;

            ServerPlayerEntity target = eligible.get(
                    server.getOverworld().getRandom().nextInt(eligible.size()));
            spawnAnubisNear(target);
        });
        Yongye.LOGGER.info("[夜蚀] 阿努比斯自然降临系统已挂载");
    }

    private static void spawnAnubisNear(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        // 玩家附近 12~24 格水平偏移,落点取地表(地面 BOSS,不刷高空)。
        var r = world.getRandom();
        double dist = 12.0 + r.nextDouble() * 12.0;
        double angle = r.nextDouble() * Math.PI * 2.0;
        int sx = (int) Math.round(player.getX() + Math.cos(angle) * dist);
        int sz = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
        int sy = world.getTopY(Heightmap.Type.WORLD_SURFACE, sx, sz);

        AnubisEntity anubis = new AnubisEntity(ModEntities.ANUBIS, world);
        anubis.refreshPositionAndAngles(sx + 0.5, sy, sz + 0.5, r.nextFloat() * 360f, 0f);
        world.spawnEntity(anubis);
        anubis.setTarget(player); // 出生即锁定该玩家

        world.getServer().getPlayerManager().broadcast(
                Text.literal("黄沙翻涌——永恒的裁判者·阿努比斯降临人间!")
                        .formatted(Formatting.GOLD, Formatting.BOLD),
                false);
    }
}
