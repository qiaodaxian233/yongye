package com.yongye.system;

import com.yongye.YongyeConfig;
import net.minecraft.server.MinecraftServer;

/**
 * m335:卡顿护栏(作者:「战斗爽召唤的怪物太卡,按游戏卡顿上限来刷怪」)。
 * 以服务端平均 MSPT(getAverageTickTime,yarn 1.21.1 官方映射 method_54832 已核)为准的中央节流:
 *  - MSPT ≤ soft(默认 35ms):全量刷;
 *  - soft~hard(默认 48ms)之间:线性降量(45ms 时约砍 3/4);
 *  - ≥ hard:本波直接跳过(先让服务器喘气,下一波再补)。
 * 接入点:夜袭尸潮 / 烛光域爆发 / 自定义 BOSS·精英投放。任务刷怪(据点守卫等)不节流——任务必须能完成。
 */
public final class LagGuard {
    private LagGuard() {}

    /** 当前允许的刷怪系数 0~1。 */
    public static double spawnFactor(MinecraftServer server) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.lagGuardEnabled) return 1.0;
        float mspt = server.getAverageTickTime();
        double soft = Math.max(1.0, cfg.lagGuardSoftMspt);
        double hard = Math.max(soft + 1.0, cfg.lagGuardHardMspt);
        if (mspt <= soft) return 1.0;
        if (mspt >= hard) return 0.0;
        return 1.0 - (mspt - soft) / (hard - soft);
    }

    /** 把计划刷怪数按当前卡顿缩放:0=本波跳过;有余量时至少保 1 只(不至于完全没仗打)。 */
    public static int scale(MinecraftServer server, int planned) {
        if (planned <= 0) return 0;
        double f = spawnFactor(server);
        if (f <= 0.0) return 0;
        if (f >= 0.999) return planned;
        return Math.max(1, (int) Math.floor(planned * f));
    }
}
