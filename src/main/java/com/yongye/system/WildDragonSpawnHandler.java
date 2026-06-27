package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.entity.ToroEnderDragonEntity;
import com.yongye.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 野生末影龙 BOSS:游戏第 wildDragonMinDay 天起,每隔一段时间检定,有 wildDragonSpawnChance 概率
 * 在某个玩家头顶高空刷出一条会飞的末影龙(ToroEnderDragonEntity,飞行 AI、出生即锁定该玩家)。
 *
 * <p>全服同时存活数受 wildDragonMaxAlive 限制(默认 1,稀有 BOSS 事件),不会刷成一片。
 */
public final class WildDragonSpawnHandler {
    private WildDragonSpawnHandler() {}

    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableWildDragonSpawn) return;
            if (++tick < Math.max(1, cfg.wildDragonCheckIntervalTicks)) return;
            tick = 0;

            // 全服存活龙计数(复用 m155 已验证的 getWorlds()/iterateEntities())
            int alive = 0;
            for (ServerWorld w : server.getWorlds()) {
                for (Entity e : w.iterateEntities()) {
                    if (e instanceof ToroEnderDragonEntity d && d.isAlive()) alive++;
                }
            }
            if (alive >= Math.max(1, cfg.wildDragonMaxAlive)) return;

            // 收集合法玩家:生存/冒险 + 在 ServerWorld + 已到达天数门槛
            List<ServerPlayerEntity> eligible = new ArrayList<>();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                GameMode gm = player.interactionManager.getGameMode();
                if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) continue;
                if (!(player.getWorld() instanceof ServerWorld world)) continue;
                if (ProgressionManager.gameDay(world) < cfg.wildDragonMinDay) continue;
                eligible.add(player);
            }
            if (eligible.isEmpty()) return;

            // 概率检定(每次检定全服一条机会)
            if (server.getOverworld().getRandom().nextDouble() >= cfg.wildDragonSpawnChance) return;

            ServerPlayerEntity target = eligible.get(
                    server.getOverworld().getRandom().nextInt(eligible.size()));
            spawnDragonAbove(target, cfg);
        });
        Yongye.LOGGER.info("[永夜] 野生末影龙刷怪系统已挂载");
    }

    private static void spawnDragonAbove(ServerPlayerEntity player, YongyeConfig cfg) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        // 玩家上方高空生成(龙会飞、无重力,不需要落点);稍加水平偏移避免正头顶。
        var r = world.getRandom();
        double ox = (r.nextDouble() - 0.5) * 16.0;
        double oz = (r.nextDouble() - 0.5) * 16.0;
        double sx = player.getX() + ox;
        double sz = player.getZ() + oz;
        double sy = Math.min(player.getY() + cfg.wildDragonSpawnHeight, world.getTopY() - 4);

        ToroEnderDragonEntity dragon =
                new ToroEnderDragonEntity(ModEntities.TORO_ENDER_DRAGON, world);
        dragon.refreshPositionAndAngles(sx, sy, sz, r.nextFloat() * 360f, 0f);
        world.spawnEntity(dragon);
        dragon.setTarget(player); // 出生即锁定该玩家,从高空俯冲追杀

        world.getServer().getPlayerManager().broadcast(
                Text.literal("天空骤暗——一条末影龙盘旋而来!").formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
                false);
    }
}
