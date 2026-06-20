package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.screen.AccessoryScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** 饰品栏界面(无贴图,纯填充背景)。 */
public class AccessoryScreen extends HandledScreen<AccessoryScreenHandler> {
    public AccessoryScreen(AccessoryScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 158;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = 8;
        this.titleY = 6;
        this.playerInventoryTitleX = 8;
        this.playerInventoryTitleY = 60;
    }

    private static final Identifier BG = Identifier.of(Yongye.MOD_ID, "textures/gui/accessory_gui.png");

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;
        // 自定义背景图(176x158);缺失时不影响——槽位描边照画
        ctx.drawTexture(BG, x, y, 0, 0, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
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
