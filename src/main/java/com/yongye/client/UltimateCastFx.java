package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * 大招起手屏幕边缘职业色光晕(m407,3A 路线图第 17 项)。
 * 触发:零新协议——SkillCdPayload 收包处抓「大招槽 CD 从 0 跳正」边沿(=刚施放),
 * 首包只播种不触发(重登/换世界带着半截 CD 回来不算起手,m380 六边界同思路)。
 * 观感:屏幕四边向内渐隐的职业色光带,80ms 冲到峰值 → 余程淡出,总时长 0.4s(FxBudget 短寿可缩);
 * 峰值透明度吃 skillCastFxIntensity 倍率,reduceScreenFlash 开=峰值减半(全局弱闪光铁律);
 * 同时最多 1 个,新触发覆盖旧的(全屏叠层预算,m379 评审口径)。fillGradient 在树(勋章屏先例)。
 */
@Environment(EnvType.CLIENT)
public final class UltimateCastFx {
    private UltimateCastFx() {}

    private static final long LIFE_MS = 400, IN_MS = 80;

    private static long startNanos = 0;
    private static int color = 0xFFD780;

    /** 职业色(键=PlayerClass **id**,ClientStats.className 同口径;没配到=暖金兜底)。
     *  m423 修:原键是中文名而 className 传的是 id("summoner"等)永不命中,
     *  大招光晕一直走暖金兜底不分职业——与干弟入口同根病(cn↔id 口径混用),一并清账。 */
    static int classColor(String id) {   // m438:放宽包内可见,咏唱台词复用同一张职业色表
        return switch (id) {
            case "tank"      -> 0x4C8DFF; // 肉盾·钢蓝
            case "warrior"   -> 0xFF7A2E; // 战士·炽橙
            case "warlock"   -> 0xB05CFF; // 术士·紫
            case "swordsman" -> 0x3EE6D0; // 剑客·青
            case "monk"      -> 0xFFCC33; // 武僧·金
            case "assassin"  -> 0xFF4560; // 刺客·绯红
            case "summoner"  -> 0x59D96A; // 召唤师·翠绿
            default -> 0xFFD780;
        };
    }

    /** 大招施放边沿(YongyeClient 的 SkillCdPayload 收包处调)。 */
    public static void onUltimateCast() {
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableSkillCastFx || !FxBudget.on()) { FxStats.dropped(FxStats.OVERLAY); return; } // m427 记丢弃
        if (startNanos != 0) FxStats.dropped(FxStats.OVERLAY); // m427:覆盖旧光晕=旧的记丢弃
        FxStats.used(FxStats.OVERLAY);
        color = classColor(ClientStats.className);
        startNanos = System.nanoTime();               // 覆盖旧的:同时最多 1 个
        ChantFx.onCast(ClientStats.className);        // m438 咏唱台词同帧甩出(零新协议,共用本边沿)
        SwordRainFx.onCast(ClientStats.className);    // m448 万剑归一幻影剑群(剑客限定,拍点取自作者 bbmodel)
        if ("swordsman".equals(ClientStats.className)) {
            // m448「这动作没有」另一半:放大招时本地玩家甩一记居合(m254 七式之第 6 式,蓄而后发的气口最像「归一」)
            MinecraftClient mc2 = MinecraftClient.getInstance();
            if (mc2.player != null) SlashAnimManager.playFor(mc2.player, 6); // 第 6 式=居合(SlashFxManager V_IAI 私有常量,此处字面量+注释)
        }
    }

    /** m411 调试面板探针:光晕是否进行中。 */
    static boolean isActive() { return startNanos != 0; }

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (startNanos == 0) return;
            long age = (System.nanoTime() - startNanos) / 1_000_000L;
            long life = Math.max(150, FxBudget.scaleLife(LIFE_MS));
            if (age >= life) { startNanos = 0; return; }
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableSkillCastFx) { startNanos = 0; return; }

            // 包络:80ms 线性冲峰 → 余程二次淡出
            float env = age < IN_MS ? age / (float) IN_MS
                    : 1f - (age - IN_MS) / (float) (life - IN_MS);
            env = Math.max(0f, Math.min(1f, env)) * env;   // 平方=收尾更柔
            double inten = Math.max(0, Math.min(2.0, c.skillCastFxIntensity));
            inten *= FxBudget.flashScale();                 // m417 闪光中枢(弱闪光×0.5/低刺激×0.25)
            int peak = (int) (0x5A * env * inten);
            if (peak <= 3) return;                          // alpha<0x04 会被文本管线当不透明,叠层同样别贴地

            MinecraftClient mc = MinecraftClient.getInstance();
            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            int t = Math.max(18, Math.min(w, h) / 6);       // 光带厚度
            int a = (peak << 24) | color, z = color & 0xFFFFFF; // z=同色全透明端(显式 alpha=0)
            ctx.fillGradient(0, 0, w, t, a, z);             // 上
            ctx.fillGradient(0, h - t, w, h, z, a);         // 下
            // 左右:fillGradient 仅纵向渐变,横向衰减用两层阶梯 fill 近似(内窄浓/外宽淡,零新 API)
            int s1 = Math.max(4, t / 4), s2 = Math.max(8, t / 2);
            ctx.fill(0, t, s1, h - t, (peak * 3 / 4 << 24) | color);
            ctx.fill(s1, t, s2, h - t, (peak / 3 << 24) | color);
            ctx.fill(w - s1, t, w, h - t, (peak * 3 / 4 << 24) | color);
            ctx.fill(w - s2, t, w - s1, h - t, (peak / 3 << 24) | color);
        });
    }
}
