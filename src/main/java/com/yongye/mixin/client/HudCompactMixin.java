package com.yongye.mixin.client;

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
 * 血量/护甲数值过大时,原版会铺满整屏图标、且护甲条被多行心数往上顶("飘")。
 * 本 mixin 在血量超阈值时改为「图标+数值」一行紧凑显示,并把护甲值画在同一排(血量右侧),
 * 同时取消原版会浮动的护甲条;数值正常时保持原版观感。
 */
@Mixin(InGameHud.class)
public class HudCompactMixin {

    private static final float YONGYE_HEALTH_THRESHOLD = 60.0f;
    private static final Identifier YONGYE_HEART = Identifier.ofVanilla("hud/heart/full");
    private static final Identifier YONGYE_ARMOR = Identifier.ofVanilla("hud/armor_full");

    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void yongye$compactHealth(DrawContext context, PlayerEntity player, int x, int y,
                                      int lines, int regeneratingHeartIndex, float maxHealth,
                                      int lastHealth, int health, int absorption, boolean blinking,
                                      CallbackInfo ci) {
        if (maxHealth + absorption <= YONGYE_HEALTH_THRESHOLD) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;

        // 关键:血量一律读「客户端实时值」,不用原版传入的动画形参(health/lastHealth)。
        // 原版形参带受伤抖动 + 回血心跳延迟,会让数字阶梯跳变、看不出实时回血;
        // getHealth() 是当前帧真实生命,回血时数字会平滑地往上走。
        float curHp = player.getHealth();
        float maxHp = player.getMaxHealth();
        float absorb = player.getAbsorptionAmount();
        int armor = player.getArmor();
        float rate = HealthRateTracker.getRatePerSec();

        // 各段文本
        String hpText = yongye$num(curHp)
                + (absorb >= 0.5f ? "+" + yongye$num(absorb) : "")
                + " / " + yongye$num(maxHp);
        String armorText = armor > 0 ? String.valueOf(armor) : null;
        String rateText = (Math.abs(rate) >= 0.1f) ? yongye$rate(rate) : null;

        // 布局:屏幕底部、血条原位(主物品栏左缘上方),整排横向排开
        int left = context.getScaledWindowWidth() / 2 - 91;
        int top = context.getScaledWindowHeight() - 39;
        final int ICON = 9;   // 图标边长
        final int GAP = 3;    // 图标与其文字间距
        final int SEG = 8;    // 段与段之间距

        // 先量总宽,好画底衬
        int wHp = tr.getWidth(hpText);
        int total = ICON + GAP + wHp;
        int wArmor = 0;
        if (armorText != null) {
            wArmor = tr.getWidth(armorText);
            total += SEG + ICON + GAP + wArmor;
        }
        int wRate = 0;
        if (rateText != null) {
            wRate = tr.getWidth(rateText);
            total += SEG + wRate;
        }

        // 半透明深色底衬(提升亮背景下可读性)
        final int PADX = 3, PADY = 2;
        context.fill(left - PADX, top - PADY, left + total + PADX, top + ICON + PADY, 0x90000000);

        // 心形 + 血量(白字最清晰,语义靠红心图标传达)
        int cx = left;
        context.drawGuiTexture(YONGYE_HEART, cx, top, ICON, ICON);
        cx += ICON + GAP;
        context.drawTextWithShadow(tr, Text.literal(hpText), cx, top + 1, 0xFFFFFFFF);
        cx += wHp;

        // 护甲(同排,蓝白字)
        if (armorText != null) {
            cx += SEG;
            context.drawGuiTexture(YONGYE_ARMOR, cx, top, ICON, ICON);
            cx += ICON + GAP;
            context.drawTextWithShadow(tr, Text.literal(armorText), cx, top + 1, 0xFFB0C4FF);
            cx += wArmor;
        }

        // 回血/掉血速率(绿=回血,红=掉血;静止时不显示)
        if (rateText != null) {
            cx += SEG;
            int col = rate > 0 ? 0xFF55FF55 : 0xFFFF6666;
            context.drawTextWithShadow(tr, Text.literal(rateText), cx, top + 1, col);
        }
        ci.cancel();
    }

    /** 血量数字格式化:取整(高血量下小数是噪音,实时回血由「速率」段体现)。 */
    private static String yongye$num(float v) {
        return String.valueOf(Math.round(v));
    }

    /** 速率格式化:小幅保留 1 位小数、大幅取整,带正负号与 /s 后缀。 */
    private static String yongye$rate(float r) {
        float a = Math.abs(r);
        String n = (a < 10f) ? String.format(Locale.ROOT, "%.1f", a) : String.valueOf(Math.round(a));
        return (r > 0 ? "+" : "-") + n + "/s";
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void yongye$compactArmor(DrawContext context, PlayerEntity player,
                                            int a, int b, int c, int x, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float total = player.getMaxHealth() + player.getAbsorptionAmount();
        int armor = player.getArmor();

        // 紧凑血量已接管:护甲已和血量画在同一排 → 取消原版护甲条(否则它会被多行心数顶得往上飘)
        if (total > YONGYE_HEALTH_THRESHOLD) {
            ci.cancel();
            return;
        }
        // 血量正常但护甲溢出(>20):单独紧凑显示在血量上方一行(固定,不浮动)
        if (armor <= 20) return;
        int left = context.getScaledWindowWidth() / 2 - 91;
        int top = context.getScaledWindowHeight() - 49;
        context.drawGuiTexture(YONGYE_ARMOR, left, top, 9, 9);
        context.drawTextWithShadow(mc.textRenderer, Text.literal(" " + armor), left + 11, top + 1, 0xFFFFFFFF);
        ci.cancel();
    }
}
