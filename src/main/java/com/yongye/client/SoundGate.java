package com.yongye.client;

import com.yongye.YongyeConfig;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

import java.util.HashMap;
import java.util.Map;

/**
 * 音效并发管理器(m419,3A 打磨路线图第 27 项):客户端声音系统唯一入口
 * ({@code SoundSystem.play},由 {@code SoundGateMixin} 拦截)统一管流,三件事——
 * <ul>
 *   <li><b>同类音限流</b>:同一音效 id 在 soundSameIdWindowTicks 窗口内最多播
 *       soundSameIdMaxPerWindow 次,超出丢弃。AOE 一刀 20 只怪=20 声同款受击音同刻叠放,
 *       只加响度不加信息,留 2 声足够保住方位感;</li>
 *   <li><b>优先级</b>:音乐 / 唱片(音符盒机器也在此类,乐曲逐音符不许裁)/ 天气 / 语音 /
 *       MASTER(UI 点击)与循环音(isRepeatable,掐了头=整段永久缺失)永不限流也不占预算;
 *       其余类别共享全局每 tick 预算 soundGlobalMaxPerTick,先到先得;</li>
 *   <li><b>ducking(第 18 项挂账)</b>:重要提示音(BOSS 登场吼 / 讨伐凯旋 / 永夜转场 /
 *       多杀播报)拉起 soundDuckTicks 压制窗口,窗口内 AMBIENT 类环境氛围音直接让路——
 *       m388 音景的\"演出避让\"只避自家转场/讨伐,这里升级成对全部环境声普适。BOSS 登场吼
 *       由服务端直发无客户端挂点,故按音效 id(entity.wither.spawn)过闸时自动触发。</li>
 * </ul>
 * 统计打点进 FxStats.SOUND(m411 面板消费,\"细粒度丢弃待 24/27 顺路补\"在此清账);
 * 任何异常一律放行——管流器绝不能成为吞掉声音系统的单点故障。纯客户端零协议零服务端改动。
 */
public final class SoundGate {
    private SoundGate() {}

    private static long tick = 0;              // 客户端 tick 计数(END_CLIENT_TICK 推进)
    private static long lastPlayTick = -1;     // playsThisTick 归属的 tick
    private static int playsThisTick = 0;
    private static int duckTicks = 0;          // 剩余环境音压制窗口
    /** 音效 id → {窗口起始 tick, 窗口内已播次数}。 */
    private static final Map<String, long[]> WINDOWS = new HashMap<>();

    /** 每客户端 tick 调一次(YongyeClient 与 FxStats.tick 同挂点)。 */
    public static void tick() {
        tick++;
        if (duckTicks > 0) duckTicks--;
        if (WINDOWS.size() > 256) WINDOWS.clear();   // 防长会话膨胀;清空只损失≤1个窗口的记忆
    }

    /** 重要提示音起播时调:拉起环境音压制窗口(取更长,不叠加)。 */
    public static void duckPulse() {
        duckTicks = Math.max(duckTicks, Math.max(0, YongyeConfig.get().soundDuckTicks));
    }

    /** 压制窗口是否进行中(FxDebugHud 展示用)。 */
    public static boolean isDucking() { return duckTicks > 0; }

    /** SoundGateMixin 在 SoundSystem.play HEAD 调;返回 false=丢弃本次播放。 */
    public static boolean allow(SoundInstance s) {
        try {
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableSoundConcurrency) return true;
            SoundCategory cat = s.getCategory();
            if (cat == null || s.getId() == null) return true;
            // 优先级豁免:见类注释。循环音掐头=永久缺失(矿车/鞘翅风声),一律放行。
            if (cat == SoundCategory.MUSIC || cat == SoundCategory.RECORDS
                    || cat == SoundCategory.WEATHER || cat == SoundCategory.VOICE
                    || cat == SoundCategory.MASTER || s.isRepeatable()) {
                return true;
            }
            String id = s.getId().toString();
            // BOSS 登场吼(m263 服务端 playSoundToPlayer 直发):过闸即自动压环境,自身放行
            if ("minecraft:entity.wither.spawn".equals(id)) {
                duckPulse();
                FxStats.used(FxStats.SOUND);
                return true;
            }
            // ducking:压制窗口内环境氛围一类让路(重要提示音不被夜风盖住)
            if (duckTicks > 0 && cat == SoundCategory.AMBIENT) {
                FxStats.dropped(FxStats.SOUND);
                return false;
            }
            // 全局每 tick 预算:AOE 瞬间上百音源,超出人耳分辨全是纯噪音
            if (tick != lastPlayTick) { lastPlayTick = tick; playsThisTick = 0; }
            if (playsThisTick >= Math.max(4, c.soundGlobalMaxPerTick)) {
                FxStats.dropped(FxStats.SOUND);
                return false;
            }
            // 同类音限流
            int win = Math.max(1, c.soundSameIdWindowTicks);
            long[] w = WINDOWS.get(id);
            if (w == null || tick - w[0] >= win) {
                WINDOWS.put(id, new long[]{tick, 1});
            } else if (w[1] < Math.max(1, c.soundSameIdMaxPerWindow)) {
                w[1]++;
            } else {
                FxStats.dropped(FxStats.SOUND);
                return false;
            }
            playsThisTick++;
            FxStats.used(FxStats.SOUND);
            return true;
        } catch (Throwable t) {
            return true;   // 管流器任何意外都不许影响原声音系统
        }
    }
}
