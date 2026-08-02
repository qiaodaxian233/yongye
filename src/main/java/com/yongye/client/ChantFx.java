package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * 大招咏唱台词(m438,学 Celestisynth 的 chant message,MIT,已挂名 THIRD_PARTY_NOTICES)。
 *
 * <p><b>学到的点</b>:Celestisynth 每把武器放技能时会在准星附近甩一句短台词(「Firestarter」
 * 「HEAT.」),带 8 向描边、按时间淡出、颜色随武器走——文本量极小却把「这一下很重」的仪式感拉满,
 * 是整个模组辨识度最高的廉价特效。夜蚀本来就有说话的传统(m190 怪物嘲讽 / m226 肝帝台词),
 * 但七个职业的大招一直只有光晕没有声口,这一句正好补上。
 *
 * <p><b>与抄来的不同</b>:①它是<b>翻译键</b>写死在 lang 里,夜蚀改成**配置池**(照 m226 肝帝台词口径:
 * 竖线分隔随机抽、清空=该职业不出声),作者加梗不用改代码;②它画在准星左上,夜蚀画在**准星下方**
 * ——中屏上方 −70/−58/−44/−26 四个高度已经被讨伐字幕/多杀弹字/永夜转场占满(m418 安全区口径),
 * 下方是唯一常空区;③颜色直接复用 m407 的职业色表,不另立一套。
 *
 * <p>触发零新协议:挂在 {@link UltimateCastFx#onUltimateCast()} 同一个边沿上(SkillCdPayload 的
 * 「大招槽 CD 从 0 跳正」),光晕与台词天然同帧。时间驱动(nanoTime)到点必消,开背包/暂停不残留;
 * 同时最多一句,新的顶掉旧的。受 FxBudget.on() 与 enableChantMessage 双门;
 * 低刺激档不额外压制——这是文字信息不是闪光,压了反而看不清(m417 口径:装饰性脉冲才归低刺激管)。
 */
@Environment(EnvType.CLIENT)
public final class ChantFx {
    private ChantFx() {}

    private static final long LIFE_MS = 1400, IN_MS = 90, OUT_MS = 500;

    private static long startNanos = 0;
    private static String text = "";
    private static int color = 0xFFD780;

    /** m411 面板探针口径:台词是否在显示。 */
    static boolean isShowing() { return startNanos != 0; }

    /** 大招施放时调(UltimateCastFx 同一边沿)。职业 id 决定台词池与颜色。 */
    public static void onCast(String classId) {
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableChantMessage || !FxBudget.on()) return;
        String line = pick(poolOf(c, classId));
        if (line == null || line.isEmpty()) return;      // 该职业台词清空=不出声(m226 同款语义)
        text = line;
        color = UltimateCastFx.classColor(classId);      // m407 职业色表复用,不另立一套
        startNanos = System.nanoTime();
    }

    /** 按职业 id 取台词池(键与 m407/m423 的 id 口径一致——**不用中文名**,那是 m423 的老病根)。 */
    private static String poolOf(YongyeConfig c, String id) {
        return switch (id) {
            case "tank"      -> c.chantTank;
            case "warrior"   -> c.chantWarrior;
            case "warlock"   -> c.chantWarlock;
            case "swordsman" -> c.chantSwordsman;
            case "monk"      -> c.chantMonk;
            case "assassin"  -> c.chantAssassin;
            case "summoner"  -> c.chantSummoner;
            default -> "";
        };
    }

    /** 竖线分隔随机抽一句(照 m226 肝帝台词:空/全空白=不出声)。 */
    private static String pick(String pool) {
        if (pool == null || pool.isBlank()) return null;
        String[] arr = pool.split("\\|");
        int n = 0;
        for (String s : arr) if (!s.isBlank()) n++;
        if (n == 0) return null;
        int want = (int) (System.nanoTime() % n);
        for (String s : arr) {
            if (s.isBlank()) continue;
            if (want-- == 0) return s.trim();
        }
        return null;
    }

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (startNanos == 0) return;
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableChantMessage || !FxBudget.on()) { startNanos = 0; return; }
            long age = (System.nanoTime() - startNanos) / 1_000_000L;
            if (age >= LIFE_MS) { startNanos = 0; return; }        // 到点必消
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            var tr = mc.textRenderer;

            // 淡入 90ms → 驻留 → 末 500ms 淡出
            int a = 255;
            if (age < IN_MS) a = (int) (255 * age / (float) IN_MS);
            else if (age > LIFE_MS - OUT_MS) a = (int) (255 * (LIFE_MS - age) / (float) OUT_MS);
            a = Math.max(6, Math.min(255, a));

            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            // 准星下方(中屏上方 −70/−58/−44/−26 已被讨伐/多杀/转场占满);吃 m418 安全边距不出屏
            int y = h / 2 + 20 + FxBudget.safeY() / 2;
            Text t = Text.literal(text);
            int tw = tr.getWidth(text);
            int x = (w - tw) / 2;

            // 8 向描边(暗色)托底:无底框也读得清,亮背景不糊——学它的 drawInBatch8xOutline 观感,
            // 但用在树的 drawText 手搓八向(那个原版方法要 VertexConsumerProvider.Immediate 批渲,
            // HudRenderCallback 里另铺一套批渲不划算)
            int dark = (a << 24) | 0x0A0410;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    ctx.drawText(tr, t, x + dx, y + dy, dark, false);
                }
            }
            ctx.drawText(tr, t, x, y, (a << 24) | (color & 0xFFFFFF), false);
        });
    }
}
