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
 * HUD 重写(m93):
 * 1. 血量超阈值时替换为 RPG 横向血条(带数字/速率/护甲),否则保持原版心形。
 * 2. 血条下方画职业资源条(MP),按职业颜色区分。
 * 3. 原版护甲条一并接管,避免心数上顶时飘移。
 */
@Mixin(InGameHud.class)
public class HudCompactMixin {

    /** 超过该血量时切换紧凑模式 */
    private static final float THRESHOLD = 60.0f;

    /** 血条尺寸 */
    private static final int BAR_W = 182; // 和原版经验条等宽
    private static final int BAR_H = 6;
    private static final int MP_H  = 4;
    private static final int GAP   = 2;   // 血条和 MP 条间距

    private static final Identifier HEART = Identifier.ofVanilla("hud/heart/full");
    private static final Identifier ARMOR = Identifier.ofVanilla("hud/armor_full");

    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void yongye$renderHealthBar(DrawContext ctx, PlayerEntity player, int x, int y,
                                        int lines, int regenIdx, float maxHealth,
                                        int lastHealth, int health, int absorption,
                                        boolean blinking, CallbackInfo ci) {
        float maxHp = player.getMaxHealth();
        float absHp = player.getAbsorptionAmount();
        if (maxHp + absHp <= THRESHOLD) return; // 血量正常:保持原版

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;

        float curHp  = player.getHealth();
        int   armor  = player.getArmor();
        float rate   = HealthRateTracker.getRatePerSec();

        // 布局锚点:左下角,和原版血条对齐
        int left = mc.getWindow().getScaledWidth() / 2 - 91;
        int top  = mc.getWindow().getScaledHeight() - 39;

        // ===== 底衬 =====
        int padX = 4, padY = 3;
        int totalH = BAR_H + GAP + MP_H;
        ctx.fill(left - padX, top - padY,
                 left + BAR_W + padX, top + totalH + padY, 0xA0000000);

        // ===== 血条 =====
        // 底色(深红)
        ctx.fill(left, top, left + BAR_W, top + BAR_H, 0xFF3B0000);
        // 吸收层(金色,叠在血条右侧)
        float totalHp   = maxHp + absHp;
        int   absBarEnd = (int)(BAR_W * Math.min(1f, (curHp + absHp) / totalHp));
        if (absHp > 0.5f) {
            ctx.fill(left, top, left + absBarEnd, top + BAR_H, 0xFF806000);
        }
        // 当前血(鲜红)
        int hpBarW = (int)(BAR_W * Math.max(0f, Math.min(1f, curHp / maxHp)));
        ctx.fill(left, top, left + hpBarW, top + BAR_H, 0xFFCC1010);
        // 血条高光(顶部 1px 亮线)
        ctx.fill(left, top, left + hpBarW, top + 1, 0x40FFFFFF);

        // 血量文字(居中在血条上)
        String hpStr = yongye$num(curHp) + " / " + yongye$num(maxHp)
                + (absHp >= 0.5f ? "  +" + yongye$num(absHp) : "");
        int tw = tr.getWidth(hpStr);
        ctx.drawTextWithShadow(tr, Text.literal(hpStr),
                left + (BAR_W - tw) / 2, top + (BAR_H - 7) / 2, 0xFFFFFFFF);

        // 护甲值(血条右侧)
        if (armor > 0) {
            int ax = left + BAR_W + 5;
            ctx.drawGuiTexture(ARMOR, ax, top, 8, 8);
            ctx.drawTextWithShadow(tr, Text.literal(String.valueOf(armor)), ax + 10, top + 1, 0xFFB0C4FF);
        }

        // 速率(血条左侧,绿=回血 红=掉血)
        if (Math.abs(rate) >= 0.1f) {
            String rateStr = yongye$rate(rate);
            int col = rate > 0 ? 0xFF55FF55 : 0xFFFF5555;
            ctx.drawTextWithShadow(tr, Text.literal(rateStr), left - tr.getWidth(rateStr) - 5, top + 1, col);
        }

        // ===== MP 条 =====
        yongye$renderMpBar(ctx, tr, left, top + BAR_H + GAP);

        ci.cancel();
    }

    private static void yongye$renderMpBar(DrawContext ctx, TextRenderer tr, int left, int top) {
        float mp = ClientStats.mp;
        String cls = ClientStats.className;
        if (cls == null || cls.isEmpty()) return;

        // 各职业颜色 + 名称
        int[] colors = yongye$mpColors(cls);
        if (colors == null) return;
        int bgCol   = colors[0]; // 底色
        int fillCol = colors[1]; // 填充色
        int hlCol   = colors[2]; // 高光
        String label = yongye$mpLabel(cls);

        // 底色
        ctx.fill(left, top, left + BAR_W, top + MP_H, bgCol);
        // 填充
        int fillW = (int)(BAR_W * Math.max(0f, Math.min(1f, mp)));
        if (fillW > 0) {
            ctx.fill(left, top, left + fillW, top + MP_H, fillCol);
            ctx.fill(left, top, left + fillW, top + 1, hlCol); // 高光线
        }
        // 标签(条右侧小字)
        if (!label.isEmpty()) {
            ctx.drawTextWithShadow(tr, Text.literal(label),
                    left + BAR_W + 5, top - 1, 0xFFAAAAAA);
        }
    }

    /** 按职业返回 [底色, 填充色, 高光色];不认识的职业返回 null */
    private static int[] yongye$mpColors(String cls) {
        return switch (cls) {
            case "warlock"   -> new int[]{0xFF1A0040, 0xFF8A2BE2, 0xFFBF80FF}; // 紫:灵力
            case "assassin"  -> new int[]{0xFF001020, 0xFF0066CC, 0xFF4DA6FF}; // 深蓝:暗能
            case "warrior"   -> new int[]{0xFF300A00, 0xFFCC4400, 0xFFFF8844}; // 橙红:怒气
            case "swordsman" -> new int[]{0xFF001818, 0xFF00BBCC, 0xFF80EEFF}; // 青白:剑气
            case "tank"      -> new int[]{0xFF10100A, 0xFF888800, 0xFFEEDD00}; // 暗金:坚守
            case "monk"      -> new int[]{0xFF201000, 0xFFCC8800, 0xFFFFCC44}; // 金橙:拳意
            default          -> null;
        };
    }

    /** 条旁边显示的资源名 */
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

    private static String yongye$num(float v) {
        return String.valueOf(Math.round(v));
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
        if (total > THRESHOLD) {
            // 紧凑模式已把护甲画到血条旁,取消原版护甲条(否则往上飘)
            ci.cancel();
        }
    }
}
