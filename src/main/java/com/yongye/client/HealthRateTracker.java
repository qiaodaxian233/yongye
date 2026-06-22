package com.yongye.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * 客户端血量速率采样器。
 *
 * 作用:逐客户端 tick 记录玩家当前生命到一个约 1 秒(20 tick)的环形缓冲,
 * 计算「最近一秒的净血量变化」并换算成「每秒速率」(正=回血,负=掉血),
 * 供 HUD 在血量旁显示「+X.X/s」之类的实时回血提示。
 *
 * 说明:
 *  - 纯客户端、零网络。HUD 里血量数字本身实时读 {@code player.getHealth()},
 *    本类只负责算「速率」这个导数量(需要时间窗口平滑,不能逐帧裸算)。
 *  - 用「带 tick 戳的环形缓冲 + 扫描窗口内最旧样本」实现:语义清晰
 *    (= 最近一秒净变化),启动阶段(样本不足 1 秒)也能正确退化。
 *  - 单次大额变化(如被秒掉一大截血)会在速率里停留约 1 秒后自然滑出,
 *    这恰好等于「最近一秒你净损失了多少血」,是诚实且有用的读数。
 */
public final class HealthRateTracker {

    /** 窗口大小:21 个槽 = 略多于 1 秒(20 tick),容纳「当前 + 1 秒前」两端样本。 */
    private static final int SIZE = 21;
    /** 窗口时长(tick):1 秒。 */
    private static final int WINDOW_TICKS = 20;

    private static final float[] hpBuf = new float[SIZE]; // 各样本的血量
    private static final long[] tickBuf = new long[SIZE]; // 各样本对应的 tick 序号

    private static int filled = 0;        // 已写入样本数(上限 SIZE)
    private static int writePos = 0;      // 下一个写入位置(环形)
    private static long tickCounter = 0;  // 单调递增的 tick 计数
    private static float ratePerSec = 0f; // 平滑后的每秒净变化

    private HealthRateTracker() {}

    /**
     * 每客户端 tick 调用一次,刷新速率。
     * 不在世界内(主菜单/死亡后无玩家)时清空,避免重进世界时用陈旧样本算出离谱速率。
     */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            reset();
            return;
        }

        float cur = p.getHealth();
        tickCounter++;

        // 写入当前样本
        hpBuf[writePos] = cur;
        tickBuf[writePos] = tickCounter;
        writePos = (writePos + 1) % SIZE;
        if (filled < SIZE) filled++;

        // 在「最近 1 秒(20 tick)」内找最旧的样本,用它和当前值算净变化
        long cutoff = tickCounter - WINDOW_TICKS; // 早于此 tick 的样本视为过期
        float refHp = cur;
        long refTick = tickCounter;
        for (int i = 0; i < filled; i++) {
            if (tickBuf[i] >= cutoff && tickBuf[i] < refTick) {
                refTick = tickBuf[i];
                refHp = hpBuf[i];
            }
        }

        long dtTicks = tickCounter - refTick;
        // dtTicks 个 tick = dtTicks/20 秒;速率 = 血量差 / 时间(秒)
        ratePerSec = (dtTicks > 0) ? (cur - refHp) / (dtTicks / 20f) : 0f;
        // 极小波动归零,避免长期残留 ±0.0x 抖动
        if (Math.abs(ratePerSec) < 0.1f) ratePerSec = 0f;
    }

    /** 当前每秒血量净变化:正=回血,负=掉血,约 0 视为静止。 */
    public static float getRatePerSec() {
        return ratePerSec;
    }

    /** 清空采样(离开世界时调用)。 */
    public static void reset() {
        filled = 0;
        writePos = 0;
        tickCounter = 0;
        ratePerSec = 0f;
    }
}
