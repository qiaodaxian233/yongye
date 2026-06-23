package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 定时清理地面掉落物。
 * <p>服务器启动后第 {@code itemCleanupFirstMinutes} 分钟进行首次清理,之后每 {@code itemCleanupIntervalMinutes}
 * 分钟清理一次。清理前按 60/30/10/5/4/3/2/1 秒全服倒计时提示,给玩家捡拾时间。
 * <p>计时基于 {@code server.getTicks()}(服务器运行 tick,重启归零;1 分钟 = 1200 tick)。
 */
public final class ItemCleanupHandler {
    private ItemCleanupHandler() {}

    /** 倒计时提示节点(秒):60/30 用聊天栏,≤10 用动作栏避免刷屏。 */
    private static final int[] WARN_SECONDS = {60, 30, 10, 5, 4, 3, 2, 1};
    private static final int TICKS_PER_MINUTE = 1200;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableItemCleanup) return;

            int first    = Math.max(1, cfg.itemCleanupFirstMinutes)    * TICKS_PER_MINUTE;
            int interval = Math.max(1, cfg.itemCleanupIntervalMinutes) * TICKS_PER_MINUTE;
            long t = server.getTicks();

            // 到点清理:首次在 first,之后每 interval 一次
            if (t >= first && (t - first) % interval == 0) {
                doCleanup(server);
                return;
            }

            // 距离下一次清理还剩多少 tick
            long until;
            if (t < first) {
                until = first - t;
            } else {
                long phase = (t - first) % interval;          // phase!=0(==0 已在上面清理并 return)
                until = interval - phase;
            }

            // 倒计时提示:精确落在某节点的那一 tick 才触发,每个节点恰好播一次
            for (int sec : WARN_SECONDS) {
                if (until == (long) sec * 20) {
                    warn(server, sec);
                    break;
                }
            }
        });
        Yongye.LOGGER.info("[永夜] 掉落物定时清理已挂载");
    }

    private static void warn(MinecraftServer server, int sec) {
        Formatting color = sec <= 5 ? Formatting.RED : (sec <= 10 ? Formatting.GOLD : Formatting.YELLOW);
        Text msg = Text.literal("【清理】" + sec + " 秒后清理地面掉落物,请及时捡起需要的物品!").formatted(color);
        boolean overlay = sec <= 10;   // ≤10 秒走动作栏,不刷聊天屏
        server.getPlayerManager().broadcast(msg, overlay);
    }

    private static void doCleanup(MinecraftServer server) {
        int total = 0;
        for (ServerWorld world : server.getWorlds()) {
            // 先收集再删除,避免遍历途中结构性修改
            List<ItemEntity> items = new ArrayList<>();
            for (Entity e : world.iterateEntities()) {        // 待编译验证:ServerWorld.iterateEntities() 全实体遍历(失败则改用 getEntitiesByType(EntityType.ITEM, ...))
                if (e instanceof ItemEntity ie && ie.isAlive()) items.add(ie);
            }
            for (ItemEntity ie : items) { ie.discard(); total++; }
        }
        if (total > 0) {
            server.getPlayerManager().broadcast(
                    Text.literal("【清理】已清除地面掉落物 " + total + " 个。").formatted(Formatting.GRAY), false);
        }
        Yongye.LOGGER.info("[永夜] 定时清理掉落物:{} 个", total);
    }
}
