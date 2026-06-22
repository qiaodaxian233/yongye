package com.yongye.system;

import com.google.gson.Gson;
import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 永夜灾变系统(文档第 10 章)。
 *  - 等级 0(正常)~ 5(灭世)。
 *  - 文件持久化到存档目录 yongye_nightfall.json。
 *  - level>=1 时世界锁定为黑夜。
 *  - 等级影响精英概率与怪物锁定半径。
 */
public final class NightfallManager {
    private NightfallManager() {}

    private static final Gson GSON = new Gson();
    private static final String[] NAMES = {"昼夜正常", "永夜 I · 暗潮", "永夜 II · 猎杀", "永夜 III · 围城", "永夜 IV · 灾变", "永夜 V · 灭世"};

    private static int level = 0;
    private static Path savePath;
    private static int tickCounter = 0;

    private static class State { int level; }

    public static int getLevel() { return level; }

    public static String getLevelName() {
        if (level < NAMES.length) return NAMES[Math.max(0, level)];
        // 超过 V5(灭世)后:深渊层,层数 = level-5
        return "永夜 · 深渊 " + (level - 5) + " 层";
    }

    public static double getEliteChanceMultiplier() {
        double[] arr = YongyeConfig.get().nightfallEliteChanceMultiplier;
        return arr[Math.max(0, Math.min(arr.length - 1, level))];
    }

    public static double getLockRadius() {
        double[] arr = YongyeConfig.get().nightfallLockRadius;
        return arr[Math.max(0, Math.min(arr.length - 1, level))];
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            savePath = server.getSavePath(WorldSavePath.ROOT).resolve("yongye_nightfall.json");
            load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!YongyeConfig.get().enableNightfall) return;
            if (level < 1) return;
            if (++tickCounter < 20) return;
            tickCounter = 0;
            lockNight(server);
        });

        Yongye.LOGGER.info("[永夜] 永夜系统已挂载");
    }

    private static void lockNight(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;
        long t = overworld.getTimeOfDay();
        long tod = t % 24000L;
        if (tod < 13000L) {
            overworld.setTimeOfDay(t - tod + 14000L);
        }
    }

    public static void setLevel(MinecraftServer server, int newLevel) {
        int clamped = Math.max(0, Math.min(YongyeConfig.get().nightfallMaxLevel, newLevel));
        if (clamped == level) return;
        level = clamped;
        save();
        broadcast(server, Text.literal("【永夜】世界进入:" + getLevelName()).formatted(Formatting.DARK_PURPLE));
        for (net.minecraft.server.network.ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            com.yongye.network.YongyeNet.sendNightfall(p);
        }
    }

    public static void escalate(MinecraftServer server) {
        setLevel(server, level + 1);
    }

    /** 赎夜:降低一级。 */
    public static void redeem(MinecraftServer server) {
        if (level <= 0) {
            broadcast(server, Text.literal("【永夜】昼夜本就正常,无需赎夜。").formatted(Formatting.GRAY));
            return;
        }
        setLevel(server, level - 1);
        broadcast(server, Text.literal("【赎夜】黑暗退去一分,世界恢复到:" + getLevelName()).formatted(Formatting.AQUA));
    }

    private static void broadcast(MinecraftServer server, Text msg) {
        server.getPlayerManager().broadcast(msg, false);
    }

    private static void load() {
        try {
            if (savePath != null && Files.exists(savePath)) {
                State s = GSON.fromJson(Files.readString(savePath), State.class);
                if (s != null) level = Math.max(0, Math.min(YongyeConfig.get().nightfallMaxLevel, s.level));
            }
        } catch (IOException | RuntimeException e) {
            Yongye.LOGGER.error("[永夜] 读取永夜状态失败", e);
        }
    }

    private static void save() {
        try {
            if (savePath == null) return;
            State s = new State();
            s.level = level;
            Files.writeString(savePath, GSON.toJson(s));
        } catch (IOException e) {
            Yongye.LOGGER.error("[永夜] 写入永夜状态失败", e);
        }
    }
}
