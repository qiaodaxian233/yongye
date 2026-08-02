package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * BOSS 讨伐终结演出客户端(m387):金色闪光(400ms,受 reduceScreenFlash 减半)+
 * 「◆ 讨伐成功 ◆」金色大字(缩放冲击 2.4→1.8 落位)+ BOSS 名白色副标 + 凯旋升级音,共 2.2s。
 * 顿帧/震屏由服务端另发的 CombatFxPayload 加强档承担(HEAVY 档避免 KILL 档被 MultiKillFx
 * 重复计链);演出纯 nanoTime 驱动到点必消,同屏 1 个新来替换;
 * enableBossKillFx 与 FxBudget.on() 双门。零新 API 面。
 *
 * <p>m389 评审修补:①双门从「只在收包时查」升级为渲染入口每帧复核——演出进行中关开关或
 * 把 fxQuality 调 0,字幕/金闪立即消失(路线图「关掉开关=零残留」口径);②BOSS 名副标
 * trimToWidth 裁宽,超长自定义名不出屏。
 */
public final class BossKillFx {
    private BossKillFx() {}

    private static final long SHOW_MS = 2200, FLASH_MS = 400, POP_MS = 140, FADE_MS = 350;

    private static boolean showing = false;
    private static long bornNanos = 0;
    private static String bossName = "";

    /** m388 音景避让探针:讨伐演出是否进行中。 */
    static boolean isShowing() { return showing; }

    /** 收包入口(主线程)。 */
    public static void onBossKill(String name) {
        if (!YongyeConfig.get().enableBossKillFx || !FxBudget.on()) return;
        showing = true;
        bornNanos = System.nanoTime();
        bossName = name == null ? "" : name;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 0.85f); // 凯旋(在树)
        }
    }

    /** 客户端初始化时挂(YongyeClient 调)。 */
    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (!showing) return;
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableBossKillFx || !FxBudget.on()) { showing = false; return; } // m389:运行中关开关/降 OFF 立即消
            long ageMs = (System.nanoTime() - bornNanos) / 1_000_000L;
            if (ageMs >= SHOW_MS) { showing = false; return; }   // 到点必消
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();

            // 金色闪光:前 400ms 由峰值渐出(弱闪光减半)
            if (ageMs < FLASH_MS) {
                int peak = (int) (90 * FxBudget.flashScale());     // m417 闪光中枢
                int a = (int) (peak * (1f - ageMs / (float) FLASH_MS));
                if (a >= 8) ctx.fill(0, 0, w, h, (a << 24) | 0xFFD873);
            }

            // 字幕:缩放冲击落位 + 末段淡出
            float scale = ageMs < POP_MS ? 2.4f - (ageMs / (float) POP_MS) * 0.6f : 1.8f;
            int a = 255;
            if (ageMs > SHOW_MS - FADE_MS) a = (int) (255 * (SHOW_MS - ageMs) / (float) FADE_MS);
            a = Math.max(8, Math.min(255, a));

            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, h / 2f - 70, 0);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("◆ 讨伐成功 ◆"),
                    0, -4, (a << 24) | 0xFFC332);
            ctx.getMatrices().pop();
            if (!bossName.isEmpty()) {
                String sub = mc.textRenderer.trimToWidth(bossName, w - 40); // m389:超长自定义名裁宽防出屏
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(sub),
                        w / 2, h / 2 - 44, (a << 24) | 0xF0EADC);
            }
        });
    }
}
