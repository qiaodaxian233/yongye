package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * 万剑归一·幻影剑群(m448,作者:「剑的大招万剑归一没有剑啊」)。
 *
 * <p><b>做什么</b>:剑客放大招时,在玩家身后升起一扇幻影剑阵(渲染的就是玩家手里那把武器的模型,
 * 作者换什么剑、幻影就是什么剑),悬停列阵后按拍点分波俯冲向准星方向,2.6 秒收场。
 *
 * <p><b>编舞的时间轴来自作者自己的 bbmodel</b>:苍魂剑工程里「万剑归一」动画 length=2.6s、
 * sword_root 关键帧落在 0 / 0.55 / 1.2 / 1.75 / 2.0 / 2.6 —— 骨骼时间轴动画原版物品模型放不了
 * (m447 已说明),但它的**节拍能放**:0~0.55 升起展扇、0.55~1.2 悬停蓄势、1.2 / 1.75 / 2.0
 * 三个拍点各放一波俯冲、2.6 全部收场。作者画的是剑的挥舞,这里译成剑群的进军。
 *
 * <p><b>全部走在树先例,零新 API 面</b>:世界渲染挂 {@code AFTER_TRANSLUCENT} + 相机差值定位
 * (DamageNumberManager 同款);渲染一把武器 = {@code getItemRenderer().renderItem(..., FIXED, ...)}
 * (m247 背挂逐字同款,FIXED 模式吃模型自己的 display);满亮度 0xF000F0(发光 FX 惯例)。
 * 纯本地纯演出:伤害仍由服务端大招本体结算,这里一行游戏逻辑都没有;时间驱动到点必消。
 *
 * <p>预算:剑数走 FxBudget.scaleCount(默 14,LOW 自动减),enableSwordRainFx 总闸,
 * FxBudget.on() OFF 退场;每帧每剑一次 renderItem,2.6 秒生命期,无队列无残留。
 */
@Environment(EnvType.CLIENT)
public final class SwordRainFx {
    private SwordRainFx() {}

    /** 作者 bbmodel「万剑归一」的关键帧拍点(秒):三波俯冲的触发时刻。 */
    private static final float BEAT_RISE_END = 0.55f, BEAT_W1 = 1.20f, BEAT_W2 = 1.75f, BEAT_W3 = 2.00f;
    private static final float LIFE_S = 2.6f;
    private static final int MAX_SWORDS = 22;

    private static long startNanos = 0;
    private static Vec3d origin = Vec3d.ZERO;   // 施放时玩家位置
    private static Vec3d aim = new Vec3d(0, 0, 1); // 施放时视线方向(水平化)
    private static ItemStack blade = ItemStack.EMPTY;

    /** m411 面板探针口径。 */
    static boolean isShowing() { return startNanos != 0; }

    /** 大招边沿调(UltimateCastFx):仅剑客出剑阵。 */
    public static void onCast(String classId) {
        if (!"swordsman".equals(classId)) return;
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableSwordRainFx || !FxBudget.on()) return;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        ItemStack held = p.getMainHandStack();
        if (held.isEmpty()) return;               // 空手放大招:没有剑可成阵,不出
        blade = held.copy();
        origin = p.getPos();
        Vec3d look = p.getRotationVec(1.0f);
        aim = new Vec3d(look.x, 0, look.z).normalize();
        if (aim.lengthSquared() < 1.0e-4) aim = new Vec3d(0, 0, 1); // 正俯仰视:退化兜底
        startNanos = System.nanoTime();
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SwordRainFx::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (startNanos == 0) return;
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableSwordRainFx || !FxBudget.on()) { startNanos = 0; return; }
        float t = (System.nanoTime() - startNanos) / 1_000_000_000f;
        if (t >= LIFE_S) { startNanos = 0; return; }
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null || blade.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        int count = Math.max(6, Math.min(MAX_SWORDS, FxBudget.scaleCount(
                Math.max(6, Math.min(MAX_SWORDS, c.swordRainCount)))));
        Vec3d cam = ctx.camera().getPos();
        Vec3d side = new Vec3d(-aim.z, 0, aim.x);            // 水平右向
        float yawDeg = (float) Math.toDegrees(MathHelper.atan2(aim.x, aim.z));

        MatrixStack ms = new MatrixStack();
        for (int i = 0; i < count; i++) {
            // 三波分配:i%3 → 波拍点(作者关键帧 1.2/1.75/2.0);展扇位次按 i 对称展开
            float beat = switch (i % 3) { case 0 -> BEAT_W1; case 1 -> BEAT_W2; default -> BEAT_W3; };
            float fan = (i - (count - 1) / 2f) / Math.max(1f, (count - 1) / 2f);   // −1..1
            // 阵位:身后 1.6~2.6 格、左右展开 ±2.8、高 1.2~3.4(外侧略高成弧)
            Vec3d slot = origin
                    .add(aim.multiply(-1.6 - 1.0 * Math.abs(fan)))
                    .add(side.multiply(fan * 2.8))
                    .add(0, 2.0 + 1.2 * Math.abs(fan) + 0.2 * Math.sin(i * 2.39996), 0);

            Vec3d pos;
            float pitchDeg;   // 剑体俯仰:升起竖直、俯冲低头
            float alpha01;
            if (t < BEAT_RISE_END) {                        // 升起:从腰际浮到阵位
                float k = ease(t / BEAT_RISE_END);
                pos = origin.add(0, 0.8, 0).lerp(slot, k);
                pitchDeg = -90f + 25f * k;                  // 尖朝上略前倾
                alpha01 = k;
            } else if (t < beat) {                          // 悬停蓄势:微幅呼吸
                float bob = 0.06f * MathHelper.sin((t - BEAT_RISE_END) * 6.0f + i);
                pos = slot.add(0, bob, 0);
                pitchDeg = -65f;
                alpha01 = 1f;
            } else {                                        // 俯冲:朝准星方向 45° 下扎,飞 14 格
                float k = ease(Math.min(1f, (t - beat) / 0.5f));
                Vec3d dive = slot.add(aim.multiply(14 * k)).add(0, -(slot.y - origin.y - 0.4) * k, 0);
                pos = dive;
                pitchDeg = 35f;                             // 低头突刺
                alpha01 = 1f - Math.max(0f, (k - 0.72f) / 0.28f);   // 末段淡出
            }
            if (alpha01 <= 0.02f) continue;

            ms.push();
            ms.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg + fan * 14f)); // 面向前进方向,扇内微散
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDeg));
            float sc = 0.9f + 0.15f * (i % 3);
            ms.scale(sc, sc, sc);
            mc.getItemRenderer().renderItem(mc.player, blade, ModelTransformationMode.FIXED, false,
                    ms, consumers, mc.world, 0xF000F0, OverlayTexture.DEFAULT_UV,
                    mc.player == null ? i : mc.player.getId() + i);
            ms.pop();
        }
    }

    private static float ease(float x) { x = MathHelper.clamp(x, 0f, 1f); return x * x * (3 - 2 * x); }
}
