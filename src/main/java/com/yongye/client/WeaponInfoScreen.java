package com.yongye.client;

import com.yongye.YongyeConfig;
import com.yongye.item.WeaponQuality;
import com.yongye.item.WeaponSkill;
import com.yongye.registry.ModComponents;
import com.yongye.system.EquipmentEnhancer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 武器/装备介绍界面:展示手持装备的品质、基础属性、稀有度与传说语。
 * 数据全部来自物品自身组件(强化等级 + 默认/强化后属性),客户端本地读取。
 */
public class WeaponInfoScreen extends Screen {

    private final Screen parent;
    private final ItemStack stack;

    public WeaponInfoScreen(Screen parent, ItemStack stack) {
        super(Text.literal("装备介绍"));
        this.parent = parent;
        this.stack = stack;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(this.width / 2 - 50, this.height - 34, 100, 20).build());
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        int level = stack.getOrDefault(ModComponents.ENHANCE_LEVEL, 0);
        WeaponQuality q = WeaponQuality.forLevel(level);
        boolean weapon = EquipmentEnhancer.isWeapon(stack);
        YongyeConfig c = YongyeConfig.get();

        int panelW = 320, panelH = 244;
        int x0 = (this.width - panelW) / 2;
        int y0 = (this.height - panelH) / 2;

        // 外框
        ctx.fill(x0 - 2, y0 - 2, x0 + panelW + 2, y0 + panelH + 2, 0xFF2A2140);
        ctx.fill(x0, y0, x0 + panelW, y0 + panelH, 0xF00E0B1A);

        // 标题(品质着色)
        Text title = Text.literal("✦ " + q.cn + " · ").formatted(q.color)
                .append(stack.getName().copy().formatted(q.color));
        ctx.drawCenteredTextWithShadow(this.textRenderer, title, x0 + panelW / 2, y0 + 8, 0xFFFFFFFF);
        ctx.fill(x0 + 16, y0 + 22, x0 + panelW - 16, y0 + 23, 0x66FFFFFF);

        // 左侧:放大的物品图标
        int iconX = x0 + 18, iconY = y0 + 40;
        ctx.fill(iconX - 4, iconY - 4, iconX + 72, iconY + 72, 0x40FFFFFF);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(iconX, iconY, 0);
        ctx.getMatrices().scale(4.0f, 4.0f, 1.0f);
        ctx.drawItem(stack, 0, 0);
        ctx.getMatrices().pop();

        // 右侧:基础属性
        int rx = x0 + 110;
        int ry = y0 + 40;
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("基础属性").formatted(Formatting.YELLOW), rx, ry, 0xFFFFD700);
        ry += 16;
        if (weapon) {
            double atk = level * c.enhanceDamagePerLevel;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("攻击力  +" + fmt(atk)).formatted(Formatting.RED), rx, ry, 0xFFFF5555); ry += 14;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("攻击速度  +" + fmt(q.attackSpeed)).formatted(Formatting.GOLD), rx, ry, 0xFFFFAA00); ry += 14;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("暴击率  +" + (int) Math.round(q.critChance * 100) + "%").formatted(Formatting.GREEN), rx, ry, 0xFF55FF55); ry += 14;
        } else {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("护甲  +" + fmt(level * c.enhanceArmorPerLevel)).formatted(Formatting.AQUA), rx, ry, 0xFF55FFFF); ry += 14;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("韧性  +" + fmt(level * c.enhanceToughnessPerLevel)).formatted(Formatting.AQUA), rx, ry, 0xFF55FFFF); ry += 14;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("最大生命  +" + fmt(level * c.enhanceHealthPerLevel)).formatted(Formatting.RED), rx, ry, 0xFFFF5555); ry += 14;
        }
        if (stack.isDamageable()) {
            int max = stack.getMaxDamage();
            int cur = max - stack.getDamage();
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("耐久度  " + cur + " / " + max).formatted(Formatting.AQUA), rx, ry, 0xFF55FFFF); ry += 14;
        }

        // 品质 / 类型 / 稀有度 / 强化
        int qy = y0 + 120;
        ctx.fill(rx - 6, qy - 4, x0 + panelW - 14, qy + 62, 0x33FFFFFF);
        line(ctx, rx, qy, "品质", q.cn, q.color); qy += 14;
        line(ctx, rx, qy, "类型", weapon ? "武器" : "盔甲", Formatting.WHITE); qy += 14;
        line(ctx, rx, qy, "稀有度", q.grade, q.color); qy += 14;
        line(ctx, rx, qy, "强化", "+" + level, Formatting.AQUA); qy += 14;

        // 神器技能区(仅武器)
        if (weapon) {
            int sy = y0 + 168;
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("✦ 神器技能").formatted(Formatting.LIGHT_PURPLE),
                    x0 + 18, sy, 0xFFFF66FF);
            sy += 14;
            int[] cds = {c.skillSlashCooldown, c.skillDevourCooldown, c.skillFinalityCooldown};
            WeaponSkill[] skills = WeaponSkill.values();
            for (int i = 0; i < skills.length; i++) {
                boolean unlocked = skills[i].isUnlocked(level);
                Formatting col = unlocked ? Formatting.WHITE : Formatting.DARK_GRAY;
                String head = (unlocked ? "✦ " : "✖ ") + skills[i].cn + "  CD" + (cds[i] / 20) + "s"
                        + (unlocked ? "" : "  (需「" + skills[i].unlockTier.cn + "」)");
                ctx.drawTextWithShadow(this.textRenderer, Text.literal(head).formatted(col), x0 + 18, sy, 0xFFFFFFFF);
                sy += 12;
            }
        }

        // 距离下一品质
        int toNext = q.levelsToNext(level);
        String tip = toNext < 0 ? "已达最高品质「至尊」"
                : "再强化 " + toNext + " 级 → 晋升【" + q.next().cn + "】";
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(tip).formatted(Formatting.GRAY), x0 + panelW / 2, y0 + panelH - 16, 0xFFAAAAAA);
    }

    private void line(DrawContext ctx, int x, int y, String key, String val, Formatting valColor) {
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(key + ":").formatted(Formatting.GRAY), x, y, 0xFFAAAAAA);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(val).formatted(valColor), x + 48, y, 0xFFFFFFFF);
    }

    private static String fmt(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
