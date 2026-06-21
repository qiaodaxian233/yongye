package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.item.PlayerClass;
import com.yongye.network.ChooseClassPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * 开局选职界面(卡图版):六张职业卡 3×2 排列,鼠标点选即提交本命职业并关闭。
 * 强制选择(屏蔽 ESC)。卡图用确认过的 DrawContext.drawTexture(id,x,y,u,v,w,h,texW,texH) 签名,原尺寸绘制(96×132)。
 */
public class ClassSelectScreen extends Screen {

    private static final int CW = 106, CH = 132, GAP_X = 12, GAP_Y = 18, COLS = 3;
    private final PlayerClass[] classes = PlayerClass.values();

    public ClassSelectScreen() {
        super(Text.literal("选择本命职业"));
    }

    private int gridLeft() { return this.width / 2 - (COLS * CW + (COLS - 1) * GAP_X) / 2; }
    private int gridTop()  { return this.height / 2 - (2 * CH + GAP_Y) / 2 + 6; }
    private int cardX(int i) { return gridLeft() + (i % COLS) * (CW + GAP_X); }
    private int cardY(int i) { return gridTop() + (i / COLS) * (CH + GAP_Y); }

    private static Identifier cardTex(PlayerClass c) {
        return Identifier.of(Yongye.MOD_ID, "textures/gui/class_card_" + c.id + ".png");
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < classes.length; i++) {
                int x = cardX(i), y = cardY(i);
                if (mouseX >= x && mouseX <= x + CW && mouseY >= y && mouseY <= y + CH) {
                    ClientPlayNetworking.send(new ChooseClassPayload(classes[i].id));
                    MinecraftClient.getInstance().setScreen(null);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u25c6 \u9009\u62e9\u4f60\u7684\u672c\u547d\u804c\u4e1a \u25c6").formatted(Formatting.GOLD),
                cx, gridTop() - 30, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u51fa\u751f\u5373\u751f\u6548\u3001\u4e0d\u53ef\u66f4\u6539\uff1b\u7b2c\u4e8c\u804c\u4e1a\u65e5\u540e\u7528\u804c\u4e1a\u4e66\u4e60\u5f97").formatted(Formatting.GRAY),
                cx, gridTop() - 18, 0xFFAAAAAA);
        for (int i = 0; i < classes.length; i++) {
            int x = cardX(i), y = cardY(i);
            boolean hover = mouseX >= x && mouseX <= x + CW && mouseY >= y && mouseY <= y + CH;
            if (hover) {
                int b = 0xFFFFD700;
                ctx.fill(x - 3, y - 3, x + CW + 3, y, b);
                ctx.fill(x - 3, y + CH, x + CW + 3, y + CH + 3, b);
                ctx.fill(x - 3, y, x, y + CH, b);
                ctx.fill(x + CW, y, x + CW + 3, y + CH, b);
            }
            ctx.drawTexture(cardTex(classes[i]), x, y, 0, 0, CW, CH, CW, CH);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }
}
