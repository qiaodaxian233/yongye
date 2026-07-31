package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * 死亡/重生转场(m384,3A 打磨路线图第 11 项):
 * <ul>
 *   <li>死亡瞬间:600ms 黑幕渐入,死亡期间维持一层暗纱(画在 HUD 层,
 *       在死亡界面按钮之下,不挡「重生/返回标题」任何交互);</li>
 *   <li>重生瞬间:1s 黑幕渐出,渐出中屏幕中央淡入淡出一行状况提示
 *       「第 N 天 · <永夜阶段名>」——帮玩家一睁眼就重建状况感知。</li>
 * </ul>
 *
 * <p>实现口径(全 DoD):纯客户端零网络——每 tick 跟踪本地玩家存活状态,
 * 活→死=起渐入,死→活或玩家实体引用更换(重生必换实体)=起渐出;
 * 世界引用变化(重登/换维度/退主菜单)整体复位防跨世界残留;
 * 演出纯 nanoTime 驱动到点必消;坐标全 scaled 尺寸;
 * reduceScreenFlash=暗纱浓度上限下调;enableDeathTransition 与 FxBudget.on() 双门。
 * 死亡机制/掉落/重生逻辑零改动(防重复列口径)。
 */
public final class DeathTransitionFx {
    private DeathTransitionFx() {}

    private static final long FADE_IN_MS = 600, FADE_OUT_MS = 1000;
    private static final long TEXT_IN_AT = 150, TEXT_OUT_AT = 820;

    private static Object lastWorldRef = null;
    private static Object lastPlayerRef = null;
    private static boolean wasAlive = true;

    /** 阶段:0=无 1=死亡渐入/暗纱持稳 2=重生渐出。 */
    private static int phase = 0;
    private static long phaseNanos = 0;
    private static String reviveLine = "";

    /** 客户端初始化时挂(YongyeClient 调)。 */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world != lastWorldRef) {          // 重登/换维度/退主菜单:整体复位
                lastWorldRef = mc.world;
                lastPlayerRef = mc.player;
                wasAlive = true;
                phase = 0;
                return;
            }
            var p = mc.player;
            if (p == null) return;
            boolean alive = p.isAlive();
            boolean playerSwapped = p != lastPlayerRef;
            lastPlayerRef = p;

            if (wasAlive && !alive) {                // 活→死:渐入
                phase = 1;
                phaseNanos = System.nanoTime();
            } else if (!wasAlive && (alive || playerSwapped)) {   // 死→活/换实体:渐出
                phase = 2;
                phaseNanos = System.nanoTime();
                long day = com.yongye.system.ProgressionManager.gameDay(mc.world) + 1; // 第 1 天起算(m288 口径)
                String stage = YongyeClient.nightfallName == null || YongyeClient.nightfallName.isEmpty()
                        ? "昼夜正常" : YongyeClient.nightfallName;
                reviveLine = "第 " + day + " 天 · " + stage;
            }
            wasAlive = alive;
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (phase == 0) return;
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableDeathTransition || !FxBudget.on()) { phase = 0; return; }
            long ageMs = (System.nanoTime() - phaseNanos) / 1_000_000L;
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();

            if (phase == 1) {
                // 渐入 600ms 到暗纱,死亡期间持稳(退出条件由 tick 状态机切 phase,不在此驻留判断)
                int cap = c.reduceScreenFlash ? 110 : 160;               // 暗纱浓度上限(弱闪光下调)
                float t = Math.min(1f, ageMs / (float) FADE_IN_MS);
                int a = (int) (cap * (1f - (1f - t) * (1f - t)));
                if (a >= 8) ctx.fill(0, 0, w, h, (a << 24));             // 纯黑
                return;
            }

            // phase == 2:重生渐出 + 状况提示
            if (ageMs >= FADE_OUT_MS) { phase = 0; return; }             // 到点必消
            float t = ageMs / (float) FADE_OUT_MS;
            int start = c.reduceScreenFlash ? 150 : 210;
            int a = (int) (start * (1f - t) * (1f - t));                 // ease-in 渐出(前段深后段快散)
            if (a >= 8) ctx.fill(0, 0, w, h, (a << 24));

            // 提示行:150ms 淡入,820ms 起淡出
            float tA;
            if (ageMs < TEXT_IN_AT) tA = ageMs / (float) TEXT_IN_AT;
            else if (ageMs > TEXT_OUT_AT) tA = (FADE_OUT_MS - ageMs) / (float) (FADE_OUT_MS - TEXT_OUT_AT);
            else tA = 1f;
            int textA = Math.max(8, Math.min(255, (int) (255 * tA)));
            MinecraftClient mc = MinecraftClient.getInstance();
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(reviveLine),
                    w / 2, h / 2 - 30, (textA << 24) | 0xD8C9A6);        // 暖灰金,不喧宾
        });
    }
}
