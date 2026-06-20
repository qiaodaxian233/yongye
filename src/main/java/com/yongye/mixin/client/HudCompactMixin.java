package com.yongye.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
        int left = context.getScaledWindowWidth() / 2 - 91;
        int top = context.getScaledWindowHeight() - 39;

        // 血量(紧凑)
        context.drawGuiTexture(YONGYE_HEART, left, top, 9, 9);
        String hs = " " + health + (absorption > 0 ? "+" + absorption : "") + " / " + (int) maxHealth;
        context.drawTextWithShadow(mc.textRenderer, Text.literal(hs), left + 11, top + 1, 0xFFFF5555);

        // 护甲(同一排,画在血量右侧)
        int armor = player.getArmor();
        if (armor > 0) {
            int ax = left + 11 + mc.textRenderer.getWidth(hs) + 10;
            context.drawGuiTexture(YONGYE_ARMOR, ax, top, 9, 9);
            context.drawTextWithShadow(mc.textRenderer, Text.literal(" " + armor), ax + 11, top + 1, 0xFFFFFFFF);
        }
        ci.cancel();
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
