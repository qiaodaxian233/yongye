package com.yongye.client;

import com.yongye.YongyeConfig;

/**
 * FX 统一质量档与降级入口(m381,3A 打磨路线图第 22 项,按 m379 评审提前落地;
 * m389 评审定性修正:本类是<b>无状态的质量缩放工具</b>,不是运行时计数预算——
 * 没有类别额度/每帧消耗/拒绝统计,原路线图写的 FxBudget.allow(类别,代价) 那半截
 * 「计数型总预算与统计」并入第 23 项 FX 调试面板一起做,届时再补 beginFrame/
 * tryConsume/used/dropped 一族,现有各管理器接法不变):
 * 全模组客户端特效不再各自判断"低配模式",统一向本类查询质量档并做<b>动态降级而非硬丢</b>。
 *
 * <p>质量档 {@code fxQuality}(/yongye config set fxQuality N 即改即生效,HUD/渲染每帧读):
 * <pre>
 * 0 = OFF   全部装饰性特效关闭(功能性 HUD 不受影响)
 * 1 = LOW   量 ×0.4、寿命 ×0.7、可见距离 ×0.6、几何分段减半
 * 2 = MEDIUM 量 ×0.7、寿命 ×0.9、可见距离 ×0.85
 * 3 = HIGH  全量(默认)
 * </pre>
 *
 * <p>各效果的降级口径(接入点在各管理器,统一走这里的助手):
 * 粒子=降数量(scaleCount);飘字=降同屏上限与寿命(scaleCount/scaleLife);
 * 光柱=缩可见距离+LOW 裁外圈分段(scaleDistSq/lowDetail);受击弧=LOW 减子段;
 * 屏幕叠层(界面淡入/转场/按钮动效)=OFF 档整体让位(on()),各自专属开关照常独立。
 * 服务端侧特效量(命中粒子/飘字发包限额)另走 LagGuard(MSPT 口径,m335)——
 * 客户端按画质降、服务端按卡顿降,两头各管各的互不越权。
 *
 * <p>纯静态工具零状态零 API 面;新增效果一律接本闸(POLISH_ROADMAP DoD 第 5 条)。
 */
public final class FxBudget {
    private FxBudget() {}

    public static final int OFF = 0, LOW = 1, MEDIUM = 2, HIGH = 3;

    /** 当前质量档(钳 0~3)。 */
    public static int quality() {
        int q = YongyeConfig.get().fxQuality;
        return Math.max(OFF, Math.min(HIGH, q));
    }

    /** m418(路线图26)HUD 安全边距 X(超宽屏/曲面屏/录播遮挡:边缘停靠元素统一向内收;0~80 钳制)。 */
    public static int safeX() {
        return Math.max(0, Math.min(80, YongyeConfig.get().hudSafeMarginX));
    }

    /** m418 HUD 安全边距 Y。 */
    public static int safeY() {
        return Math.max(0, Math.min(80, YongyeConfig.get().hudSafeMarginY));
    }

    /** m417(路线图25)闪光强度中枢:低刺激整档 ×0.25 / 弱闪光 ×0.5 / 常规 ×1。
     *  所有整屏闪/罩/晕类一律乘此值(替代各处手写 reduceScreenFlash 三目)。 */
    public static float flashScale() {
        var c = YongyeConfig.get();
        return c.lowStimulusMode ? 0.25f : c.reduceScreenFlash ? 0.5f : 1f;
    }

    /** m417 动感中枢(震屏/FOV冲击/顿帧):低刺激整档 ×0.3 / 常规 ×1。 */
    public static float motionScale() {
        return YongyeConfig.get().lowStimulusMode ? 0.3f : 1f;
    }

    /** m417 脉冲类装饰(CD转好闪/加点脉冲/标题呼吸)是否保留:低刺激整档=否。 */
    public static boolean pulseOn() {
        return !YongyeConfig.get().lowStimulusMode;
    }

    /** 装饰性特效是否开启(OFF 档 = false)。 */
    public static boolean on() { return quality() > OFF; }

    /** LOW 档(几何分段/装饰层裁剪用)。 */
    public static boolean lowDetail() { return quality() <= LOW; }

    /** 数量倍率:OFF 0 / LOW 0.4 / MEDIUM 0.7 / HIGH 1。 */
    public static double amountScale() {
        return switch (quality()) {
            case OFF -> 0.0;
            case LOW -> 0.4;
            case MEDIUM -> 0.7;
            default -> 1.0;
        };
    }

    /** 按档缩数量(向下取整,HIGH 原样)。 */
    public static int scaleCount(int n) {
        return (int) Math.floor(n * amountScale());
    }

    /** 按档缩寿命(毫秒):LOW ×0.7 / MEDIUM ×0.9。 */
    public static long scaleLife(long ms) {
        return switch (quality()) {
            case LOW -> (long) (ms * 0.7);
            case MEDIUM -> (long) (ms * 0.9);
            default -> ms;
        };
    }

    /** 按档缩可见距离平方:LOW ×0.36(距离×0.6)/ MEDIUM ×0.72(距离×0.85)。 */
    public static double scaleDistSq(double distSq) {
        return switch (quality()) {
            case LOW -> distSq * 0.36;
            case MEDIUM -> distSq * 0.72;
            default -> distSq;
        };
    }
}
