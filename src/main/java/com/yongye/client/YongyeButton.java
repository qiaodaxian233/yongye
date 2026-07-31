package com.yongye.client;

import com.yongye.YongyeConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 夜蚀主题按钮:深色半透明玻璃蓝底 + 蓝青描边 + 悬停发光 + 顶部一道玻璃高光。
 * 用于背包左侧那一列功能按钮(成长/装备/饰品/天赋/强化/兑换/学书/本命),替代朴素的原版灰按钮。
 *
 * 实现:继承 ButtonWidget(沿用其点击/叙述/点击音逻辑),只重写 renderWidget 自绘外观,不动其它界面、零 mixin。
 * 想换配色只改下面几个常量即可。
 *
 * m375(3A 打磨第 3 项)UI 动效三件套(enableUiFx 可整体关回旧静态观感):
 * <ul>
 *   <li>悬停过渡:进/出悬停时底色/描边/文字 110ms 线性插值渐变,不再硬切;</li>
 *   <li>按压反馈:按下瞬间内容下沉 1px + 底色压暗 90ms(点击音沿用 ButtonWidget 原版统一音);</li>
 *   <li>入场动效:构造后前 150ms 从下方 5px 上浮 + 淡入(ease-out)——所有走 clearAndInit 重建
 *       按钮的界面(背包列/设置页签切换)自动吃到开场动效,零逐界面接线。</li>
 * </ul>
 * 三者皆纯视觉:命中区/点击判定始终按真实坐标(m369 悬停上浮同一取舍,防命中抖动)。
 */
public class YongyeButton extends ButtonWidget {

    // 配色(ARGB)。想改风格(比如改成血红主题)动这里即可。
    private static final int BG          = 0xB0142036; // 常态:深海军蓝半透明
    private static final int BG_HOVER    = 0xCC1E3A66; // 悬停:略亮
    private static final int BG_OFF      = 0x80101018; // 禁用:更暗
    private static final int BORDER      = 0xFF3A6EA5; // 常态描边:中蓝
    private static final int BORDER_HOVER= 0xFF6FD0FF; // 悬停描边:亮青(发光感)
    private static final int SHEEN       = 0x40BFE6FF; // 顶部玻璃高光
    private static final int TEXT        = 0xFFCFE6FF; // 常态文字:淡蓝白
    private static final int TEXT_HOVER  = 0xFFFFFFFF; // 悬停文字:纯白
    private static final int TEXT_OFF    = 0xFF6A6A78; // 禁用文字:灰

    // m375 动效时长(毫秒)
    private static final long HOVER_MS = 110, PRESS_MS = 90, ENTER_MS = 150;

    private final long bornNanos = System.nanoTime(); // 入场动效计时(构造即开)
    private float hoverT = 0f;                        // 悬停插值进度 0(常态)~1(悬停)
    private long lastFrameNanos = bornNanos;          // 悬停插值用帧间隔
    private long pressNanos = 0L;                     // 最近一次按下时刻(0=没按过)

    public YongyeButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    public void onPress() {
        pressNanos = System.nanoTime(); // 记按压时刻(下沉闪),再走原动作
        super.onPress();
    }

    /** 线性插值 ARGB(逐通道,含 alpha)。 */
    private static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24), ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24), br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** 颜色乘 alpha 系数(0~1;结果 alpha 钳 ≥8 防 MC 文本 <0x04 强制不透明的怪相)。 */
    private static int mulAlpha(int argb, float f) {
        int a = Math.max(8, Math.min(255, (int) ((argb >>> 24) * f)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean fx = YongyeConfig.get().enableUiFx;
        long now = System.nanoTime();
        boolean hover = this.active && isHovered();

        // —— 悬停插值(实时时间推进,与帧率无关;关动效=硬切 0/1 回旧观感)——
        if (fx) {
            float step = (now - lastFrameNanos) / 1_000_000f / HOVER_MS;
            hoverT = Math.max(0f, Math.min(1f, hoverT + (hover ? step : -step)));
        } else {
            hoverT = hover ? 1f : 0f;
        }
        lastFrameNanos = now;

        // —— 入场:前 150ms 从下方 5px 上浮 + 淡入(ease-out;纯视觉,命中区不动)——
        float enterA = 1f;
        int dy = 0;
        if (fx) {
            long age = (now - bornNanos) / 1_000_000L;
            if (age < ENTER_MS) {
                float t = age / (float) ENTER_MS;
                float ease = 1f - (1f - t) * (1f - t);
                enterA = 0.15f + 0.85f * ease;
                dy = Math.round(5f * (1f - ease));
            }
        }

        // —— 按压:按下后 90ms 内容再下沉 1px + 底色压暗 ——
        boolean pressed = fx && pressNanos > 0 && (now - pressNanos) / 1_000_000L < PRESS_MS;
        if (pressed) dy += 1;

        int x1 = getX(), y1 = getY() + dy, x2 = getX() + getWidth(), y2 = getY() + getHeight() + dy;

        // 底(悬停插值;按压压暗)
        int bg = !this.active ? BG_OFF : lerpColor(BG, BG_HOVER, hoverT);
        if (pressed) bg = lerpColor(bg, 0xE00C1626, 0.5f);
        ctx.fill(x1, y1, x2, y2, mulAlpha(bg, enterA));
        // 顶部玻璃高光(底色之上、描边之下,一道淡亮线;按压时熄灭=失去受光面)
        if (this.active && !pressed) ctx.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, mulAlpha(SHEEN, enterA));
        // 描边(四边各 1px,悬停插值出渐亮发光)
        int border = !this.active ? 0xFF2A2A33 : lerpColor(BORDER, BORDER_HOVER, hoverT);
        border = mulAlpha(border, enterA);
        ctx.fill(x1, y1, x2, y1 + 1, border);
        ctx.fill(x1, y2 - 1, x2, y2, border);
        ctx.fill(x1, y1, x1 + 1, y2, border);
        ctx.fill(x2 - 1, y1, x2, y2, border);

        // 文字居中(悬停插值)
        int tc = !this.active ? TEXT_OFF : lerpColor(TEXT, TEXT_HOVER, hoverT);
        ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                getX() + getWidth() / 2, y1 + (getHeight() - 8) / 2, mulAlpha(tc, enterA));
    }
}
