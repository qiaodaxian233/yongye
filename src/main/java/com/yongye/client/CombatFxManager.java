package com.yongye.client;

import java.util.Random;

/**
 * 沉浸式战斗手感——客户端状态机(m239)。
 * 收 {@code CombatFxPayload} 置入强度,每客户端 tick 指数衰减;
 * 相机抖动由 {@code CameraShakeMixin} 每帧取偏移、FOV 顿挫由 {@code FovKickMixin} 取偏移、
 * 击杀闪光由 YongyeClient 的 HudRenderCallback 取 alpha。全部读静态字段,无锁无分配。
 */
public final class CombatFxManager {
    private CombatFxManager() {}

    private static float shakeStrength = 0f;  // 当前镜头抖动强度(度级)
    private static float fovPunch = 0f;       // 当前 FOV 顿挫(度,正值,应用时取负=瞬间拉近)
    private static int flashTicks = 0;        // 击杀闪光剩余 tick
    private static final int FLASH_MAX_TICKS = 7;
    private static final Random RNG = new Random();

    /** 收到服务端 FX 包(已在客户端主线程)。 */
    public static void onFx(int kind, float shake, float fov, boolean flash, boolean sound) {
        // 取 max 而不是叠加:连击时保持"最重那一下"的手感,不会震到失控
        shakeStrength = Math.min(2.5f, Math.max(shakeStrength, shake));
        fovPunch = Math.min(6f, Math.max(fovPunch, fov));
        if (flash) flashTicks = FLASH_MAX_TICKS;
        if (sound) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player != null)
                mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_ARROW_HIT_PLAYER, 0.55f, 1.6f);
        }
    }

    /** 每客户端 tick 衰减(YongyeClient 挂 END_CLIENT_TICK)。 */
    public static void tick() {
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
