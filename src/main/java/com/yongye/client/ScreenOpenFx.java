package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/**
 * 界面开场淡入底座(m375,3A 打磨路线图第 3 项):所有本模组界面(类在 com.yongye 包下)
 * 打开瞬间盖一层由暗到透的整屏罩(150ms ease-out),配合 YongyeButton 自带的入场上浮,
 * 界面从"啪一下糊脸上"变成"浮现出来"。一处全局 AFTER_INIT 接线,零逐界面改动。
 *
 * <p>实现:AFTER_INIT 里判屏幕类包名 → 是本模组界面则记开屏时刻,并给<b>该屏幕实例</b>
 * 注册 afterRender 回调画淡出罩(Fabric screen API v1 的实例级事件,回调随屏幕关闭自动失效,
 * 不会泄漏);罩色用界面主题深蓝黑,峰值 55% 不至于黑一闪。原版界面(背包本体等)不碰。
 * 关 enableUiFx = 不注册回调,完全回旧观感。
 *
 * <p>待编译验证(低险):ScreenEvents.afterRender(screen) 实例级事件与其回调签名
 * (screen, drawContext, mouseX, mouseY, tickDelta)——与在树 AFTER_INIT 同类
 * (net.fabricmc.fabric.api.client.screen.v1.ScreenEvents),官方 screen API v1 自 1.16 稳定;
 * 若报错删 register() 里 afterRender 段即可,只损失界面淡入(按钮动效不受影响)。
 */
public final class ScreenOpenFx {
    private ScreenOpenFx() {}

    private static final long FADE_MS = 150;
    /** 罩峰值 alpha(0~255,55%≈140;太高会"黑一闪",太低看不出)。 */
    private static final int PEAK_A = 140;

    /** 客户端初始化时挂(YongyeClient 调)。 */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!YongyeConfig.get().enableUiFx || !FxBudget.on()) return; // m381 OFF 档让位
            if (screen == null || !screen.getClass().getName().startsWith("com.yongye.")) return;
            long opened = System.nanoTime();
            ScreenEvents.afterRender(screen).register((scr, ctx, mouseX, mouseY, tickDelta) -> {
                long age = (System.nanoTime() - opened) / 1_000_000L;
                if (age >= FADE_MS) return;
                float t = age / (float) FADE_MS;
                float ease = 1f - (1f - t) * (1f - t);          // ease-out
                int a = (int) (PEAK_A * (1f - ease));
                if (a < 8) return;
                ctx.fill(0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(),
                        (a << 24) | 0x0A1220);                   // 主题深蓝黑罩
            });
        });
    }
}
