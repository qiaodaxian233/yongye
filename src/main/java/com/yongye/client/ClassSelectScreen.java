package com.yongye.client;

import com.yongye.item.PlayerClass;
import com.yongye.network.ChooseClassPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 开局选职界面:六职业各一按钮,点击即提交本命职业并关闭。
 * 强制选择(屏蔽 ESC),保证每个新玩家都先定本命职业。
 */
public class ClassSelectScreen extends Screen {

    public ClassSelectScreen() {
        super(Text.literal("选择本命职业"));
    }

    @Override
    protected void init() {
        PlayerClass[] cs = PlayerClass.values();
        int cols = 2, bw = 200, bh = 28, gapX = 16, gapY = 10;
        int totalW = cols * bw + (cols - 1) * gapX;
        int startX = this.width / 2 - totalW / 2;
        int startY = this.height / 2 - 64;
        for (int i = 0; i < cs.length; i++) {
            PlayerClass c = cs[i];
            int col = i % cols, row = i / cols;
            int x = startX + col * (bw + gapX);
            int y = startY + row * (bh + gapY);
            addDrawableChild(ButtonWidget.builder(
                            Text.literal(c.cn + " · " + tagOf(c)),
                            b -> choose(c))
                    .dimensions(x, y, bw, bh).build());
        }
    }

    private void choose(PlayerClass c) {
        ClientPlayNetworking.send(new ChooseClassPayload(c.id));
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 选择你的本命职业 ◆").formatted(Formatting.GOLD), cx, this.height / 2 - 96, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("出生即生效、不可更改;第二职业日后用职业书习得").formatted(Formatting.GRAY),
                cx, this.height / 2 - 84, 0xFFAAAAAA);
    }

    private static String tagOf(PlayerClass c) {
        return switch (c) {
            case WARRIOR -> "稳定近战";
            case TANK -> "据点防守";
            case ASSASSIN -> "夜间爆发";
            case WARLOCK -> "高风险法系";
            case MONK -> "空手流";
            case SWORDSMAN -> "武器技能流";
        };
    }
}
