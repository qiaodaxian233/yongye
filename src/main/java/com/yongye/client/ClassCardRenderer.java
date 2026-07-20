package com.yongye.client;

import com.yongye.item.PlayerClass;
import com.yongye.registry.ModItems;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.EnumMap;
import java.util.Map;

/**
 * 职业卡片渲染器(m204)——程序化绘制,替代旧的 AI 卡图 PNG(作者嫌丑)。
 * 选职界面(ClassSelectScreen)与替换界面(ClassReplaceScreen)共用,风格一次改处处生效。
 *
 * <p>卡面构成(106×132,与旧卡图同尺寸,两个界面的排版不用动):
 * 职业色描边(悬停加发光)→ 夜蚀深蓝纵向渐变底 → 放大职业名 → 分隔线 →
 * 2× 职业武器图标(武僧空手,画大字「拳」)→ 定位语 → 三行特长 → 悬停「点击选择」。
 *
 * <p>特长文案与 ClassManager.mods() / 职业技能实际数值对齐;改数值记得同步这里。
 * 配色想换只动 THEMES 里的 accent。绘制全用已验证 API:fill / drawCenteredTextWithShadow /
 * matrices push+translate+scale + drawItem(WeaponInfoScreen 同款)。
 */
public final class ClassCardRenderer {
    private ClassCardRenderer() {}

    /** 卡片尺寸(与旧卡图一致,界面网格无需改动)。 */
    public static final int CW = 106, CH = 132;

    private record Theme(int accent, String role, String p1, String p2, String p3, String ext) {}

    private static final Map<PlayerClass, Theme> THEMES = new EnumMap<>(PlayerClass.class);
    private static final Map<PlayerClass, ItemStack> ICONS = new EnumMap<>(PlayerClass.class);

    static {
        THEMES.put(PlayerClass.TANK, new Theme(0xFF4F9DFF, "不动如山",
                "生命+20 护甲+8", "伤害移速较低", "磐盾格挡反震",
                "最肉的开局:高生命高护甲,代价是伤害与移速;持磐盾被近战命中会反震伤敌。稳扎稳打首选。"));
        THEMES.put(PlayerClass.WARRIOR, new Theme(0xFFFF7A45, "均衡怒战",
                "生命+10 护甲+3", "攻击+1 三维均衡", "受击攒怒气爆发",
                "全能近战:生命、护甲、攻击都加一点;受击积攒怒气,怒满打出爆发强击。不挑食,新手友好。"));
        THEMES.put(PlayerClass.WARLOCK, new Theme(0xFFB668FF, "燃血施法",
                "攻击+1 生命-10", "交互距离更远", "法杖燃血施法",
                "玻璃大炮:牺牲生命上限换输出;职业法杖消耗生命施放法术,距离远、威力大。会走位再选。"));
        THEMES.put(PlayerClass.SWORDSMAN, new Theme(0xFFE8EDF5, "剑锋所指",
                "攻击+4 护甲-2", "举盾格挡近战", "格挡反击伤敌",
                "纯粹的剑术:全职业最高基础攻击;举盾挡下近战即可反击,持职业剑反伤更痛。"));
        THEMES.put(PlayerClass.MONK, new Theme(0xFFF0B84A, "空手苦修",
                "生命+10 护甲+5", "空手不持武器", "吞材料永久强化",
                "唯一不用武器的职业:空手作战,吞噬材料把拳意与生命永久炼进身体,越吃越强。"));
        THEMES.put(PlayerClass.ASSASSIN, new Theme(0xFF5FE08C, "疾影暗杀",
                "移速+20% 攻+2", "护甲-3 靠走位", "夜视+暗能突袭",
                "最快的影子:高移速高爆发、护甲最低;自带永夜夜视,脱战蓄暗能,近身一击致命。"));
    }

    /** 悬停时选职界面底部展示的一句话介绍。 */
    public static String extendedDesc(PlayerClass c) {
        Theme t = THEMES.get(c);
        return t == null ? "" : t.ext();
    }

    public static int accent(PlayerClass c) {
        Theme t = THEMES.get(c);
        return t == null ? 0xFFFFFFFF : t.accent();
    }

    /** 画一张职业卡。hover 时描边发光并显示「点击选择」。 */
    public static void drawCard(DrawContext ctx, TextRenderer tr, PlayerClass c, int x, int y, boolean hover) {
        Theme t = THEMES.get(c);
        if (t == null) return;
        int accent = t.accent();

        // 悬停外发光:两圈半透明职业色
        if (hover) {
            ctx.fill(x - 4, y - 4, x + CW + 4, y + CH + 4, (accent & 0x00FFFFFF) | 0x28000000);
            ctx.fill(x - 2, y - 2, x + CW + 2, y + CH + 2, (accent & 0x00FFFFFF) | 0x55000000);
        }
        // 描边(整块打底,内底盖出 1px 边)
        ctx.fill(x, y, x + CW, y + CH, hover ? brighten(accent) : accent);
        // 内底:夜蚀深蓝纵向渐变(顶部染一点职业色 → 近黑),2px 一带,零新 API
        int top = mix(accent, 0xFF131A28, 0.72f);
        for (int i = 1; i < CH - 1; i += 2) {
            float f = (i - 1) / (float) (CH - 3);
            ctx.fill(x + 1, y + i, x + CW - 1, y + Math.min(i + 2, CH - 1), mix(top, 0xFF090D15, f));
        }

        // 职业名(放大 1.25×,TitleScreenMixin 同款矩阵缩放)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + CW / 2.0, y + 6.0, 0.0);
        ctx.getMatrices().scale(1.25f, 1.25f, 1.0f);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(c.cn), 0, 0, brighten(accent));
        ctx.getMatrices().pop();
        // 名下分隔线
        ctx.fill(x + 10, y + 21, x + CW - 10, y + 22, (accent & 0x00FFFFFF) | 0x77000000);

        // 图标区:淡衬板 + 2× 武器图标(WeaponInfoScreen 同款画法);武僧空手 → 大字「拳」
        ctx.fill(x + CW / 2 - 21, y + 26, x + CW / 2 + 21, y + 62, 0x1EFFFFFF);
        ItemStack icon = iconOf(c);
        if (!icon.isEmpty()) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + CW / 2.0 - 16, y + 28.0, 0.0);
            ctx.getMatrices().scale(2.0f, 2.0f, 1.0f);
            ctx.drawItem(icon, 0, 0);
            ctx.getMatrices().pop();
        } else {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + CW / 2.0, y + 36.0, 0.0);
            ctx.getMatrices().scale(2.2f, 2.2f, 1.0f);
            ctx.drawCenteredTextWithShadow(tr, Text.literal("拳"), 0, 0, accent);
            ctx.getMatrices().pop();
        }

        // 定位语 + 三行特长
        ctx.drawCenteredTextWithShadow(tr, Text.literal("— " + t.role() + " —"), x + CW / 2, y + 67, accent);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(t.p1()), x + CW / 2, y + 81, 0xFFB9C4D4);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(t.p2()), x + CW / 2, y + 93, 0xFFB9C4D4);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(t.p3()), x + CW / 2, y + 105, 0xFFB9C4D4);

        // 悬停脚标
        if (hover) {
            ctx.drawCenteredTextWithShadow(tr, Text.literal("▶ 点击选择 ◀"), x + CW / 2, y + 119, 0xFFFFD700);
        }
    }

    /** 职业武器图标(懒取,武僧无武器返回空栈)。 */
    private static ItemStack iconOf(PlayerClass c) {
        return ICONS.computeIfAbsent(c, k -> {
            Item it = ModItems.getClassWeapon(k);
            return it == null ? ItemStack.EMPTY : new ItemStack(it);
        });
    }

    /** ARGB 通道插值:f=0 取 a,f=1 取 b。 */
    private static int mix(int a, int b, float f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * f), g = (int) (ag + (bg - ag) * f), bl = (int) (ab + (bb - ab) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** 提亮职业色(悬停描边/职业名用)。 */
    private static int brighten(int c) {
        return mix(c, 0xFFFFFFFF, 0.35f);
    }
}
