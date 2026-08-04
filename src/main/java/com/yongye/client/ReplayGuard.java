package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Flashback 回放守卫(m456):判断客户端当前是否处于 Flashback 回放(含正在打开回放/导出渲染)。
 *
 * 病根:Flashback 回放的原理是把录制期间收到的全部 S2C 包**原样重放**给客户端(模组的
 * 自定义 payload 接收器照常触发,这也是模组 HUD/血条能在回放里正常显示的原因)——但
 * 「打开界面」类的包(猎杀勋章三选一 / 选职 / 选难度 / 调试菜单 / 守护书 / 任务书)在回放里
 * 会再弹一次屏,直接糊在宣传镜头上(作者实机:录宣传素材回放时弹出杀怪三选一)。
 *
 * 方案:Flashback 是纯客户端 mod、非本项目编译依赖,不能直接 import——反射探测
 * com.moulberry.flashback.Flashback#isInReplay()(类名/方法名/mod id 均照其 GitHub
 * 1.21 分支源码逐字核对:public static boolean isInReplay(),mod id = flashback)。
 * 未装 Flashback / 反射任何一步失败 → 永久短路返回 false(零影响不刷日志),
 * 失败只降级不崩(m254 player-animator 同款兜底口径)。
 *
 * 注意:只挡「回放播放」,不影响正常游玩,也不影响**录制时**的实时弹屏(录的时候是真在
 * 打真在选,理应弹;回放是给镜头看的,不该弹)。
 */
public final class ReplayGuard {
    private ReplayGuard() {}

    /** 0=未初始化 1=可用 -1=不可用(未装 Flashback 或反射失败,永久短路)。 */
    private static int state = 0;
    private static java.lang.reflect.Method isInReplay;

    /** 回放中且守卫开关开 → true(调用方应跳过弹屏)。 */
    public static boolean suppressPopups() {
        return YongyeConfig.get().replayGuardPopups && isReplayActive();
    }

    /** 当前是否处于 Flashback 回放(含正在打开回放)。未装/失败恒 false。 */
    public static boolean isReplayActive() {
        if (state == 0) init();
        if (state < 0) return false;
        try {
            return (Boolean) isInReplay.invoke(null);
        } catch (Throwable t) {
            state = -1;   // 运行期再出岔子也永久降级,不影响游戏
            return false;
        }
    }

    private static synchronized void init() {
        if (state != 0) return;
        try {
            if (!FabricLoader.getInstance().isModLoaded("flashback")) { state = -1; return; }
            Class<?> c = Class.forName("com.moulberry.flashback.Flashback");
            isInReplay = c.getMethod("isInReplay");
            state = 1;
        } catch (Throwable t) {
            state = -1;
        }
    }
}
