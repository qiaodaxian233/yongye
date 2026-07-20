package com.yongye.client;

import com.yongye.item.PlayerClass;
import com.yongye.network.ChooseClassPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 开局选职界面(m204 重做:程序化卡片版,替代旧 AI 卡图 PNG)。
 * 六张职业卡 3×2,卡面由 {@link ClassCardRenderer} 统一绘制(职业色描边 + 夜蚀渐变底 +
 * 2× 武器图标 + 定位语 + 三行特长);悬停发光并在底部展示一句话介绍;点选即提交本命职业并关闭。
 * 强制选择(屏蔽 ESC)。网格与点击判定沿用旧版,只换了画法。
 */
public class ClassSelectScreen extends Screen {

    private static final int CW = ClassCardRenderer.CW, CH = ClassCardRenderer.CH, GAP_X = 12, GAP_Y = 18, COLS = 3;
    private final PlayerClass[] classes = PlayerClass.values();

    public ClassSelectScreen() {
        super(Text.literal("选择本命职业"));
    }

    private int gridLeft() { return this.width / 2 - (COLS * CW + (COLS - 1) * GAP_X) / 2; }
    private int gridTop()  { return this.height / 2 - (2 * CH + GAP_Y) / 2 + 6; }
    private int cardX(int i) { return gridLeft() + (i % COLS) * (CW + GAP_X); }
    private int cardY(int i) { return gridTop() + (i / COLS) * (CH + GAP_Y); }

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
        // 再压一层夜色,突出卡片
        ctx.fill(0, 0, this.width, this.height, 0x46060810);

        int cx = this.width / 2;
        // 标题(1.4× 放大,金色)+ 副标(TitleScreenMixin 同款矩阵缩放)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, gridTop() - 34.0, 0.0);
        ctx.getMatrices().scale(1.4f, 1.4f, 1.0f);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 选择你的本命职业 ◆").formatted(Formatting.BOLD), 0, 0, 0xFFFFD700);
        ctx.getMatrices().pop();
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("出生即生效、不可更改;第二职业日后用职业书习得"),
                cx, gridTop() - 16, 0xFF9AA6B8);

        PlayerClass hovered = null;
        for (int i = 0; i < classes.length; i++) {
            int x = cardX(i), y = cardY(i);
            boolean hover = mouseX >= x && mouseX <= x + CW && mouseY >= y && mouseY <= y + CH;
            if (hover) hovered = classes[i];
            ClassCardRenderer.drawCard(ctx, this.textRenderer, classes[i], x, y, hover);
        }

        // 底部:悬停职业的一句话介绍(职业色)
        if (hovered != null) {
            int by = gridTop() + 2 * CH + GAP_Y + 10;
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("【" + hovered.cn + "】" + ClassCardRenderer.extendedDesc(hovered)),
                    cx, by, ClassCardRenderer.accent(hovered));
        }
        super.render(ctx, mouseX, mouseY, delta);
    }
}
