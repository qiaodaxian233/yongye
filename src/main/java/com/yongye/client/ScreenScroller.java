package com.yongye.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * 整页滚动器(m422,作者:「设置显示不全,应该自动识别界面尺寸 或者做一个滚动条」)。
 * 给"从上往下摆一列控件"的设置类界面(VisualFxScreen / DebugScreen)补溢出处理:
 * 内容总高超过窗口逻辑高(GUI 缩放大 / 窗口矮)时,标题·页签·按钮·返回钮**整页一起上移**
 * ——没有任何固定元素,天然互不遮挡,不需要 scissor 裁剪;右缘画滚动条,
 * 支持 滚轮 / 拖动拖块 / 点击轨道跳转 三种滚法;内容装得下时完全不出现,行为与旧版零差别。
 *
 * 用法(宿主 Screen):
 *   init() 开头 begin();每个 addDrawableChild 的控件再 reg(w)(宿主可包一个 addS 助手);
 *   末尾 finish(内容总底y, this.height)——clearAndInit 重建时滚动位保留并自动钳制
 *   (数值页回车刷新不丢位置),切页签属于换内容,请先 reset();
 *   render 里把自绘文本(大标题/分组头)的 y 减去 offset(),末尾 renderBar(ctx, w, h) 画条;
 *   鼠标四事件转发给 mouseScrolled / mouseClicked / mouseDragged / mouseReleased。
 *
 * API 面(全部已核):Widget.setY/getY = yarn 1.21.1 method_46419/46427(getX/getY 在树,
 * ClassSelectScreen);Element 四鼠标事件签名 = mouseScrolled(DDDD)Z method_25401 /
 * mouseClicked(DDI)Z method_25402(在树多处)/ mouseDragged(DDIDD)Z method_25403 /
 * mouseReleased(DDI)Z method_25406;DrawContext.fill 在树满仓。
 */
@Environment(EnvType.CLIENT)
public class ScreenScroller {

    /** 每滚轮格滚动像素(约两行按钮:BTN_H 18 + GAP_Y 3 的两倍)。 */
    private static final int WHEEL_STEP = 42;
    /** 滚动条:轨道宽 / 距右缘 / 点击判定带宽 / 拖块最小高。 */
    private static final int BAR_W = 4;
    private static final int BAR_MARGIN = 2;
    private static final int HIT_BAND = 10;
    private static final int THUMB_MIN_H = 20;

    private final List<ClickableWidget> widgets = new ArrayList<>();
    private int scroll = 0;        // 当前滚动量 px(内容整体上移了多少)
    private int maxScroll = 0;     // 可滚上限(0=装得下,滚动器整体休眠)
    private int contentBottom = 0; // 内容总底(未滚动坐标系,用于算拖块高)
    private int viewH = 0;         // 窗口逻辑高
    private boolean draggingBar = false;

    /** init() 开头调:清空控件登记(滚动位故意保留,finish 时钳制)。 */
    public void begin() {
        widgets.clear();
        draggingBar = false;
    }

    /** 登记一个随页滚动的控件(加进 Screen 后、还在"未滚动"坐标时调)。 */
    public void reg(ClickableWidget w) {
        widgets.add(w);
    }

    /** init() 末尾调:contentBottomY = 最底控件下缘+边距;把保留的滚动位一次性应用到全部控件。 */
    public void finish(int contentBottomY, int screenHeight) {
        this.contentBottom = contentBottomY;
        this.viewH = screenHeight;
        this.maxScroll = Math.max(0, contentBottomY - screenHeight);
        this.scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (scroll != 0) {
            for (ClickableWidget w : widgets) w.setY(w.getY() - scroll);
        }
    }

    /** 换页签/换内容时调:回到顶部(避免带着旧滚动位看新页开头被吞)。 */
    public void reset() {
        scroll = 0;
    }

    /** 当前滚动量(宿主自绘文本的 y 请减去它)。 */
    public int offset() {
        return scroll;
    }

    /** 滚 delta px(正=内容上移),越界钳制;返回是否属于本滚动器管(有溢出即算,防事件漏底)。 */
    private boolean scrollBy(int delta) {
        if (maxScroll <= 0) return false;
        int ns = Math.max(0, Math.min(scroll + delta, maxScroll));
        int diff = ns - scroll;
        if (diff != 0) {
            for (ClickableWidget w : widgets) w.setY(w.getY() - diff);
            scroll = ns;
        }
        return true;
    }

    /** 滚轮(宿主先让 super 尝试给子控件,没人吃再进这里)。 */
    public boolean mouseScrolled(double verticalAmount) {
        return scrollBy((int) Math.round(-verticalAmount * WHEEL_STEP));
    }

    /** 左键按下:点中右缘判定带 = 开始拖动并跳到该处(判定带比可见轨道宽,好点中)。 */
    public boolean mouseClicked(double mouseX, double mouseY, int screenWidth) {
        if (maxScroll <= 0 || mouseX < screenWidth - HIT_BAND) return false;
        draggingBar = true;
        jumpTo(mouseY);
        return true;
    }

    /** 左键拖动:拖块跟手(仅当拖动始于轨道)。 */
    public boolean mouseDragged(double mouseY) {
        if (!draggingBar) return false;
        jumpTo(mouseY);
        return true;
    }

    /** 左键抬起:结束拖动(宿主无条件转发,幂等)。 */
    public void mouseReleased() {
        draggingBar = false;
    }

    /** 鼠标 y → 滚动位(以拖块中心对齐鼠标,轨道两端各留半块)。 */
    private void jumpTo(double mouseY) {
        int th = thumbH();
        double t = (mouseY - th / 2.0) / Math.max(1, viewH - th);
        scrollBy((int) Math.round(Math.max(0, Math.min(1, t)) * maxScroll) - scroll);
    }

    /** 拖块高:视口占内容比,封最小高防内容极长时块小到点不中。 */
    private int thumbH() {
        return Math.max(THUMB_MIN_H, (int) ((long) viewH * viewH / Math.max(1, contentBottom)));
    }

    /** render 末尾调:仅溢出时画右缘轨道+拖块(拖动中金色高亮,平时灰蓝半透)。 */
    public void renderBar(DrawContext ctx, int screenWidth, int screenHeight) {
        if (maxScroll <= 0) return;
        int x = screenWidth - BAR_W - BAR_MARGIN;
        ctx.fill(x, 0, x + BAR_W, screenHeight, 0x66000000);
        int th = thumbH();
        int ty = (int) ((long) (screenHeight - th) * scroll / Math.max(1, maxScroll));
        ctx.fill(x, ty, x + BAR_W, ty + th, draggingBar ? 0xFFFFD700 : 0xCC8FA3B8);
    }
}
