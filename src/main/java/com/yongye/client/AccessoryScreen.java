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
            // 第 11 槽(鞘翅格)固定在 x=152,y=28(见 ScreenHandler);用坐标识别
            boolean isWingSlot = (s.x == 152 && s.y == 28);
            int border = isWingSlot ? 0xFF8B0000 : 0xFF8B8B8B;  // 鞘翅格暗红边
            ctx.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, border);
            ctx.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, 0xFF373737);
            if (isWingSlot && !s.hasStack()) {
                ctx.drawText(this.textRenderer, net.minecraft.text.Text.literal("翼"),
                        x + s.x + 4, y + s.y + 4, 0xFF6B0000, false);
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
