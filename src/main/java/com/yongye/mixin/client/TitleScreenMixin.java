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
 * 主菜单「夜蚀」暗黑化。
 *
 * 思路(最稳):render 的 TAIL 只叠加绘制「夜蚀」标题文字,不取消原版任何渲染流程,版本兼容性最好。
 * 演进:
 *   - m79/m80:全屏压暗 + 顶部黑红横幅(盖原版 logo)+「夜蚀」大字。
 *   - m123:用户做了暗黑全景图作背景,去掉全屏压暗让全景显出。
 *   - m125:用户要求去掉顶部黑红横幅。横幅原是用来盖原版 MINECRAFT logo 的;现改为用**透明贴图**
 *     覆盖原版 logo 与 Java Edition 副标(assets/minecraft/textures/gui/title/minecraft.png · edition.png),
 *     logo 直接不可见,于是横幅不再需要。最终只剩「夜蚀」大字 + 英文副标浮在全景图上。
 *
 * 备注:原版 logo 是像素贴图、无法塞中文,故标题用文字重画(本 mixin) + 透明贴图隐藏原 logo。
 * 「夜蚀」大字默认在屏幕顶部(translate y=16, scale 5x);若位置要调,改下面 translate 的 y 即可。
 *
 * m408(路线图19)主菜单动效:
 *   - 标题呼吸辉光:3s 正弦周期,辉光层在暗红(0x4A0000)↔血红(0x8C0808)间摆、主体亮度微摆(±8%),
 *     并随呼吸给辉光多叠一圈远偏移(呼吸峰时描边显得"涨开"),纯时间驱动(nanoTime)零状态残留;
 *   - 入场淡入:打开主菜单头 600ms 标题/副标从透明浮入(alpha 0→255 ease-out + 标题 y 从 -6 落到位),
 *     首次进屏 nanoTime 记在静态字段,离屏(init 再触发)重置——切回主菜单再淡一次,观感统一;
 *   - 全走 enableTitleFx 配置(默认开,关=回 m125 静态观感);alpha 恒显式,呼吸不吃 reduceScreenFlash
 *     (亮度摆动 ±8% 远低于闪光阈,评审 25 号低刺激档扩展时再统一收编)。
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    /** m408 入场时刻(静态:TitleScreen 每次构造都是新实例,用 init 注入点重置)。 */
    private static long yongye$enterNanos = 0;

    @Inject(method = "init", at = @At("HEAD"))
    private void yongye$markEnter(CallbackInfo ci) {
        yongye$enterNanos = System.nanoTime();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void yongye$darkTitle(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth();

        // 1. (m123 改)不再全屏压暗:用户已做暗黑全景图作主页背景,再叠 53% 黑会把全景压成死黑。
        //    全景本身平均亮度仅约 40/255,足够暗;原版按钮自带半透底+白字,直接铺在全景上仍清晰可读。
        //    如需为按钮区再加一点可读性,可在底部叠一条很淡的渐变,这里先全去掉。

        // 2. (m125 改)去掉顶部黑红横幅(用户要求)。横幅原本是用来盖住原版 MINECRAFT logo 的;
        //    现改为用透明贴图覆盖原版 logo(assets/minecraft/textures/gui/title/minecraft.png · edition.png),
        //    logo 直接不可见,于是横幅就不需要了——全景图顶部(天空/闪电)得以完整显示,「夜蚀」大字直接浮在全景上。

        // 3. 「夜蚀」大字:先画暗红辉光(四向偏移)再叠亮血红主体,矩阵放大、水平居中
        //    m408:呼吸辉光(3s 正弦)+ 入场 600ms 淡入;enableTitleFx 关=回 m125 静态观感
        boolean fx = com.yongye.YongyeConfig.get().enableTitleFx;
        long now = System.nanoTime();
        float in = 1f;      // 入场进度(0→1,ease-out)
        float breath = 0f;  // 呼吸相位(-1..1)
        if (fx) {
            if (yongye$enterNanos == 0) yongye$enterNanos = now;
            float e = Math.min(1f, (now - yongye$enterNanos) / 600_000_000f);
            in = 1f - (1f - e) * (1f - e);
            if (com.yongye.client.FxBudget.pulseOn()) {   // m417 低刺激整档=标题不呼吸(淡入保留)
                breath = (float) Math.sin((now % 3_000_000_000L) / 3_000_000_000.0 * Math.PI * 2);
            }
        }
        int inA = Math.max(8, (int) (255 * in));                     // 淡入 alpha(≥8:MC 文本 alpha<0x04 强制不透明)
        float dropY = fx ? -6f * (1f - in) : 0f;                     // 入场标题从上方 6px 落到位

        Text title = Text.literal("夜蚀").formatted(Formatting.BOLD);
        int titleW = mc.textRenderer.getWidth(title);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(w / 2.0, 16.0 + dropY, 0.0);
        ctx.getMatrices().scale(5.0f, 5.0f, 1.0f);
        int cx = -titleW / 2;
        // 辉光:四周偏移,颜色随呼吸在暗红↔血红间摆
        int glowRgb = yongye$mix(0x4A0000, 0x8C0808, (breath + 1f) / 2f);
        int glow = (inA << 24) | glowRgb;
        ctx.drawText(mc.textRenderer, title, cx - 1, 0, glow, false);
        ctx.drawText(mc.textRenderer, title, cx + 1, 0, glow, false);
        ctx.drawText(mc.textRenderer, title, cx, -1, glow, false);
        ctx.drawText(mc.textRenderer, title, cx, 1, glow, false);
        if (fx && breath > 0) {                                      // 呼吸峰:再叠一圈远偏移,描边"涨开"
            int outer = ((int) (inA * 0.45f * breath) << 24) | glowRgb;
            if ((outer >>> 24) > 3) {
                ctx.drawText(mc.textRenderer, title, cx - 2, 0, outer, false);
                ctx.drawText(mc.textRenderer, title, cx + 2, 0, outer, false);
                ctx.drawText(mc.textRenderer, title, cx, -2, outer, false);
                ctx.drawText(mc.textRenderer, title, cx, 2, outer, false);
            }
        }
        // 主体:亮血红 + 阴影,亮度随呼吸 ±8% 微摆
        int bodyRgb = fx ? yongye$mix(0xCE1313, 0xF21717, (breath + 1f) / 2f) : 0xE01515;
        ctx.drawText(mc.textRenderer, title, cx, 0, (inA << 24) | bodyRgb, true);
        ctx.getMatrices().pop();

        // 4. 副标题:字距拉开的英文小字,低调灰(跟随淡入)
        Text sub = Text.literal("N I G H T B L I G H T").formatted(Formatting.BOLD);
        int subW = mc.textRenderer.getWidth(sub);
        ctx.drawText(mc.textRenderer, sub, w / 2 - subW / 2, 63, (inA << 24) | 0x888888, true);
    }

    /** RGB 线性插值(t 0..1;m393 mixColor 同手法,mixin 包内自带一份避免跨包拖依赖)。 */
    private static int yongye$mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
