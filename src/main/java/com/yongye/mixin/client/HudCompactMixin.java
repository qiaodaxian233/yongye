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
 * 血量/护甲数值过大时,原版会铺满整屏心形/图标遮挡视线。
 * 本 mixin 在数值超过阈值时,接管 InGameHud 的血条与护甲绘制,改为「图标 + 数值」一行紧凑显示;
 * 数值正常时不接管,保持原版观感。
 */
@Mixin(InGameHud.class)
public class HudCompactMixin {

    /** 血量(含吸收)超过此点数时改用紧凑显示(约 30 颗心)。 */
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
        context.drawGuiTexture(YONGYE_HEART, left, top, 9, 9);
        String s = " " + health + (absorption > 0 ? "+" + absorption : "") + " / " + (int) maxHealth;
        context.drawTextWithShadow(mc.textRenderer, Text.literal(s), left + 11, top + 1, 0xFFFF5555);
        ci.cancel();
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void yongye$compactArmor(DrawContext context, PlayerEntity player,
                                            int a, int b, int c, int x, CallbackInfo ci) {
        int armor = player.getArmor();
        if (armor <= 20) return; // 原版护甲最多 10 个图标,不溢出时不接管
        MinecraftClient mc = MinecraftClient.getInstance();
        int left = context.getScaledWindowWidth() / 2 - 91;
        int top = context.getScaledWindowHeight() - 49;
        context.drawGuiTexture(YONGYE_ARMOR, left, top, 9, 9);
        context.drawTextWithShadow(mc.textRenderer, Text.literal(" " + armor), left + 11, top + 1, 0xFFFFFFFF);
        ci.cancel();
    }
}
