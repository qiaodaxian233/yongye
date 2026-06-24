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

        // 底衬:包住 等级行 + 血条 + MP条 + 食物条(贴合)
        int totalH = BAR_H + GAP + MP_H + GAP + FOOD_H;
        ctx.fill(left - 2, top - 11, left + BAR_W + 2, top + totalH + 2, 0xC0000000);

        // ===== 等级行(本命职业 Lv.X · 名)在血条正上方 =====
        String cls0 = ClientStats.className;
        if (cls0 != null && !cls0.isEmpty()) {
            int lv = yongye$classLevel(cls0);
            String lvStr = "Lv." + lv + " " + yongye$classCnName(cls0);
            ctx.drawTextWithShadow(tr, Text.literal(lvStr), left, top - 10, 0xFFFFD700);
        }

        // ===== 血条(m138 改蓝,配玻璃蓝 UI) =====
        ctx.fill(left, top, left + BAR_W, top + BAR_H, 0xFF06223F);              // 深蓝底
        float totalHp = maxHp + absHp;
        if (absHp > 0.5f) {
            int absEnd = (int)(BAR_W * Math.min(1f, (curHp + absHp) / totalHp));
            ctx.fill(left, top, left + absEnd, top + BAR_H, 0xFF806000);         // 金色吸收
        }
        int hpW = (int)(BAR_W * Math.max(0f, Math.min(1f, curHp / maxHp)));
        ctx.fill(left, top, left + hpW, top + BAR_H, 0xFF2E86D8);                // 亮蓝血量
        ctx.fill(left, top, left + hpW, top + 1, 0x66BFE6FF);                    // 青色玻璃高光

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

        // ===== 食物条(m138:上移到血条正下方 + 加粗成正经横条,绿色与蓝血条区分)=====
        int foodTop = top + BAR_H + GAP;
        ctx.fill(left, foodTop, left + BAR_W, foodTop + FOOD_H, 0xFF18260F);      // 深绿底
        int foodW = (int)(BAR_W * Math.max(0f, Math.min(1f, food / 20f)));
        ctx.fill(left, foodTop, left + foodW, foodTop + FOOD_H, 0xFF8FBF4A);      // 草绿填充
        ctx.fill(left, foodTop, left + foodW, foodTop + 1, 0x66FFFFFF);           // 高光
        ctx.drawGuiTexture(FOOD, left + BAR_W + 6, foodTop - 1, 8, 8);
        ctx.drawTextWithShadow(tr, Text.literal(food + "/20"), left + BAR_W + 16, foodTop, 0xFF9FCF5A);

        // ===== MP/资源 条(食物条下方) =====
        yongye$renderMpBar(ctx, tr, left, top + BAR_H + GAP + FOOD_H + GAP);

        ci.cancel();
    }

    private static void yongye$renderMpBar(DrawContext ctx, TextRenderer tr, int left, int top) {
        float mp = ClientStats.mp;
        String cls = ClientStats.className;
        if (cls == null || cls.isEmpty()) return;
        int[] colors = yongye$mpColors(cls);
        if (colors == null) return;

        ctx.fill(left, top, left + BAR_W, top + MP_H, colors[0]);
        int fillW = (int)(BAR_W * Math.max(0f, Math.min(1f, mp)));
        if (fillW > 0) {
            ctx.fill(left, top, left + fillW, top + MP_H, colors[1]);
            ctx.fill(left, top, left + fillW, top + 1, colors[2]);
        }
        String label = yongye$mpLabel(cls);
        if (!label.isEmpty()) {
            ctx.drawTextWithShadow(tr, Text.literal(label), left + BAR_W + 6, top - 2, 0xFFAAAAAA);
        }
    }

    private static int[] yongye$mpColors(String cls) {
        return switch (cls) {
            case "warlock"   -> new int[]{0xFF1A0040, 0xFF8A2BE2, 0xFFBF80FF};
            case "assassin"  -> new int[]{0xFF001020, 0xFF0066CC, 0xFF4DA6FF};
            case "warrior"   -> new int[]{0xFF300A00, 0xFFCC4400, 0xFFFF8844};
            case "swordsman" -> new int[]{0xFF001818, 0xFF00BBCC, 0xFF80EEFF};
            case "tank"      -> new int[]{0xFF10100A, 0xFF888800, 0xFFEEDD00};
            case "monk"      -> new int[]{0xFF201000, 0xFFCC8800, 0xFFFFCC44};
            default          -> null;
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
