package com.yongye.client;

/**
 * FX 运行时统计(m411,路线图第 23 项;m389 自 22 号迁来的"计数型总预算与统计"落地形态)。
 * 各特效管理器在"新增/被裁"处打点,本类按 20 tick(≈1s)滚动出每秒用量与丢弃数,
 * FxDebugHud 消费展示。纯客户端主线程读写零并发;打点是两次数组自增,热路径零负担。
 * 注:m381 的 FxBudget 仍是无状态质量缩放工具,两类各司其职——Budget 定"缩多少",本类记"发生了什么"。
 */
public final class FxStats {
    private FxStats() {}

    public static final int NUM = 0, BEAM = 1, BAR = 2, OVERLAY = 3, CARD = 4;
    public static final String[] CN = {"飘字", "光柱", "血条", "叠层", "拾取卡"};
    private static final int N = 5;

    private static final int[] accUsed = new int[N], accDrop = new int[N];
    private static final int[] secUsed = new int[N], secDrop = new int[N];
    private static int tickCtr = 0;

    /** 新增了一个该类特效(收包/入队/起播时打)。 */
    public static void used(int cat) { accUsed[cat]++; }

    /** 该类特效被预算/上限/关档裁掉一个。 */
    public static void dropped(int cat) { accDrop[cat]++; }

    /** 每客户端 tick 调一次;每 20t 滚动快照成"每秒"读数。 */
    public static void tick() {
        if (++tickCtr < 20) return;
        tickCtr = 0;
        for (int i = 0; i < N; i++) {
            secUsed[i] = accUsed[i]; accUsed[i] = 0;
            secDrop[i] = accDrop[i]; accDrop[i] = 0;
        }
    }

    public static int perSecUsed(int cat) { return secUsed[cat]; }
    public static int perSecDropped(int cat) { return secDrop[cat]; }
}
