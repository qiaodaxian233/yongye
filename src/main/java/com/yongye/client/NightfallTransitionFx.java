package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * 永夜升级/消退转场演出(m380,3A 打磨路线图第 6 项,按 m379 专项验收卡六边界实现):
 * 永夜等级发生<b>真实变化</b>的瞬间——
 * <ul>
 *   <li>升级:整屏压暗(深红黑,峰值 45%×强度)+ 低音心跳 ×2 + 阶段名血红大字幕
 *       (「☽ 永夜降临」小字导语),1.8 秒;</li>
 *   <li>降级(赎夜):柔和金色微光(峰值 18%×强度)+ 清铃音 + 金字短字幕「永夜消退」,
 *       1.2 秒——刻意做成好事的观感,不与升级混用。</li>
 * </ul>
 *
 * <p><b>六边界的实现口径:</b>
 * <ol>
 *   <li>真实变化才播:客户端持有等级基线 lastLevel,与 NightfallSyncPayload 比对,同值同步不播;</li>
 *   <li>首次同步/重登/维度切换不播:每客户端 tick 检查 mc.world <b>引用</b>是否变化
 *       (换维度/重登/退到主菜单世界对象必换),变了就把基线重置为「未知(-1)」,
 *       未知状态的下一次同步只记账不播;</li>
 *   <li>跨级合并:客户端只见同步终值,1 直升 5 天然只播一次「灭世」;</li>
 *   <li>暂停/开界面不残留:演出纯 System.nanoTime 时间驱动,每帧按年龄算 alpha,
 *       到点必消,不依赖任何事件收尾;</li>
 *   <li>GUI 缩放安全:全部坐标出自 ctx.getScaledWindowWidth/Height,大字用矩阵缩放居中;</li>
 *   <li>低刺激:enableNightfallTransition 总开关 / transitionIntensity 强度倍率(0~2)/
 *       reduceScreenFlash(全局弱闪光,压暗与微光减半——后续所有闪光类 FX 均须查询此项)。</li>
 * </ol>
 * 性能预算:全屏演出同时最多 1 个,新来的直接替换旧的,不叠加。
 * 零新 API 面:HudRenderCallback/ClientTickEvents/playSound/矩阵缩放画字全在树
 * (心跳音 m287、铃音 m232 起已随构建编过)。
 */
public final class NightfallTransitionFx {
    private NightfallTransitionFx() {}

    private static final long UP_MS = 1800, DOWN_MS = 1200;

    /** 等级基线:-1=未知(首次同步只记账);世界引用变化时重置回 -1。 */
    private static int lastLevel = -1;
    private static Object lastWorldRef = null;

    // 当前演出(同时最多 1 个,新来替换)
    private static long bornNanos = 0;
    private static boolean playing = false, upgrade = true;
    private static String stageName = "";

    /** 客户端初始化时挂(YongyeClient 调)。 */
    public static void register() {
        // 边界 2:世界引用变化(重登/换维度/退主菜单)→ 基线归未知,顺手掐掉跨世界残留演出
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world != lastWorldRef) {
                lastWorldRef = mc.world;
                lastLevel = -1;
                playing = false;
            }
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (!playing) return;
            long ageMs = (System.nanoTime() - bornNanos) / 1_000_000L;
            long dur = upgrade ? UP_MS : DOWN_MS;
            if (ageMs >= dur) { playing = false; return; }   // 边界 4:到点必消

            YongyeConfig c = YongyeConfig.get();
            float inten = (float) Math.max(0.0, Math.min(2.0, c.transitionIntensity));
            if (inten <= 0f) { playing = false; return; }
            float flashMul = FxBudget.flashScale();                // m417 闪光中枢

            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            MinecraftClient mc = MinecraftClient.getInstance();

            // —— 整屏罩:起 350ms 升到峰值 → 持稳 → 末 40% 时长渐出 ——
            float peak = (upgrade ? 115f : 46f) * inten * flashMul;      // 45% / 18%
            float env;
            long fadeStart = dur * 3 / 5;
            if (ageMs < 350)          env = easeOut(ageMs / 350f);
            else if (ageMs < fadeStart) env = 1f;
            else                      env = 1f - (ageMs - fadeStart) / (float) (dur - fadeStart);
            int a = Math.min(255, Math.max(0, (int) (peak * env)));
            if (a >= 8) {
                int rgb = upgrade ? 0x1A0000 : 0x2A2008;                 // 深红黑 / 暗金
                ctx.fill(0, 0, w, h, (a << 24) | rgb);
            }

            // —— 字幕:250ms 淡入,尾 300ms 淡出 ——
            float tA;
            if (ageMs < 250)               tA = ageMs / 250f;
            else if (ageMs > dur - 300)    tA = (dur - ageMs) / 300f;
            else                           tA = 1f;
            int textA = Math.max(8, Math.min(255, (int) (255 * tA)));

            String lead = upgrade ? "☽ 永夜降临" : "☾ 永夜消退";
            int leadCol = (textA << 24) | (upgrade ? 0xC97A6A : 0xC9B87A);
            int nameCol = (textA << 24) | (upgrade ? 0xFF5548 : 0xFFD873);
            var tr = mc.textRenderer;

            int cy = h / 2 - 26;                                          // 中偏上,不压准星
            ctx.drawCenteredTextWithShadow(tr, Text.literal(lead), w / 2, cy, leadCol);

            float scale = 2.2f;                                           // 阶段名大字(矩阵缩放,GUI 缩放安全)
            Text name = Text.literal(stageName);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, cy + 14, 0);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.drawCenteredTextWithShadow(tr, name, 0, 0, nameCol);
            ctx.getMatrices().pop();
        });
    }

    /** m411 fxtest 直触:强制播一场转场,**不碰 lastLevel 基线**——下一次真实同步照常按基线走。 */
    public static void testPlay(String name, boolean up) {
        if (!YongyeConfig.get().enableNightfallTransition || !FxBudget.on()) return;
        stageName = name == null || name.isEmpty() ? "测试阶段" : name;
        upgrade = up;
        bornNanos = System.nanoTime();
        playing = true;
    }

    /** NightfallSyncPayload 接收处调(主线程):比对基线,真实变化才起演出。 */
    public static void onLevelSync(int level, String name) {
        if (lastLevel == -1) { lastLevel = level; return; }   // 边界 2:首同步只记账
        if (level == lastLevel) return;                        // 边界 1:同值不播
        boolean up = level > lastLevel;
        lastLevel = level;                                     // 边界 3:终值记账,跨级天然合并

        YongyeConfig c = YongyeConfig.get();
        if (!c.enableNightfallTransition || !FxBudget.on()) return; // m381 OFF 档让位
        float inten = (float) Math.max(0.0, Math.min(2.0, c.transitionIntensity));
        if (inten <= 0f) return;

        playing = true;                                        // 替换式:同时最多 1 个
        upgrade = up;
        stageName = name == null ? "" : name;
        bornNanos = System.nanoTime();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            if (up) {
                mc.player.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.9f * inten, 0.55f);
            } else {
                mc.player.playSound(SoundEvents.BLOCK_BELL_RESONATE, 0.6f * inten, 1.25f);
            }
        }
    }

    /** m388 音景避让探针:转场演出是否进行中。 */
    static boolean isPlaying() { return playing; }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t); }
}
