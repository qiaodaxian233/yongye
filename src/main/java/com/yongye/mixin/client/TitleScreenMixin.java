package com.yongye.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 主菜单「永夜」暗黑化。
 *
 * 思路(最稳):只在 render 的 TAIL 叠加绘制,不取消原版任何渲染(不碰 logo/全景图/按钮的
 * 原生绘制流程),因此版本兼容性最好、最不易崩:
 *   1. 全屏压暗 —— 营造暗黑末日氛围(按钮有自身贴图,压暗后仍清晰可读)。
 *   2. 顶部深色横幅 —— 盖住原版 MINECRAFT logo(否则会与「永夜」大字重叠)。
 *   3. 「永夜」血红大字(矩阵放大)+ 一行副标题。
 *
 * 备注:原版 logo 是像素贴图图集、无法塞中文,故标题只能用文字重画(本 mixin 即此法)。
 * 顶部横幅高度 86 是按常见分辨率估的;若某些 GUI 缩放下露出原 logo 或压到按钮,调这个值即可。
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void yongye$darkTitle(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // 1. 全屏压暗:整体暗黑氛围
        ctx.fill(0, 0, w, h, 0x66000000);

        // 2. 顶部深色横幅:遮住原版 MINECRAFT logo(暗红黑)
        ctx.fill(0, 0, w, 86, 0xD2120006);

        // 3. 「永夜」大字(血红 + 阴影):矩阵放大后绘制,水平居中
        Text title = Text.literal("永夜").formatted(Formatting.BOLD);
        int titleW = mc.textRenderer.getWidth(title);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(w / 2.0, 22.0, 0.0);
        ctx.getMatrices().scale(4.5f, 4.5f, 1.0f);
        ctx.drawText(mc.textRenderer, title, -titleW / 2, 0, 0xFFC81E1E, true);
        ctx.getMatrices().pop();

        // 4. 副标题小字(居中)
        Text sub = Text.literal("ETERNAL NIGHT · 活下去").formatted(Formatting.GRAY);
        int subW = mc.textRenderer.getWidth(sub);
        ctx.drawText(mc.textRenderer, sub, w / 2 - subW / 2, 66, 0xFFB0B0B0, true);
    }
}
