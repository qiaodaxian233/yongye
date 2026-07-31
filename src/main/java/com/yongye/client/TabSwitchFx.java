package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.Screen;

import java.util.WeakHashMap;

/**
 * 界面页签切换过渡(m391,3A 打磨路线图第 8 项):页签点击重建内容区时,整屏内容 120ms
 * 从切换方向轻移 14px 滑入(ease-out)+ 一层由暗到透的薄纱(峰值 ~35%,主题深蓝黑)——
 * 「啪一下换页」变成「翻过去」。零改布局零改逻辑,纯渲染层。
 *
 * <p><b>接线口径(与 m375 ScreenOpenFx 的关键区别,已核 Fabric 1.21.1 真源码):</b>
 * clearAndInit() 走的是无参 init(),而 Fabric 的 AFTER_INIT 只注入 init(MC,II) 与 resize
 * (ScreenMixin 两处 @Inject 逐字核过)——所以页签切换<b>无法</b>用 AFTER_INIT 统一探测,
 * 必须在各页签点击处显式调 {@link #trigger}(本轮接五处:DebugScreen / VisualFxScreen /
 * QuestBookScreen 顶部页签、VaultScreen 翻页、ClassSelectScreen 职业页签)。
 * 另一个官方行为:resize 的 beforeInit 会<b>清空实例级 screen 事件</b>(源码注释
 * "All elements are repopulated ... reinitialize all events"),故 register() 挂一个全局
 * AFTER_INIT(开屏与 resize 都触发)把该实例从 HOOKED 摘除,下次 trigger 重挂即自愈。
 *
 * <p><b>渲染:</b>beforeRender 给整屏矩阵 push+translate(位移随 ease 归零),afterRender
 * 先按 pushed 旗标无条件 pop(<b>配对只认旗标</b>,中途改配置/关质量档也不失衡),再画
 * 整屏薄纱。命中区按真实坐标(m369/m375 同取舍,120ms 内容偏移≤14px 可接受);背景压暗层
 * 同被平移=切换瞬间屏幕一侧有 14px 短暂浅带,被薄纱盖住基本不可见(取舍已知)。
 * enableTabSwitchFx 与 FxBudget.on() 双门;nanoTime 驱动到点必消(DoD);首次开屏不触发
 * (开场淡入归 ScreenOpenFx,两层职责不重叠)。
 *
 * <p><b>待编译验证(低险):</b>ScreenEvents.beforeRender(screen) 实例级事件仓库首用——
 * 与 m375 已编过的 afterRender 同类同签名族(官方源码五参 (screen,ctx,mouseX,mouseY,
 * tickDelta) 已逐字核对);报错删 beforeRender 段=只损失滑动、薄纱淡入照常。
 */
public final class TabSwitchFx {
    private TabSwitchFx() {}

    private static final long DUR_MS = 120;
    private static final float SLIDE_PX = 14f;
    /** 薄纱峰值 alpha(0~255,~35%;比开屏罩 140 淡得多,页签高频点击不闷)。 */
    private static final int PEAK_A = 90;

    private static final class State {
        long startNanos;                                   // 0=无过渡
        int dir;                                           // +1=从右滑入 / -1=从左 / 0=只淡
        boolean pushed;                                    // 本帧矩阵已 push(配对旗标)
    }

    /** 已挂 render 钩子的屏幕实例(弱引用,关屏即回收;resize 被官方清事件后摘除重挂)。 */
    private static final WeakHashMap<Screen, State> HOOKED = new WeakHashMap<>();

    /** 客户端初始化时挂(YongyeClient 调):只负责在开屏/resize(官方清实例事件的两个时点)
     *  摘除失效钩子记录,让下一次 trigger 重挂。 */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> HOOKED.remove(screen));
    }

    /** 页签点击处调(clearAndInit 前调,便于用旧页码算方向):dir 传「新页-旧页」即可,
     *  内部取符号(+右来/-左来/0 纯淡入)。双门不满足时为无害空操作。 */
    public static void trigger(Screen screen, int dir) {
        if (screen == null) return;
        if (!YongyeConfig.get().enableTabSwitchFx || !FxBudget.on()) return;
        State st = HOOKED.get(screen);
        if (st == null) {
            st = new State();
            HOOKED.put(screen, st);
            final State s = st;
            ScreenEvents.beforeRender(screen).register((scr, ctx, mx, my, delta) -> {
                float k = remain(s);
                if (k <= 0f || s.pushed) return;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(s.dir * SLIDE_PX * k, 0f, 0f);
                s.pushed = true;
            });
            ScreenEvents.afterRender(screen).register((scr, ctx, mx, my, delta) -> {
                if (s.pushed) { ctx.getMatrices().pop(); s.pushed = false; } // 配对只认旗标
                float k = remain(s);
                if (k <= 0f) return;
                int a = (int) (PEAK_A * k);
                if (a < 8) return;                                          // <0x08 强制不透明坑
                ctx.fill(0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(),
                        (a << 24) | 0x0A1220);                              // 与 ScreenOpenFx 同主题色
            });
        }
        st.dir = Integer.signum(dir);
        st.startNanos = System.nanoTime();
    }

    /** 过渡剩余量 1→0(ease-out 反相);双门中途关掉立即归零=「关开关零残留」口径。 */
    private static float remain(State s) {
        if (s.startNanos == 0) return 0f;
        if (!YongyeConfig.get().enableTabSwitchFx || !FxBudget.on()) { s.startNanos = 0; return 0f; }
        long age = (System.nanoTime() - s.startNanos) / 1_000_000L;
        if (age >= DUR_MS) { s.startNanos = 0; return 0f; }
        float t = age / (float) DUR_MS;
        float ease = 1f - (1f - t) * (1f - t);
        return 1f - ease;
    }
}
