package com.yongye.client;

import com.yongye.YongyeConfig;
import com.yongye.item.WeaponQuality;
import com.yongye.item.WeaponSkill;
import com.yongye.network.UpgradeWeaponSkillPayload;
import com.yongye.registry.ModComponents;
import com.yongye.registry.ModItems;
import com.yongye.system.EquipmentEnhancer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

    private static final int PANEL_W = 320, PANEL_H = 270;

    // ===== m347 服务端同步的技能数据(WeaponSkillLvPayload → YongyeClient 接收器写入;-1=尚未收到) =====
    /** 三技能当前升级等级(WeaponSkill 枚举序)。 */
    private static final int[] syncLv = {-1, -1, -1};
    /** 升到下一级所需终焉精华(服务端按服务端配置算好)。 */
    private static final int[] syncCost = new int[3];
    /** 生效冷却 tick(已按技能等级缩减,与 use() 施放同式)。 */
    private static final int[] syncCd = new int[3];
    /** 技能等级上限(服务端配置)。 */
    private static int syncMax = -1;

    /** m347:收到服务端技能等级同步(YongyeClient 接收器调用,主线程)。 */
    public static void onSync(com.yongye.network.WeaponSkillLvPayload p) {
        syncLv[0] = p.lv0(); syncLv[1] = p.lv1(); syncLv[2] = p.lv2();
        syncCost[0] = p.cost0(); syncCost[1] = p.cost1(); syncCost[2] = p.cost2();
        syncCd[0] = p.cd0(); syncCd[1] = p.cd1(); syncCd[2] = p.cd2();
        syncMax = p.maxLevel();
    }

    private final Screen parent;
    private final ItemStack stack;
    /** m347 升级按钮引用:同步到满级后禁用。 */
    private ButtonWidget[] skillButtons = null;

    public WeaponInfoScreen(Screen parent, ItemStack stack) {
        super(Text.literal("装备介绍"));
        this.parent = parent;
        this.stack = stack;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(this.width / 2 - 50, this.height - 34, 100, 20).build());

        // 武器技能升级按钮(仅武器 + 开关开):各对应一个技能,点击用背包终焉精华升一级(服务端校验+反馈)
        YongyeConfig cfg = YongyeConfig.get();
        if (EquipmentEnhancer.isWeapon(stack) && cfg.enableWeaponSkillUpgrade) {
            int y0 = (this.height - PANEL_H) / 2;
            int x0 = (this.width - PANEL_W) / 2;
            WeaponSkill[] sk = WeaponSkill.values();
            int bw = 96, gap = 8, by = y0 + 224;
            int total = bw * sk.length + gap * (sk.length - 1);
            int bx = x0 + (PANEL_W - total) / 2;
            skillButtons = new ButtonWidget[sk.length];
            for (int i = 0; i < sk.length; i++) {
                final int idx = i;
                skillButtons[i] = addDrawableChild(ButtonWidget.builder(Text.literal("升·" + sk[i].cn),
                                b -> ClientPlayNetworking.send(new UpgradeWeaponSkillPayload(idx)))
                        .dimensions(bx + i * (bw + gap), by, bw, 20).build());
            }
            // m347:开屏即请求当前技能等级(回包经 YongyeClient 写入静态字段,面板下一帧刷新)
            ClientPlayNetworking.send(new com.yongye.network.RequestWeaponSkillPayload());
        }
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

        int panelW = PANEL_W, panelH = PANEL_H;
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
        // m209:行距 14→12。旧版 4 行属性(含耐久度)会画到 y0+112~121,
        // 而下方品质框底色从 y0+116 起——耐久度那行正好压进框里(实机截图的"重叠")。
        if (weapon) {
            // m237:肉盾系(HYBRID)攻击按折减显示(此前漏乘,显示虚高);攻速+暴击合并一行,
            // 腾出的行显示强化生命(普通武器 0.1/级,肉盾系 1.0/级)——仍 3 行,不破 m209 版面
            boolean hybrid = EquipmentEnhancer.kindOf(stack.getItem()) == EquipmentEnhancer.Kind.HYBRID;
            double atk = EquipmentEnhancer.attackBonusFor(level, c.enhanceDamagePerLevel)
                    * (hybrid ? c.enhanceHybridDamageFraction : 1.0); // m298 曲线(与实际属性同式)
            double hp = level * (hybrid ? c.enhanceHealthPerLevel : c.enhanceWeaponHealthPerLevel);
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("攻击力  +" + NumFmt.compact(atk)).formatted(Formatting.RED), rx, ry, 0xFFFF5555); ry += 12;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("攻速 +" + fmt(q.attackSpeed) + " · 暴击 +" + (int) Math.round(q.critChance * 100) + "%")
                            .formatted(Formatting.GOLD), rx, ry, 0xFFFFAA00); ry += 12;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("最大生命  +" + NumFmt.compact(hp)).formatted(Formatting.RED), rx, ry, 0xFFFF5555); ry += 12;
        } else {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("护甲  +" + NumFmt.compact(level * c.enhanceArmorPerLevel)).formatted(Formatting.AQUA), rx, ry, 0xFF55FFFF); ry += 12;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("韧性  +" + NumFmt.compact(level * c.enhanceToughnessPerLevel)).formatted(Formatting.AQUA), rx, ry, 0xFF55FFFF); ry += 12;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("最大生命  +" + NumFmt.compact(level * c.enhanceHealthPerLevel)).formatted(Formatting.RED), rx, ry, 0xFFFF5555); ry += 12;
        }
        if (stack.isDamageable()) {
            int max = stack.getMaxDamage();
            int cur = max - stack.getDamage();
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("耐久度  " + cur + " / " + max).formatted(Formatting.AQUA), rx, ry, 0xFF55FFFF); ry += 12;
        }

        // 品质 / 类型 / 稀有度 / 强化(m209:框上移到 y0+112、行距 12,框底 y0+164,
        // 与上方属性区(最深 y0+101)和下方"✦ 神器技能"(y0+168)都留出净空,不再互压)
        int qy = y0 + 112;
        ctx.fill(rx - 6, qy - 4, x0 + panelW - 14, qy + 52, 0x33FFFFFF);
        line(ctx, rx, qy, "品质", q.cn, q.color); qy += 12;
        line(ctx, rx, qy, "类型", weapon ? "武器" : "盔甲", Formatting.WHITE); qy += 12;
        line(ctx, rx, qy, "稀有度", q.grade, q.color); qy += 12;
        line(ctx, rx, qy, "强化", "+" + level, Formatting.AQUA); qy += 12;

        // 神器技能区(仅武器)
        if (weapon) {
            int sy = y0 + 168;
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("✦ 神器技能").formatted(Formatting.LIGHT_PURPLE),
                    x0 + 18, sy, 0xFFFF66FF);
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("(下方按钮·终焉精华升级)").formatted(Formatting.DARK_GRAY),
                    x0 + 92, sy, 0xFF888888);
            sy += 14;
            int[] cds = {c.skillSlashCooldown, c.skillDevourCooldown, c.skillFinalityCooldown};
            WeaponSkill[] skills = WeaponSkill.values();
            for (int i = 0; i < skills.length; i++) {
                boolean unlocked = skills[i].isUnlocked(level) || stack.getItem() == ModItems.CHAOS_BLADE
                        || stack.getItem() == ModItems.DRAGON_BLADE;   // m347:龙魂免解锁与施放口径(m331)对齐
                Formatting col = unlocked ? Formatting.WHITE : Formatting.DARK_GRAY;
                String head;
                boolean synced = syncLv[i] >= 0;   // m347:服务端已同步=显示真实等级/生效CD/升级花费
                boolean maxed = synced && syncMax >= 0 && syncLv[i] >= syncMax;
                if (synced) {
                    head = (unlocked ? "✦ " : "✖ ") + skills[i].cn + " Lv." + syncLv[i]
                            + "  CD" + (syncCd[i] / 20) + "s";
                    if (!unlocked) head += "  (需「" + skills[i].unlockTier.cn + "」)";
                    else if (maxed) head += "  已满级";
                    else head += "  升级:精华×" + syncCost[i];
                    if (maxed && unlocked) col = Formatting.GOLD;
                } else {   // 未收到同步(刚开屏一瞬/离线容错):回落配置基础CD老显示
                    head = (unlocked ? "✦ " : "✖ ") + skills[i].cn + "  CD" + (cds[i] / 20) + "s"
                            + (unlocked ? "" : "  (需「" + skills[i].unlockTier.cn + "」)");
                }
                ctx.drawTextWithShadow(this.textRenderer, Text.literal(head).formatted(col), x0 + 18, sy, 0xFFFFFFFF);
                sy += 12;
                if (skillButtons != null && skillButtons[i] != null) skillButtons[i].active = !maxed;   // 满级禁按钮
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
