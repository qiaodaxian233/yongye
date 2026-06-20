package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * 时间进度系统(类「惊变」):按游戏天数驱动——
 * 第一天长白天 + 新手保护 → 入夜刷怪 → 一晚比一晚强 →
 * 第3天起小概率精英 → 第5天起精英概率大增 → 每10天进化 → 早期怪不会挖方块。
 */
public final class ProgressionManager {
    private ProgressionManager() {}

    private static double day1Time = -1;

    public static long gameDay(World world) {
        return world.getTimeOfDay() / 24000L;
    }

    /** 新手保护:仅第一天白天(总时间 < 12000)。 */
    public static boolean isNewbie(World world) {
        YongyeConfig cfg = YongyeConfig.get();
        return cfg.enableProgression && cfg.newbieProtectDay1 && world.getTimeOfDay() < 12000L;
    }

    /** 精英刷新概率倍率:第3天前=0;第5天起大幅提升;第3~4天小概率。 */
    public static double eliteChanceMultiplier(World world) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableProgression) return 1.0;
        long d = gameDay(world);
        if (d < cfg.eliteStartDay) return 0.0;
        if (d >= cfg.eliteBoostDay) return cfg.eliteBoostMultiplier;
        return cfg.eliteEarlyMultiplier;
    }

    /** 进化倍率:每 evolutionEveryDays 天 +evolutionPerStage。 */
    public static double evolutionMultiplier(World world) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableProgression) return 1.0;
        long stage = gameDay(world) / Math.max(1, cfg.evolutionEveryDays);
        return 1.0 + stage * cfg.evolutionPerStage;
    }

    /** 早期怪不能挖方块,达到 mobDigStartDay 天才解锁。 */
    public static boolean canMobsDig(World world) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableProgression) return true;
        return gameDay(world) >= cfg.mobDigStartDay;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ProgressionManager::tickFirstDay);
        Yongye.LOGGER.info("[亡途荒夜] 时间进度系统已挂载");
    }

    /** 第一天白天放慢到 firstDayMinutes 分钟(仅主世界、仅首个白天 0..12000)。 */
    private static void tickFirstDay(MinecraftServer server) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableProgression || !cfg.firstDayLong) { day1Time = -1; return; }
        ServerWorld ow = server.getOverworld();
        if (ow == null) return;
        long tod = ow.getTimeOfDay();
        if (tod >= 12000L) { day1Time = -1; return; } // 首个白天结束,交还原版
        double target = Math.max(1.0, cfg.firstDayMinutes * 60.0 * 20.0); // 目标白天 tick 数
        double rate = 12000.0 / target;                                   // 每 tick 有效推进(<1 即放慢)
        if (day1Time < 0) day1Time = tod;
        day1Time += rate;
        ow.setTimeOfDay((long) day1Time);
    }
}
