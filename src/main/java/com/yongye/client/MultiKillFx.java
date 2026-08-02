package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * 击杀连锁演出(m382,3A 打磨路线图第 7 项):短时间连续击杀弹中屏大字——
 * 双杀/三杀/四连杀/五连绝灭/七连屠戮/十连灭世(其后每 +5 报「N 连灭世」),
 * 弹出缩放冲击 + 升调经验音,颜色沿用连击十档色表(YongyeClient.comboColor,
 * 十档以上自动吃到彩虹流转)。
 *
 * <p><b>零新网络零新计数源</b>:击杀信号直接复用 CombatFxPayload 的 KILL 包
 * (服务端 m239 起本来就每杀必发给击杀者),客户端本地滚动窗口计链:
 * 距上一杀 ≤3s 链 +1,超时归 1。与连击(命中链,ComboHandler)是两条链互不相扰:
 * 连击看"打了多少下",本项看"杀了多少只"。
 *
 * <p>演出:同屏最多 1 条(新档替换旧档),1.3s=前 120ms 缩放 2.0→1.5 冲击落位 →
 * 持稳 → 末 300ms 淡出;非播报档位(6/8/9/11…)链照常累计但不弹字防 AOE 清怪刷屏。
 * 纯 nanoTime 时间驱动到点必消(DoD 第 3 条);坐标全出自 scaled 尺寸(GUI 缩放安全);
 * 受 enableMultiKillFx 与 FxBudget.on() 双门。零新 API 面。
 */
public final class MultiKillFx {
    private MultiKillFx() {}

    /** 链窗口:距上一杀超过此毫秒数则链归 1。 */
    private static final long WINDOW_MS = 3000;
    private static final long SHOW_MS = 1300, POP_MS = 120, FADE_MS = 300;

    private static int chain = 0;
    private static long lastKillNanos = 0;

    // 当前弹字(同屏 1 条,新档替换)
    private static boolean showing = false;
    private static long bornNanos = 0;
    private static String label = "";
    private static int colorTier = 0;

    /** CombatFxPayload KILL 包接收处调(主线程,每次击杀一次)。 */
    public static void onKill() {
        long now = System.nanoTime();
        chain = (lastKillNanos != 0 && (now - lastKillNanos) / 1_000_000L <= WINDOW_MS)
                ? chain + 1 : 1;
        lastKillNanos = now;
        if (chain < 2) return;
        if (!YongyeConfig.get().enableMultiKillFx || !FxBudget.on()) return;

        // 播报档位:2/3/4/5/7/10,10 后每 +5;其余档链照涨但不弹(防 AOE 刷屏)
        String name; int tier;
        switch (chain) {
            case 2  -> { name = "双杀";     tier = 1; }
            case 3  -> { name = "三杀";     tier = 2; }
            case 4  -> { name = "四连杀";   tier = 3; }
            case 5  -> { name = "五连绝灭"; tier = 4; }
            case 7  -> { name = "七连屠戮"; tier = 7; }
            case 10 -> { name = "十连灭世"; tier = 10; }
            default -> {
                if (chain > 10 && chain % 5 == 0) { name = chain + " 连灭世"; tier = 10; }
                else return;
            }
        }
        showing = true;
        bornNanos = now;
        label = "◆ " + name + " ◆";
        colorTier = tier;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            // 升调经验音:档位越高音越高(m279 升调音同款声源)
            SoundGate.duckPulse();   // m419 多杀播报压环境
            float pitch = Math.min(2.0f, 0.9f + tier * 0.11f);
            mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, pitch);
        }
    }

    /** 客户端初始化时挂 HUD 渲染(YongyeClient 调)。 */
    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (!showing) return;
            long ageMs = (System.nanoTime() - bornNanos) / 1_000_000L;
            if (ageMs >= SHOW_MS) { showing = false; return; }   // 到点必消
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;

            // 缩放冲击:2.0 → 1.5 落位;末段淡出
            float scale;
            if (ageMs < POP_MS) scale = 2.0f - (ageMs / (float) POP_MS) * 0.5f;
            else scale = 1.5f;
            int a = 255;
            if (ageMs > SHOW_MS - FADE_MS) a = (int) (255 * (SHOW_MS - ageMs) / (float) FADE_MS);
            a = Math.max(8, Math.min(255, a));

            int col = (a << 24) | (YongyeClient.comboColor(colorTier) & 0xFFFFFF);
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, h / 2f - 58, 0);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(label), 0, -4, col);
            ctx.getMatrices().pop();
        });
    }
}
