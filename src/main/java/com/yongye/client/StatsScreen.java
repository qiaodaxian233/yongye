package com.yongye.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 成长面板:展示玩家已学技能等级与对应效果。数据来自 ClientStats(服务端同步)。
 */
public class StatsScreen extends Screen {

    private final Screen parent;

    // 顺序须与 SkillType.values() 一致:攻击/护甲/恢复/闪避/反伤/抗性
    private static final String[] NAMES = {"攻击强化", "护甲强化", "生命恢复", "闪避强化", "反伤强化", "抗性强化"};

    public StatsScreen(Screen parent) {
        super(Text.literal("成长面板"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close())
                .dimensions(this.width / 2 - 50, this.height - 36, 100, 20).build());
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 成长面板 ◆").formatted(Formatting.GOLD), cx, 26, 0xFFFFD700);

        int y = 52;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("血量强化   V" + ClientStats.health + "    (+" + (ClientStats.health * 10L) + " 最大生命)"),
                cx, y, 0xFFFF5555);
        y += 18;

        String[] descs = {
                "  攻击伤害 +" + fmt(level(0) * 0.5),
                "  护甲 +" + fmt(level(1) * 0.5) + " / 韧性 +" + fmt(level(1) * 0.25),
                "  每秒回血 " + fmt(level(2) * 0.1),
                "  闪避 " + (int) Math.round(Math.min(0.5, level(3) * 0.01) * 100) + "%",
                "  反伤 ×" + fmt(Math.min(3.0, level(4) * 0.05)),
                "  清负面 " + (int) Math.round(Math.min(0.8, level(5) * 0.01) * 100) + "% + 抗火"
        };

        for (int i = 0; i < NAMES.length; i++) {
            int lv = level(i);
            int color = lv > 0 ? 0xFF55FFFF : 0xFF888888;
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(NAMES[i] + "   V" + lv + (lv > 0 ? descs[i] : "")), cx, y, color);
            y += 18;
        }
    }

    private static int level(int i) {
        return i < ClientStats.levels.length ? ClientStats.levels[i] : 0;
    }

    private static String fmt(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format("%.2f", v);
    }
}
