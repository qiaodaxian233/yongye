package com.yongye.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 成长面板:展示玩家已学技能等级与对应效果。数据来自 ClientStats(服务端同步)。
 */
public class StatsScreen extends Screen {

    private final Screen parent;

    // 顺序须与 SkillType.values() 一致:攻击/护甲/恢复/闪避/反伤/抗性
    private static final String[] NAMES = {"攻击强化", "护甲强化", "生命恢复", "闪避强化", "反伤强化", "抗性强化", "饱食强化", "抢夺强化"};

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
                Text.literal("◆ 成长面板 ◆").formatted(Formatting.GOLD), cx, 18, 0xFFFFD700);

        // 当前属性:所有职业通用。读本地玩家已同步的最终属性(含职业/携带/强化加成),武僧无武器也能看。
        int y = 38;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 当前属性 ◆").formatted(Formatting.AQUA), cx, y, 0xFF55FFFF);
        y += 16;
        ClientPlayerEntity pl = MinecraftClient.getInstance().player;
        if (pl != null) {
            String[] attrs = {
                    "生命 " + big(pl.getHealth()) + " / " + big(pl.getMaxHealth()),
                    "攻击伤害 " + big(pl.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE))
                            + "    攻击速度 " + fmt(pl.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED)),
                    "护甲 " + big(pl.getAttributeValue(EntityAttributes.GENERIC_ARMOR))
                            + "    韧性 " + big(pl.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS)),
                    "移动速度 " + fmt(pl.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED))
                            + "    击退抗性 " + fmt(pl.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)),
                    "幸运 " + fmt(pl.getAttributeValue(EntityAttributes.GENERIC_LUCK)),
            };
            for (String s : attrs) {
                ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(s), cx, y, 0xFFFFFFFF);
                y += 16;
            }
        } else {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("(读取中…)").formatted(Formatting.GRAY), cx, y, 0xFF888888);
            y += 16;
        }

        // 成长(技能书等级与加成)
        y += 8;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 成长(技能书)◆").formatted(Formatting.GOLD), cx, y, 0xFFFFD700);
        y += 18;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("血量强化   V" + ClientStats.health + "    (+" + (ClientStats.health * 10L) + " 最大生命)"),
                cx, y, 0xFFFF5555);
        y += 16;

        String[] descs = {
                "  攻击伤害 +" + fmt(level(0) * 0.5),
                "  护甲 +" + fmt(level(1) * 0.5) + " / 韧性 +" + fmt(level(1) * 0.25),
                "  每秒回血 " + fmt(level(2) * 0.1),
                "  闪避 " + (int) Math.round(Math.min(0.5, level(3) * 0.01) * 100) + "%",
                "  反伤 ×" + fmt(Math.min(3.0, level(4) * 0.05)),
                "  清负面 " + (int) Math.round(Math.min(0.8, level(5) * 0.01) * 100) + "% + 抗火",
                "  持续回饱食度(几乎不会饿)",
                "  抢夺概率 " + (int) Math.round(Math.min(0.9, level(7) * 0.005) * 100) + "%"
        };

        for (int i = 0; i < NAMES.length; i++) {
            int lv = level(i);
            int color = lv > 0 ? 0xFF55FFFF : 0xFF888888;
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(NAMES[i] + "   V" + lv + (lv > 0 ? descs[i] : "")), cx, y, color);
            y += 16;
        }
    }

    /** 大数紧凑显示:≥1亿→X.X亿,≥1万→X.X万,否则原样(后期攻击/生命可达十亿级)。 */
    private static String big(double v) {
        double a = Math.abs(v);
        if (a >= 1e8) return fmt(v / 1e8) + "亿";
        if (a >= 1e4) return fmt(v / 1e4) + "万";
        return fmt(v);
    }

    private static int level(int i) {
        return i < ClientStats.levels.length ? ClientStats.levels[i] : 0;
    }

    private static String fmt(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format("%.2f", v);
    }
}
