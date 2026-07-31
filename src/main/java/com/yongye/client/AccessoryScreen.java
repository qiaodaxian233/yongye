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
        // m392:背景图按真实槽位程序化重绘(玻璃蓝面板+神器区/鞘翅区/背包区衬板+全部槽位凹槽
        // 已烘进 176×158 贴图,坐标与 AccessoryScreenHandler 一一对应)——撤掉旧的代码平灰槽位
        // 覆盖(旧写法把贴图槽位区全盖死,贴图形同虚设=占位没做的病根)。
        ctx.drawTexture(BG, x, y, 0, 0, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
        for (Slot s : this.handler.slots) {
            // 第 11 槽(鞘翅格,x=152/y=28)空置时保留「翼」占位提示(凹槽本体在贴图里,亮红提对比)
            if (s.x == 152 && s.y == 28 && !s.hasStack()) {
                ctx.drawText(this.textRenderer, net.minecraft.text.Text.literal("翼"),
                        x + s.x + 4, y + s.y + 4, 0xFFB35A5A, false);
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
