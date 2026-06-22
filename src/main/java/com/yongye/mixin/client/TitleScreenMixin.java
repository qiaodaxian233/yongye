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
 *   2. 顶部不透明横幅 + 血红下边线 —— 彻底盖死原版 MINECRAFT logo
 *      (旧版半透明横幅会让 logo 透出来很乱;现改为完全不透明 + 下方渐变过渡)。
 *   3. 「永夜」血红大字(暗红辉光描边 + 矩阵放大)+ 一行英文副标题。
 *
 * 备注:原版 logo 是像素贴图图集、无法塞中文,故标题只能用文字重画(本 mixin 即此法)。
 * 顶部横幅高度 80 是按常见分辨率估的;若某些 GUI 缩放下仍露出原 logo 或压到按钮,调 bannerH 即可。
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void yongye$darkTitle(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // 1. 全屏压暗:整体暗黑末日氛围(比上版更暗)
        ctx.fill(0, 0, w, h, 0x88000000);

        // 2. 顶部横幅:完全不透明,彻底盖死原版 MINECRAFT logo(上版半透明导致 logo 透出来很乱)
        int bannerH = 80;
        ctx.fill(0, 0, w, bannerH, 0xFF0A0306);                       // 主体:近黑暗红,不透明
        // 横幅下方做几段递减透明的渐变,平滑过渡到压暗层(避免一条硬边)
        ctx.fill(0, bannerH, w, bannerH + 4, 0xCC120006);
        ctx.fill(0, bannerH + 4, w, bannerH + 9, 0x77100005);
        ctx.fill(0, bannerH + 9, w, bannerH + 14, 0x33100005);
        // 血红下边线:把横幅边缘变成有意的设计而非穿帮
        ctx.fill(0, bannerH - 2, w, bannerH, 0xFF8B0000);

        // 3. 「永夜」大字:先画暗红辉光(四向偏移)再叠亮血红主体,矩阵放大、水平居中
        Text title = Text.literal("永夜").formatted(Formatting.BOLD);
        int titleW = mc.textRenderer.getWidth(title);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(w / 2.0, 16.0, 0.0);
        ctx.getMatrices().scale(5.0f, 5.0f, 1.0f);
        int cx = -titleW / 2;
        // 辉光:四周偏移的暗红,营造发光/血染描边
        ctx.drawText(mc.textRenderer, title, cx - 1, 0, 0xFF4A0000, false);
        ctx.drawText(mc.textRenderer, title, cx + 1, 0, 0xFF4A0000, false);
        ctx.drawText(mc.textRenderer, title, cx, -1, 0xFF4A0000, false);
        ctx.drawText(mc.textRenderer, title, cx, 1, 0xFF4A0000, false);
        // 主体:亮血红 + 阴影
        ctx.drawText(mc.textRenderer, title, cx, 0, 0xFFE01515, true);
        ctx.getMatrices().pop();

        // 4. 副标题:字距拉开的英文小字,低调灰
        Text sub = Text.literal("E T E R N A L   N I G H T").formatted(Formatting.BOLD);
        int subW = mc.textRenderer.getWidth(sub);
        ctx.drawText(mc.textRenderer, sub, w / 2 - subW / 2, 63, 0xFF888888, true);
    }
}
