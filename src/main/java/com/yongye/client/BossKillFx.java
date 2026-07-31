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
 */
public final class BossKillFx {
    private BossKillFx() {}

    private static final long SHOW_MS = 2200, FLASH_MS = 400, POP_MS = 140, FADE_MS = 350;

    private static boolean showing = false;
    private static long bornNanos = 0;
    private static String bossName = "";

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
            long ageMs = (System.nanoTime() - bornNanos) / 1_000_000L;
            if (ageMs >= SHOW_MS) { showing = false; return; }   // 到点必消
            YongyeConfig c = YongyeConfig.get();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();

            // 金色闪光:前 400ms 由峰值渐出(弱闪光减半)
            if (ageMs < FLASH_MS) {
                int peak = c.reduceScreenFlash ? 45 : 90;
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
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(bossName),
                        w / 2, h / 2 - 44, (a << 24) | 0xF0EADC);
            }
        });
    }
}
