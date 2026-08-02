package com.yongye.client;

import com.yongye.screen.EnhanceScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 武器强化界面:装备槽 + 材料槽 + 「升级」按钮 + 预览文本。
 * 放装备 + 一组强化材料 → 点升级,按 材料数量×单值 加等级(生命碎片 N 个 = +N 级)。
 * 无贴图,纯填充背景(缺图也不影响)。写法照 AccessoryScreen。
 */
public class EnhanceScreen extends HandledScreen<EnhanceScreenHandler> {

    // ===== m409 强化结果演出状态(收 EnhanceFxPayload 置位;纯时间驱动到点自灭,关屏不残留)=====
    private static long fxStart = 0;
    private static int fxFrom, fxTo, fxSucceeded, fxFailed;
    private static boolean fxBroke, fxProtect;
    private static final long ROLL_MS = 900, FX_LIFE_MS = 1600, BREAK_FLASH_MS = 600;

    /** 收包入口(YongyeClient 转发,主线程)。碎裂顺手踢一脚镜头(震屏加强,复用打击感通道)。 */
    public static void onEnhanceFx(int from, int to, int succeeded, int failed, boolean broke, boolean protect) {
        if (!com.yongye.YongyeConfig.get().enableEnhanceFx) return;
        fxStart = System.nanoTime();
        fxFrom = from; fxTo = to; fxSucceeded = succeeded; fxFailed = failed;
        fxBroke = broke; fxProtect = protect;
        if (broke) CombatFxManager.onFx(1, 1.5f, 0f, false, false, 0);   // 震屏加强(m275 通道,客户端本地)
    }

    public EnhanceScreen(EnhanceScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init(); // HandledScreen.init 在此设置 this.x / this.y(背景左上角)
        this.titleX = 8;
        this.titleY = 6;
        this.playerInventoryTitleX = 8;
        this.playerInventoryTitleY = 72;
        // 「升级」按钮(材料槽右侧)
        addDrawableChild(ButtonWidget.builder(Text.literal("升级"),
                        b -> ClientPlayNetworking.send(new com.yongye.network.EnhanceApplyPayload()))
                .dimensions(this.x + 118, this.y + 33, 40, 20).build());
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;
        // 纯填充背景 + 槽位描边(灰底,缺贴图也清晰)
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFFC6C6C6);
        for (Slot s : this.handler.slots) {
            ctx.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, 0xFF8B8B8B);
            ctx.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, 0xFF373737);
        }
        // 装备槽 与 材料槽 之间的「+」提示
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("+"), x + 78, y + 40, 0xFF404040);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        // 预览:当前等级 → 升级后的等级(本次 +N)
        int cur = this.handler.currentLevel();
        int add = this.handler.previewLevels();
        Text preview = add > 0
                ? Text.literal("强化 +" + cur + " → +" + (cur + add) + "  (本次 +" + add + ")").formatted(Formatting.AQUA)
                : Text.literal("放入装备 + 强化材料,点「升级」").formatted(Formatting.GRAY);
        ctx.drawCenteredTextWithShadow(this.textRenderer, preview,
                this.x + backgroundWidth / 2, this.y + 60, add > 0 ? 0xFF55FFFF : 0xFFAAAAAA);
        renderEnhanceFx(ctx);   // m409 结果演出叠最上层
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    /** m409 结果演出:成功=Lv 数字滚动+金色粒子柱;碎裂=红闪(reduceScreenFlash 减半)+「碎 裂」+震屏。 */
    private void renderEnhanceFx(DrawContext ctx) {
        if (fxStart == 0) return;
        long age = (System.nanoTime() - fxStart) / 1_000_000L;
        if (age >= FX_LIFE_MS || !com.yongye.YongyeConfig.get().enableEnhanceFx) { fxStart = 0; return; }
        int cx = this.x + backgroundWidth / 2;
        var cfg = com.yongye.YongyeConfig.get();

        if (fxBroke) {
            // 红闪:整屏,600ms 淡尽;弱闪光=峰值减半
            if (age < BREAK_FLASH_MS) {
                int peak = (int) (0x90 * FxBudget.flashScale());   // m417 闪光中枢
                int a = (int) (peak * (1.0 - age / (double) BREAK_FLASH_MS));
                if (a > 3) ctx.fill(0, 0, this.width, this.height, (a << 24) | 0xC01212);
            }
            float t = Math.min(1f, age / 300f);
            int ta = Math.max(8, (int) (255 * (age > FX_LIFE_MS - 400 ? (FX_LIFE_MS - age) / 400f : 1f)));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, this.y - 26, 0);
            float sc = 2.2f + 0.8f * (1f - t) * (1f - t);           // 砸下来:2.2 收尾,起手更大
            ctx.getMatrices().scale(sc, sc, 1f);
            Text broke = Text.literal("碎 裂").formatted(Formatting.BOLD);
            ctx.drawText(this.textRenderer, broke, -this.textRenderer.getWidth(broke) / 2, -4,
                    (ta << 24) | 0xD42B3A, true);
            ctx.getMatrices().pop();
            if (fxProtect) { /* 不可达:碎裂与保护互斥,保护走成功分支 */ }
            return;
        }

        // —— 成功/部分成功 —— //
        // 等级数字滚动:900ms ease-out 从 from 滚到 to
        double rt = Math.min(1.0, age / (double) ROLL_MS);
        rt = 1.0 - Math.pow(1.0 - rt, 3);
        int shown = fxFrom + (int) Math.round((fxTo - fxFrom) * rt);
        int ta = Math.max(8, (int) (255 * (age > FX_LIFE_MS - 400 ? (FX_LIFE_MS - age) / 400f : 1f)));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, this.y - 24, 0);
        float pop = age < 140 ? 1.4f + (age / 140f) * 0.6f : 2.0f;   // 弹出 1.4→2.0
        ctx.getMatrices().scale(pop, pop, 1f);
        Text lv = Text.literal("Lv." + shown).formatted(Formatting.BOLD);
        ctx.drawText(this.textRenderer, lv, -this.textRenderer.getWidth(lv) / 2, -4,
                (ta << 24) | 0xFFC63C, true);
        ctx.getMatrices().pop();
        if (fxFailed > 0 || fxProtect) {
            String sub = "成功 " + fxSucceeded + " / 失败 " + fxFailed + (fxProtect ? "  ✔保护卷抵挡碎裂" : "");
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(sub), cx, this.y - 8,
                    (ta << 24) | 0xE8D8A0);
        }
        // 金色粒子柱:14 颗程序化火花在面板中轴 ±30px 内上升淡出(无状态:序号做种,逐帧重算)
        for (int i = 0; i < 14; i++) {
            long seed = i * 7919L + 13;
            double phase = ((age + (seed % 500)) % 1100) / 1100.0;   // 各自错相循环上升
            if (age > FX_LIFE_MS - 400 && phase > 0.7) continue;      // 收尾别再冒新头
            int px = cx + (int) ((seed * 31 % 61) - 30);
            int py = (int) (this.y + 70 - phase * 96);
            int pa = (int) (200 * (1.0 - phase) * (ta / 255f));
            if (pa <= 3) continue;
            int sz = 1 + (int) (seed % 2);
            ctx.fill(px, py, px + sz, py + sz, (pa << 24) | (phase < 0.5 ? 0xFFD75A : 0xFFB428));
        }
    }
}
