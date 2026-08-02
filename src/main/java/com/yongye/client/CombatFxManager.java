package com.yongye.client;

import java.util.Random;

/**
 * 沉浸式战斗手感——客户端状态机(m239),m421 起兼任<b>摄像机效果统一器</b>(路线图29)。
 * 收 {@code CombatFxPayload} 置入强度,每客户端 tick 指数衰减;
 * 相机抖动由 {@code CameraShakeMixin} 每帧取偏移、FOV 顿挫由 {@code FovKickMixin} 取偏移、
 * 击杀闪光由 YongyeClient 的 HudRenderCallback 取 alpha。全部读静态字段,无锁无分配。
 *
 * <p><b>统一叠加规则(m421 成文,全仓镜头效果唯一口径,防互相打架):</b>
 * <ol>
 *   <li><b>单入口</b>:震屏/FOV/顿帧/闪光四通道只从 kickShake / kickFov / kickHitstop / kickFlash
 *       进(onFx 也走它们)。生产者=服务端 CombatFxPayload(命中/击杀/登场/讨伐等九处构造点)
 *       与客户端本地(强化碎裂)。<b>新特效禁止绕过本类直接改 Camera / FOV / handSwing</b>;</li>
 *   <li><b>低刺激缩放只乘一次</b>:FxBudget.motionScale() 在入口处统一乘,任何生产者不得自乘;</li>
 *   <li><b>通道内取 max 不叠加</b>:连击/多源同刻只保留"最重那一下",不累加不共振;</li>
 *   <li><b>通道硬顶</b>:震屏≤cameraShakeCap(默2.5)/FOV≤cameraFovKickCap(默6°)/
 *       顿帧≤cameraHitstopCap(默6t),配置可调、代码侧再钳一层防手滑;</li>
 *   <li><b>指数衰减</b>:震屏×0.70/t、FOV×0.66/t,顿帧按 tick 计数自然耗尽——效果只会
 *       "冲一下再回落",不存在常驻位移。</li>
 * </ol>
 */
public final class CombatFxManager {
    private CombatFxManager() {}

    private static float shakeStrength = 0f;  // 当前镜头抖动强度(度级)
    private static float fovPunch = 0f;       // 当前 FOV 顿挫(度,正值,应用时取负=瞬间拉近)
    private static int flashTicks = 0;        // 击杀闪光剩余 tick
    private static final int FLASH_MAX_TICKS = 7;
    private static final Random RNG = new Random();

    // ==== m248:注入点存活探针 ====
    // 手感类 mixin 全是 require=0(不符静默不挂,不崩游戏),代价是坏了也看不出来。
    // 各 mixin 处理器第一行调一次 markInjected,首次触发在日志打一行「已生效」——
    // 进游戏后 latest.log 里少了哪行,就是哪个注入点没挂上,不用再瞎猜。
    private static final java.util.Set<String> INJECT_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void markInjected(String name) {
        if (INJECT_SEEN.add(name))
            com.yongye.Yongye.LOGGER.info("[夜蚀] 客户端注入已生效: {}", name);
    }

    // ==== m275:击杀顿帧 ====
    private static int hitstopTicks = 0;          // 剩余定帧 tick
    private static int frozenSwingTicks = 0;      // 定住的挥臂帧
    private static float frozenSwingProgress = 0f;

    /** 收到服务端 FX 包(已在客户端主线程);四通道统一走 kick 入口(m421 规则见类注释)。 */
    public static void onFx(int kind, float shake, float fov, boolean flash, boolean sound, int hitstop) {
        kickShake(shake);
        kickFov(fov);
        kickHitstop(hitstop);
        if (flash) kickFlash();
        if (sound) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player != null)
                mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_ARROW_HIT_PLAYER, 0.55f, 1.6f);
        }
    }

    // ==== m421 摄像机效果统一入口(规则见类注释;新特效一律从这四个口进) ====

    /** 震屏通道:低刺激缩放→与在途效果取 max→硬顶 cameraShakeCap(代码侧钳 0.5~5 防配置手滑)。 */
    public static void kickShake(float raw) {
        if (raw <= 0f) return;
        float cap = (float) Math.max(0.5, Math.min(5.0, com.yongye.YongyeConfig.get().cameraShakeCap));
        shakeStrength = Math.min(cap, Math.max(shakeStrength, raw * FxBudget.motionScale()));
    }

    /** FOV 顿挫通道:同上,硬顶 cameraFovKickCap(钳 1~12°)。 */
    public static void kickFov(float raw) {
        if (raw <= 0f) return;
        float cap = (float) Math.max(1.0, Math.min(12.0, com.yongye.YongyeConfig.get().cameraFovKickCap));
        fovPunch = Math.min(cap, Math.max(fovPunch, raw * FxBudget.motionScale()));
    }

    /** 顿帧通道:低刺激缩短→连杀取最重→硬顶 cameraHitstopCap(钳 0~10t,0=全局关顿帧)。 */
    public static void kickHitstop(int raw) {
        float ms = FxBudget.motionScale();
        if (ms < 1f) raw = Math.round(raw * ms);
        if (raw <= 0) return;
        int cap = Math.max(0, Math.min(10, com.yongye.YongyeConfig.get().cameraHitstopCap));
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;
        hitstopTicks = Math.min(cap, Math.max(hitstopTicks, raw));
        frozenSwingTicks = mc.player.handSwingTicks;
        frozenSwingProgress = mc.player.handSwingProgress;
    }

    /** 击杀闪光通道:定长脉冲,重复触发只重置计时不加亮。 */
    public static void kickFlash() { flashTicks = FLASH_MAX_TICKS; }

    /** 每客户端 tick 衰减(YongyeClient 挂 END_CLIENT_TICK)。 */
    public static void tick() {
        // m275 顿帧:命中/击杀瞬间把第一人称挥臂按住不动几 tick(时停感),到点自然续上。
        // 只回卷挥臂计时,不碰游戏逻辑——纯观感,不影响攻击冷却/判定。
        // 待编译验证:lastHandSwingProgress 公有字段(handSwingTicks/handSwingProgress 在树已用);
        // 若编译报此字段名,删掉那一行即可(只损失一帧插值平滑)。
        if (hitstopTicks > 0) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player != null && mc.player.handSwinging) {
                hitstopTicks--;
                mc.player.handSwingTicks = frozenSwingTicks;
                mc.player.handSwingProgress = frozenSwingProgress;
                mc.player.lastHandSwingProgress = frozenSwingProgress;
            } else {
                hitstopTicks = 0; // 没在挥了就别按了
            }
        }
        shakeStrength *= 0.70f;
        if (shakeStrength < 0.02f) shakeStrength = 0f;
        fovPunch *= 0.66f;
        if (fovPunch < 0.05f) fovPunch = 0f;
        if (flashTicks > 0) flashTicks--;
    }

    /** 相机 yaw 抖动偏移(度):每帧随机。无抖动时恒 0(mixin 据此早退)。 */
    public static float shakeYaw() {
        return shakeStrength <= 0f ? 0f : (RNG.nextFloat() - 0.5f) * 2.0f * shakeStrength;
    }

    /** 相机 pitch 抖动偏移(度):幅度略小于 yaw,更像"手劲"而非晃头。 */
    public static float shakePitch() {
        return shakeStrength <= 0f ? 0f : (RNG.nextFloat() - 0.5f) * 1.4f * shakeStrength;
    }

    /** FOV 偏移(度,负值=命中瞬间视野轻微拉近的顿挫感);0=无。 */
    public static double fovOffset() { return -fovPunch; }

    /** 击杀闪光当前 alpha(0=不画)。 */
    public static int flashAlpha() {
        if (flashTicks <= 0) return 0;
        return (int) (0x34 * (flashTicks / (float) FLASH_MAX_TICKS));
    }
}
