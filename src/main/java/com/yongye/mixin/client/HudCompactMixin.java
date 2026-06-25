package com.yongye.mixin.client;

import com.yongye.client.ClientStats;
import com.yongye.client.HealthRateTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD 重写(m94):
 * 1. 血量超阈值时替换原版心形为 RPG 横向血条;血量正常则保持原版。
 * 2. 血条下方画职业资源条(MP)。
 * 3. 整合饥饿/护甲到血条同排(图标+数字),取消原版食物条/护甲条上浮。
 * 4. 整体上移,不再和底部物品栏/原版食物条争位。
 */
@Mixin(InGameHud.class)
public class HudCompactMixin {

    private static final float THRESHOLD = 60.0f;

    private static final int BAR_W = 182;
    private static final int BAR_H = 6;
    private static final int MP_H  = 4;
    private static final int FOOD_H = 6;
    private static final int GAP   = 2;

    private static final Identifier HEART = Identifier.ofVanilla("hud/heart/full");
    private static final Identifier ARMOR = Identifier.ofVanilla("hud/armor_full");
    private static final Identifier FOOD  = Identifier.ofVanilla("hud/food_full");

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$renderStatusBars(DrawContext ctx, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;
        float maxHp = player.getMaxHealth();
        float absHp = player.getAbsorptionAmount();
        if (maxHp + absHp <= THRESHOLD) return;  // 低血量交回原版,不接管
        TextRenderer tr = mc.textRenderer;

        float curHp = player.getHealth();
        int   armor = player.getArmor();
        int   food  = player.getHungerManager().getFoodLevel();
        float rate  = HealthRateTracker.getRatePerSec();

        // 锚点:整个 HUD 块下移,贴近物品栏上方
        int left = mc.getWindow().getScaledWidth() / 2 - 91;
        int top  = mc.getWindow().getScaledHeight() - 44;

        // 底衬(m142 方案A 精致玻璃):2px 切角圆角 + 玻璃描边 + 顶亮底暗渐变;配色仍蓝系
        int totalH = BAR_H + GAP + MP_H + GAP + FOOD_H;
        yongye$panel(ctx, left, top - 11, BAR_W, totalH + 13, 0xCC1B5288, 0xCC0C2C50, 0xFF2E7AD0);

        // ===== 等级行(本命职业 Lv.X · 名)在血条正上方 =====
        String cls0 = ClientStats.className;
        if (cls0 != null && !cls0.isEmpty()) {
            int lv = yongye$classLevel(cls0);
            String lvStr = "Lv." + lv + " " + yongye$classCnName(cls0);
            ctx.drawTextWithShadow(tr, Text.literal(lvStr), left, top - 10, 0xFFFFD700);
        }

        // ===== 血条(红色,所有职业统一;m142 渐变+高光+末端光头)=====
        ctx.fill(left, top, left + BAR_W, top + BAR_H, 0xFF3B0000);              // 深红底槽
        ctx.fill(left, top, left + BAR_W, top + 1, 0x60000000);                 // 顶内阴影(凹陷感)
        float totalHp = maxHp + absHp;
        if (absHp > 0.5f) {
            int absEnd = (int)(BAR_W * Math.min(1f, (curHp + absHp) / totalHp));
            ctx.fill(left, top, left + absEnd, top + BAR_H, 0xFF806000);         // 金色吸收
        }
        int hpW = (int)(BAR_W * Math.max(0f, Math.min(1f, curHp / maxHp)));
        if (hpW > 0) {
            yongye$gradV(ctx, left, top, hpW, BAR_H, 0xFFE83030, 0xFF8B0000);    // 红血量渐变(上亮下暗)
            ctx.fill(left, top, left + hpW, top + 1, 0x90FFFFFF);                // 顶高光
            if (hpW >= 2) ctx.fill(left + hpW - 2, top, left + hpW, top + BAR_H, 0xFFFF7070);  // 末端光头
        }

        String hpStr = yongye$num(curHp) + " / " + yongye$num(maxHp)
                + (absHp >= 0.5f ? "  +" + yongye$num(absHp) : "");
        int tw = tr.getWidth(hpStr);
        ctx.drawTextWithShadow(tr, Text.literal(hpStr), left + (BAR_W - tw) / 2, top - 1, 0xFFFFFFFF);

        // 速率(条左)
        if (Math.abs(rate) >= 0.1f) {
            String rs = yongye$rate(rate);
            int col = rate > 0 ? 0xFF55FF55 : 0xFFFF5555;
            ctx.drawTextWithShadow(tr, Text.literal(rs), left - tr.getWidth(rs) - 5, top - 1, col);
        }

        // 血条右侧:护甲(图标+数字)
        int rx = left + BAR_W + 6;
        if (armor > 0) {
            ctx.drawGuiTexture(ARMOR, rx, top - 1, 8, 8);
            String as = String.valueOf(armor);
            ctx.drawTextWithShadow(tr, Text.literal(as), rx + 10, top, 0xFFB0C4FF);
        }

        // ===== 食物条(黄色,血条正下方;m142 渐变+高光+末端光头)=====
        int foodTop = top + BAR_H + GAP;
        int foodW = (int)(BAR_W * Math.max(0f, Math.min(1f, food / 20f)));
        yongye$bar(ctx, left, foodTop, BAR_W, FOOD_H, 0xFF332600, foodW, 0xFFF2D84E, 0xFFB89A1E, 0xFFFFF0A0);
        ctx.drawGuiTexture(FOOD, left + BAR_W + 6, foodTop - 1, 8, 8);
        ctx.drawTextWithShadow(tr, Text.literal(food + "/20"), left + BAR_W + 16, foodTop, 0xFFE6C42A);

        // ===== MP/资源 条(食物条下方) =====
        yongye$renderMpBar(ctx, tr, left, top + BAR_H + GAP + FOOD_H + GAP);

        ci.cancel();
    }

    /** 0xAARRGGBB 颜色线性插值(含 alpha)。 */
    private static int yongye$lerp(int c1, int c2, float t) {
        int a1 = (c1 >>> 24) & 255, r1 = (c1 >> 16) & 255, g1 = (c1 >> 8) & 255, b1 = c1 & 255;
        int a2 = (c2 >>> 24) & 255, r2 = (c2 >> 16) & 255, g2 = (c2 >> 8) & 255, b2 = c2 & 255;
        int a = (int)(a1 + (a2 - a1) * t), r = (int)(r1 + (r2 - r1) * t);
        int g = (int)(g1 + (g2 - g1) * t), b = (int)(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 垂直渐变填充(逐行 fill;top→bot)。 */
    private static void yongye$gradV(DrawContext ctx, int x, int y, int w, int h, int top, int bot) {
        if (w <= 0 || h <= 0) return;
        for (int i = 0; i < h; i++) {
            float t = h > 1 ? (float) i / (h - 1) : 0f;
            ctx.fill(x, y + i, x + w, y + i + 1, yongye$lerp(top, bot, t));
        }
    }

    /** 精致玻璃底衬:2px 切角 + 顶亮底暗渐变 + 玻璃描边 + 顶部内高光。 */
    private static void yongye$panel(DrawContext ctx, int x, int y, int w, int h, int top, int bot, int edge) {
        if (w <= 4 || h <= 4) return;
        yongye$gradV(ctx, x, y + 2, w, h - 4, top, bot);          // 主体(满宽渐变)
        ctx.fill(x + 2, y, x + w - 2, y + 2, top);                // 顶 2 行(切角缩进)
        ctx.fill(x + 2, y + h - 2, x + w - 2, y + h, bot);        // 底 2 行(切角缩进)
        ctx.fill(x + 2, y, x + w - 2, y + 1, edge);               // 上描边
        ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, edge);       // 下描边
        ctx.fill(x, y + 2, x + 1, y + h - 2, edge);               // 左描边
        ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, edge);       // 右描边
        ctx.fill(x + 3, y + 2, x + w - 3, y + 3, 0x40FFFFFF);      // 顶部内高光
    }

    /** 一根条:底槽 + 顶内阴影 + 渐变填充 + 顶高光 + 末端光头。 */
    private static void yongye$bar(DrawContext ctx, int x, int y, int w, int h, int bg,
                                   int fillW, int fTop, int fBot, int head) {
        ctx.fill(x, y, x + w, y + h, bg);                         // 底槽
        ctx.fill(x, y, x + w, y + 1, 0x60000000);                 // 顶内阴影
        int fw = Math.max(0, Math.min(fillW, w));
        if (fw > 0) {
            yongye$gradV(ctx, x, y, fw, h, fTop, fBot);           // 渐变填充
            ctx.fill(x, y, x + fw, y + 1, 0x90FFFFFF);            // 顶高光
            if (fw >= 2) ctx.fill(x + fw - 2, y, x + fw, y + h, head);  // 末端光头
        }
    }

    private static void yongye$renderMpBar(DrawContext ctx, TextRenderer tr, int left, int top) {
        float mp = ClientStats.mp;
        String cls = ClientStats.className;
        if (cls == null || cls.isEmpty()) return;
        int[] colors = yongye$mpColors(cls);
        if (colors == null) return;

        int fillW = (int)(BAR_W * Math.max(0f, Math.min(1f, mp)));
        // m142:渐变+高光+末端光头(colors[0]底槽 / [1]填充主色 / [2]高光&光头)
        yongye$bar(ctx, left, top, BAR_W, MP_H, colors[0],
                   fillW, yongye$lerp(colors[1], colors[2], 0.35f), colors[1], colors[2]);
        String label = yongye$mpLabel(cls);
        if (!label.isEmpty()) {
            ctx.drawTextWithShadow(tr, Text.literal(label), left + BAR_W + 6, top - 2, 0xFFAAAAAA);
        }
    }

    private static int[] yongye$mpColors(String cls) {
        // m139:所有职业统一蓝色资源条(底 / 填充 / 高光)
        return switch (cls) {
            case "warlock", "assassin", "warrior", "swordsman", "tank", "monk"
                    -> new int[]{0xFF0A1E38, 0xFF2E7AD0, 0xFF7FCFFF};
            default -> null;
        };
    }

    private static String yongye$mpLabel(String cls) {
        return switch (cls) {
            case "warlock"   -> "灵力";
            case "assassin"  -> "暗能";
            case "warrior"   -> "怒气";
            case "swordsman" -> "剑气";
            case "tank"      -> "坚守";
            case "monk"      -> "拳意";
            default          -> "";
        };
    }

    /** 职业 id → 中文名(HUD 等级行用)。 */
    private static String yongye$classCnName(String cls) {
        return switch (cls) {
            case "tank"      -> "肉盾";
            case "warrior"   -> "战士";
            case "warlock"   -> "术士";
            case "swordsman" -> "剑客";
            case "monk"      -> "武僧";
            case "assassin"  -> "刺客";
            default          -> "";
        };
    }

    /** 本命职业的等级(levels 数组顺序:tank/warrior/warlock/swordsman/monk/assassin)。 */
    private static int yongye$classLevel(String cls) {
        int idx = switch (cls) {
            case "tank" -> 0; case "warrior" -> 1; case "warlock" -> 2;
            case "swordsman" -> 3; case "monk" -> 4; case "assassin" -> 5;
            default -> -1;
        };
        int[] lv = ClientStats.levels;
        return (idx >= 0 && idx < lv.length) ? lv[idx] : 0;
    }

    /** 数字紧凑显示:<1000 原样,≥1000 显示 K,≥100万 显示 M(整千/整百万不带小数)。 */
    private static String yongye$num(float v) {
        long n = Math.round(v);
        if (n < 1000L) return String.valueOf(n);
        if (n < 1_000_000L) {
            double k = n / 1000.0;
            return (k == Math.floor(k) ? String.valueOf((long) k)
                    : String.format(Locale.ROOT, "%.1f", k)) + "K";
        }
        double m = n / 1_000_000.0;
        return (m == Math.floor(m) ? String.valueOf((long) m)
                : String.format(Locale.ROOT, "%.1f", m)) + "M";
    }

    private static String yongye$rate(float r) {
        float a = Math.abs(r);
        String n = (a < 10f) ? String.format(Locale.ROOT, "%.1f", a) : String.valueOf(Math.round(a));
        return (r > 0 ? "+" : "-") + n + "/s";
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void yongye$cancelArmor(DrawContext ctx, PlayerEntity player,
                                           int a, int b, int c, int x, CallbackInfo ci) {
        float total = player.getMaxHealth() + player.getAbsorptionAmount();
        if (total > THRESHOLD) ci.cancel();
    }

    /**
     * 取消原版食物条(已整合到血条右侧)。
     * 【待编译验证】1.21.1 InGameHud 食物条方法名:可能是 renderFood / renderHungerBar。
     * 若 build 报"找不到方法 renderFood",改成本地 yarn 实际名(用 mc.player 反查或看 InGameHud)。
     */
    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void yongye$cancelFood(DrawContext ctx, PlayerEntity player, int x, int y, CallbackInfo ci) {
        float total = player.getMaxHealth() + player.getAbsorptionAmount();
        if (total > THRESHOLD) ci.cancel();
    }
}
