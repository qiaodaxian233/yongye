package com.yongye.client;

import com.yongye.screen.AccessoryScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/** 饰品栏界面(无贴图,纯填充背景)。 */
public class AccessoryScreen extends HandledScreen<AccessoryScreenHandler> {
    public AccessoryScreen(AccessoryScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 133;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = 8;
        this.titleY = 6;
        this.playerInventoryTitleX = 8;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF2B2B2B);
        ctx.fill(x, y, x + backgroundWidth, y + 1, 0xFF555555);
        for (Slot s : this.handler.slots) {
            ctx.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, 0xFF8B8B8B);
            ctx.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, 0xFF373737);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
