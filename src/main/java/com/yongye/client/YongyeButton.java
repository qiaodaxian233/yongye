package com.yongye.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 永夜主题按钮:深色半透明玻璃蓝底 + 蓝青描边 + 悬停发光 + 顶部一道玻璃高光。
 * 用于背包左侧那一列功能按钮(成长/装备/饰品/天赋/强化/兑换/学书/本命),替代朴素的原版灰按钮。
 *
 * 实现:继承 ButtonWidget(沿用其点击/叙述逻辑),只重写 renderWidget 自绘外观,不动其它界面、零 mixin。
 * 想换配色只改下面几个常量即可。
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

    public YongyeButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x1 = getX(), y1 = getY(), x2 = getX() + getWidth(), y2 = getY() + getHeight();
        boolean hover = this.active && isHovered();

        // 底
        ctx.fill(x1, y1, x2, y2, !this.active ? BG_OFF : (hover ? BG_HOVER : BG));
        // 顶部玻璃高光(底色之上、描边之下,一道淡亮线)
        if (this.active) ctx.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, SHEEN);
        // 描边(四边各 1px)
        int border = !this.active ? 0xFF2A2A33 : (hover ? BORDER_HOVER : BORDER);
        ctx.fill(x1, y1, x2, y1 + 1, border);
        ctx.fill(x1, y2 - 1, x2, y2, border);
        ctx.fill(x1, y1, x1 + 1, y2, border);
        ctx.fill(x2 - 1, y1, x2, y2, border);

        // 文字居中
        int tc = !this.active ? TEXT_OFF : (hover ? TEXT_HOVER : TEXT);
        ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, tc);
    }
}
