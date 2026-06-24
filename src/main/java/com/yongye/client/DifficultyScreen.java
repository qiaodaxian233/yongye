package com.yongye.client;

import com.yongye.item.GameDifficulty;
import com.yongye.network.ChooseDifficultyPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 开局难度选择界面:顶部「永夜」简介,下面 7 档难度(游玩→永夜)逐行可点,点选即提交并关闭。
 * 强制选择(屏蔽 ESC),与旧的「选职弹窗」同为登录首个界面——现在先选难度,职业之后用「职业选择书」自选。
 */
public class DifficultyScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int ROW_H = 26;
    private final GameDifficulty[] diffs = GameDifficulty.values();

    // 游戏简介(逐行,绘制在难度列表上方)
    private static final String[] INTRO = {
            "太阳不再升起。黑暗渗入万物的骨血,怪物日益强大,而它尤其憎恨强者——",
            "会缝死你的伤口(禁疗)、当面夺走你的装备(缴械)、把躲藏者从水里、塔顶、",
            "墙后揪出来。你将选择本命职业、强化武器、对抗永夜灾变,看自己能撑多久。",
            "永夜会越来越深,可一直加深;先选择这一局的难度——它决定怪物随你成长的凶猛程度。"
    };

    public DifficultyScreen() {
        super(Text.literal("选择难度"));
    }

    private int listTop() {
        // 标题 + 简介占顶部,难度列表整体居中偏下
        return this.height / 2 - (diffs.length * ROW_H) / 2 + 28;
    }

    private int rowX() { return this.width / 2 - PANEL_W / 2; }
    private int rowY(int i) { return listTop() + i * ROW_H; }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = rowX();
            for (int i = 0; i < diffs.length; i++) {
                int y = rowY(i);
                if (mouseX >= x && mouseX <= x + PANEL_W && mouseY >= y && mouseY <= y + ROW_H - 2) {
                    ClientPlayNetworking.send(new ChooseDifficultyPayload(i));
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

        // 标题
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 永夜 · 选择难度 ◆").formatted(Formatting.DARK_PURPLE),
                cx, listTop() - 70, 0xFFB070FF);

        // 简介
        int iy = listTop() - 56;
        for (String line : INTRO) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(line).formatted(Formatting.GRAY), cx, iy, 0xFFAAAAAA);
            iy += 10;
        }

        // 难度行
        int x = rowX();
        for (int i = 0; i < diffs.length; i++) {
            int y = rowY(i);
            GameDifficulty d = diffs[i];
            boolean hover = mouseX >= x && mouseX <= x + PANEL_W && mouseY >= y && mouseY <= y + ROW_H - 2;
            // 行底框 + 悬停高亮
            ctx.fill(x, y, x + PANEL_W, y + ROW_H - 2, hover ? 0x803A2A60 : 0x66150E28);
            if (hover) {
                ctx.fill(x, y, x + 2, y + ROW_H - 2, 0xFFFFD700);
            }
            // 难度名(着色)+ 简述
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("【" + d.cn + "】").formatted(d.color), x + 8, y + 3, 0xFFFFFFFF);
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal(d.desc).formatted(Formatting.GRAY), x + 64, y + 4, 0xFFAAAAAA);
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("怪物 ×" + trim(d.mobMult)).formatted(Formatting.DARK_GRAY), x + 8, y + 14, 0xFF888888);
        }

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("点击选择难度(本局生效,死亡保留)").formatted(Formatting.DARK_GRAY),
                cx, rowY(diffs.length) + 4, 0xFF888888);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private static String trim(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
