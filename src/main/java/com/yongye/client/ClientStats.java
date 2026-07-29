package com.yongye.client;

/**
 * 客户端侧缓存:由服务端 StatsPayload 更新,成长面板 / 背包职业标签读取。
 */
public final class ClientStats {
    private ClientStats() {}

    public static int health = 0;
    public static int[] levels = new int[0];
    public static String className = "";   // 本命职业 id("" = 无)
    /** 当前职业资源值 0.0~1.0(由 MpSyncPayload 每10tick更新) */
    public static float mp = 0f;
    /** 服务端同步的最终攻击伤害(由 AttackSyncPayload 更新;<0 = 尚未收到) */
    public static double attackDamage = -1;

    // ===== m278 格挡状态(由 GuardSyncPayload 每 5t 更新) =====
    /** 当前格挡值 / 上限(上限=最大生命×比例)。 */
    public static float guardCur = 0f, guardMax = 0f;
    /** 破防剩余 tick(>0=硬直中;客户端每 tick 递减,倒计时平滑)。 */
    public static int guardBroken = 0;
    /** 是否正持有可格挡武器(不持械且满值时面板隐藏格挡条)。 */
    public static boolean guardHolding = false;
    /** 本帧面板是否画了格挡条(HudCompactMixin 每帧回写)——永夜阶段名/核心箭头据此连锁上移 6px。 */
    public static boolean guardBarShown = false;

    // ===== m288 战况看板(由 HudInfoPayload 每 20t 更新) =====
    /** 累计杀怪总数。 */
    public static long totalKills = 0;
    /** 下一阶段名(""=已至上限不显示预告)。 */
    public static String nextStageName = "";
    /** 距久留自动升层剩余秒数(-1=不适用,只显示阶段名)。 */
    public static int nextStageSeconds = -1;
    /** m289 按天事件预告(服务端按实时配置拼好;""=无未到事件不显示)。 */
    public static String dayForecast = "";
    /** m308 预告紧凑版(hudInfoCompact 开时显示,如「3天后:佩恩降临+1」)。 */
    public static String dayForecastShort = "";
    /** m361 主线目标常显行(""=不显示;含「已达成」时看板转绿提醒领奖)。 */
    public static String mainGoal = "";
    /** m363 当前主线阶段号(渐进解锁门控;-1=尚未收到,全部按钮先显示防误锁)。 */
    public static int mainStage = -1;

    public static void update(int h, int[] l, String cn) {
        health = h;
        levels = l;
        className = cn == null ? "" : cn;
    }
}
