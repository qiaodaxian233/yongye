package com.yongye.system;

import com.google.gson.Gson;
import com.yongye.Yongye;
import com.yongye.item.GameDifficulty;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 世界难度:整局一个值,持久化到存档目录 yongye_difficulty.json(与永夜等级同套路)。
 *  - level = -1 未设定;0~6 对应 {@link GameDifficulty} 的 ordinal(游玩~永夜)。
 *  - 只有房主/OP 在「还没设定难度」的世界首次进入时弹界面选择(见 YongyeNet 的 JOIN);
 *    选定后全世界锁定,之后任何人联机都不再弹。
 *  - 所有怪物按 {@link #mobMult()} 统一缩放(DynamicScaling 调用),不再逐玩家区分。
 */
public final class DifficultyManager {
    private DifficultyManager() {}

    private static final Gson GSON = new Gson();
    private static int level = -1;       // -1 = 未设定
    private static Path savePath;

    private static class State { int level = -1; }

    public static boolean isSet() { return level >= 0; }
    public static int getLevel() { return level; }

    /** 世界难度的怪物强度倍率;未设定按「适中」(1.0)。 */
    public static double mobMult() {
        return level < 0 ? GameDifficulty.NORMAL.mobMult : GameDifficulty.byOrdinal(level).mobMult;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            savePath = server.getSavePath(WorldSavePath.ROOT).resolve("yongye_difficulty.json");
            load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
        Yongye.LOGGER.info("[永夜] 世界难度系统已挂载");
    }

    /** 设定世界难度(0~6),全服播报并写存档。命令 /yongye difficulty 与界面选择都走这里。 */
    public static void setLevel(MinecraftServer server, int newLevel) {
        level = Math.max(0, Math.min(GameDifficulty.values().length - 1, newLevel));
        save();
        GameDifficulty d = GameDifficulty.byOrdinal(level);
        server.getPlayerManager().broadcast(
                Text.literal("【永夜】世界难度设定为:【" + d.cn + "】 怪物强度 ×" + trim(d.mobMult)).formatted(d.color), false);
    }

    private static String trim(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static void load() {
        level = -1;   // 切换世界先归位,避免上一个世界的难度残留到没有难度文件的新世界(静态字段跨世界存活)
        try {
            if (savePath != null && Files.exists(savePath)) {
                State s = GSON.fromJson(Files.readString(savePath), State.class);
                if (s != null) level = s.level;
            }
        } catch (IOException | RuntimeException e) {
            Yongye.LOGGER.error("[永夜] 读取世界难度失败", e);
        }
    }

    private static void save() {
        try {
            if (savePath == null) return;
            State s = new State();
            s.level = level;
            Files.writeString(savePath, GSON.toJson(s));
        } catch (IOException e) {
            Yongye.LOGGER.error("[永夜] 写入世界难度失败", e);
        }
    }
}
