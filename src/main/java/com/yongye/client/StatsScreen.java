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
                Text.literal("◆ 成长面板 ◆").formatted(Formatting.GOLD), cx, 14, 0xFFFFD700);

        // 双栏布局(m209):旧版单栏 17 行在 GUI 缩放 4 / 小窗口下会压住「返回」按钮并顶出屏幕。
        // 左栏=当前属性,右栏=成长(技能书);栏心偏移随窗口宽度收缩,最小 96 保证不互相重叠。
        int off = Math.max(96, Math.min(120, this.width / 4));
        int lx = cx - off, rxc = cx + off;
        int lh = 15;

        // ===== 左栏:当前属性(所有职业通用) =====
        int y = 36;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 当前属性 ◆").formatted(Formatting.AQUA), lx, y, 0xFF55FFFF);
        y += lh + 2;
        ClientPlayerEntity pl = MinecraftClient.getInstance().player;
        if (pl != null) {
            // 攻击伤害用服务端同步值:GENERIC_ATTACK_DAMAGE 原版不下发客户端,本地读永远是 1(攻速是 tracked 的所以正常)。
            double atk = ClientStats.attackDamage >= 0 ? ClientStats.attackDamage
                    : pl.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            String[] attrs = {
                    "生命 " + big(pl.getHealth()) + " / " + big(pl.getMaxHealth()),
                    "攻击伤害 " + big(atk),
                    "攻击速度 " + fmt(pl.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED)),
                    "护甲 " + big(pl.getAttributeValue(EntityAttributes.GENERIC_ARMOR)),
                    "韧性 " + big(pl.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS)),
                    "移动速度 " + fmt(pl.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)),
                    "击退抗性 " + fmt(pl.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)),
                    "幸运 " + fmt(pl.getAttributeValue(EntityAttributes.GENERIC_LUCK)),
            };
            for (String s : attrs) {
                ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(s), lx, y, 0xFFFFFFFF);
                y += lh;
            }
        } else {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("(读取中…)").formatted(Formatting.GRAY), lx, y, 0xFF888888);
        }

        // ===== 右栏:成长(技能书等级与加成) =====
        y = 36;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 成长(技能书)◆").formatted(Formatting.GOLD), rxc, y, 0xFFFFD700);
        y += lh + 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("血量强化 V" + ClientStats.health + " (+" + (ClientStats.health * 10L) + " 最大生命)"),
                rxc, y, 0xFFFF5555);
        y += lh;

        String[] descs = {
                " 攻击伤害 +" + fmt(level(0) * 0.5),
                " 护甲 +" + fmt(level(1) * 0.5) + "/韧性 +" + fmt(level(1) * 0.25),
                " 每秒回血 " + fmt(level(2) * 0.1),
                " 闪避 " + (int) Math.round(Math.min(0.5, level(3) * 0.01) * 100) + "%",
                " 反伤 ×" + fmt(Math.min(3.0, level(4) * 0.05)),
                " 清负面 " + (int) Math.round(Math.min(0.8, level(5) * 0.01) * 100) + "% + 抗火",
                " 持续回饱食度",
                " 抢夺概率 " + (int) Math.round(Math.min(0.9, level(7) * 0.005) * 100) + "%"
        };

        for (int i = 0; i < NAMES.length; i++) {
            int lv = level(i);
            int color = lv > 0 ? 0xFF55FFFF : 0xFF888888;
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(NAMES[i] + " V" + lv + (lv > 0 ? descs[i] : "")), rxc, y, color);
            y += lh;
        }
    }

    /** 大数紧凑显示:≥1亿→X.X亿,≥1万→X.X万,否则原样(后期攻击/生命可达十亿级)。 */
    private static String big(double v) {
        return NumFmt.compact(v);   // m219:统一 K/M/B/T(原万/亿)
    }

    private static int level(int i) {
        return i < ClientStats.levels.length ? ClientStats.levels[i] : 0;
    }

    private static String fmt(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format("%.2f", v);
    }
}
