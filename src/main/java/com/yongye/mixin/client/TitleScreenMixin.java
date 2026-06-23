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
 * 思路(最稳):render 的 TAIL 只叠加绘制「永夜」标题文字,不取消原版任何渲染流程,版本兼容性最好。
 * 演进:
 *   - m79/m80:全屏压暗 + 顶部黑红横幅(盖原版 logo)+「永夜」大字。
 *   - m123:用户做了暗黑全景图作背景,去掉全屏压暗让全景显出。
 *   - m125:用户要求去掉顶部黑红横幅。横幅原是用来盖原版 MINECRAFT logo 的;现改为用**透明贴图**
 *     覆盖原版 logo 与 Java Edition 副标(assets/minecraft/textures/gui/title/minecraft.png · edition.png),
 *     logo 直接不可见,于是横幅不再需要。最终只剩「永夜」大字 + 英文副标浮在全景图上。
 *
 * 备注:原版 logo 是像素贴图、无法塞中文,故标题用文字重画(本 mixin) + 透明贴图隐藏原 logo。
 * 「永夜」大字默认在屏幕顶部(translate y=16, scale 5x);若位置要调,改下面 translate 的 y 即可。
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void yongye$darkTitle(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth();

        // 1. (m123 改)不再全屏压暗:用户已做暗黑全景图作主页背景,再叠 53% 黑会把全景压成死黑。
        //    全景本身平均亮度仅约 40/255,足够暗;原版按钮自带半透底+白字,直接铺在全景上仍清晰可读。
        //    如需为按钮区再加一点可读性,可在底部叠一条很淡的渐变,这里先全去掉。

        // 2. (m125 改)去掉顶部黑红横幅(用户要求)。横幅原本是用来盖住原版 MINECRAFT logo 的;
        //    现改为用透明贴图覆盖原版 logo(assets/minecraft/textures/gui/title/minecraft.png · edition.png),
        //    logo 直接不可见,于是横幅就不需要了——全景图顶部(天空/闪电)得以完整显示,「永夜」大字直接浮在全景上。

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
