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
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
