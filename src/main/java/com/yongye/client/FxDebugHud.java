package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * FX 调试面板(m411,路线图第 23 项):左下角小面板实时显示各类特效的
 * 存活数 / 每秒新增 / 每秒丢弃 与当前画质档。enableFxDebugHud 默认关,
 * `/yongye fxtest panel` 一键开合;调试工具不走美化,深底小字直给。
 */
public final class FxDebugHud {
    private FxDebugHud() {}

    private static final String[] Q = {"OFF", "LOW", "MEDIUM", "HIGH"};

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (!YongyeConfig.get().enableFxDebugHud) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            var tr = mc.textRenderer;
            int h = mc.getWindow().getScaledHeight();
            int overlays = (NightfallTransitionFx.isPlaying() ? 1 : 0)
                    + (BossKillFx.isShowing() ? 1 : 0)
                    + (UltimateCastFx.isActive() ? 1 : 0);
            String[] lines = {
                    "◆ FX 面板 · 画质 " + Q[FxBudget.quality()],
                    "飘字 活" + DamageNumberManager.liveCount()
                            + " +" + FxStats.perSecUsed(FxStats.NUM) + "/s 丢" + FxStats.perSecDropped(FxStats.NUM) + "/s",
                    "拾取卡 活" + PickupNoticeFx.liveCount()
                            + " +" + FxStats.perSecUsed(FxStats.CARD) + "/s 丢" + FxStats.perSecDropped(FxStats.CARD) + "/s",
                    "光柱 " + LootBeamManager.liveCount()
                            + "  血条 " + MobHealthBarManager.liveCount()
                            + "  叠层 " + overlays,
            };
            int y = h - 64 - FxBudget.safeY() - lines.length * 11;   // m418 吃安全边距
            int wMax = 0;
            for (String l : lines) wMax = Math.max(wMax, tr.getWidth(l));
            int lx = 2 + FxBudget.safeX();
            ctx.fill(lx, y - 3, lx + 6 + wMax, y + lines.length * 11, 0xA0101820);
            for (String l : lines) {
                ctx.drawTextWithShadow(tr, Text.literal(l), lx + 3, y, 0xFF9CD8FF);
                y += 11;
            }
        });
    }
}
