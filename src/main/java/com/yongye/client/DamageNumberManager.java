package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 伤害飘字(m373,3A 打磨路线图第 1 项):收到 DamageNumberPayload 后在世界内怪物身上
 * 弹出漂浮伤害数字——弹出过冲(前 140ms 从 0.4 冲到 1.35 再 90ms 回落 1.0)→ ease-out
 * 上浮约 1 格 → 末 260ms 淡出。普通命中=暖白小字,重击=金色大字(基准尺寸 ×1.45)。
 *
 * <p>实现取舍:
 * <ul>
 *   <li>渲染走 {@code WorldRenderEvents.AFTER_TRANSLUCENT}(与 MagicCircleFxManager/SlashFxManager
 *       同挂点),广告牌朝向 = 相机四元数({@code ctx.camera().getRotation()},yarn 1.21.1 映射已核
 *       method_23767 返回 org.joml.Quaternionf);矩阵手工构造 Matrix4f(translation→rotate→scale,
 *       scale 取 (-s,-s,s) 与原版名牌同约定),不碰 MatrixStack 少赌一个 API 面。</li>
 *   <li>文本绘制用 {@code TextRenderer.draw(String,float,float,int,boolean,Matrix4f,
 *       VertexConsumerProvider,TextLayerType,int,int)}(yarn 1.21.1 映射已核 method_27521 逐参对上);
 *       TextLayerType.NORMAL 常量名为仓库首用,列「待编译验证」(1.17 起该枚举名未变,极低险,
 *       若报错换 POLYGON_OFFSET 或 SEE_THROUGH 同枚举任一常量即可)。</li>
 *   <li>淡出用颜色 alpha 高字节;MC 文本渲染对 alpha&lt;0x04 会强制不透明(tweakTransparency),
 *       故最低钳 8,寿命一到直接移除。颜色恒带满/显式 alpha(m213 染色铁律)。</li>
 *   <li>数字口径:≥10 取整走 NumFmt.compact(全模组数字口径一致),&lt;10 保一位小数
 *       (前期 2.5 点伤害取整成 2 观感说谎)。</li>
 *   <li>同屏上限 {@link #MAX} 条(超出挤掉最旧),48 格外只老化不渲染;高频 AOE 的量闸在
 *       服务端 CombatFxHandler(每玩家每 tick 限发),两头兜。</li>
 * </ul>
 */
public final class DamageNumberManager {
    private DamageNumberManager() {}

    /** 同屏最多同时存活的飘字数(超出移除最旧,防 AOE 清屏时铺满)。 */
    private static final int MAX = 60;
    private static final long LIFE_MS = 900, POP_MS = 140, POP_BACK_MS = 90, FADE_MS = 260;
    /** 渲染距离平方(48 格外不画,仍老化)。 */
    private static final double MAX_DIST_SQ = 48 * 48;

    /** 一条飘字:出生点 + 水平漂移向量(随机散布,防同一怪身上数字叠死)+ 文本 + 类别 + 出生时刻。 */
    private record Num(double x, double y, double z, double dx, double dz,
                       String text, int kind, long bornNanos) {}

    private static final List<Num> NUMS = new ArrayList<>();
    private static final Random RAND = new Random();

    /** 客户端初始化时挂世界渲染事件(YongyeClient 调)。 */
    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(DamageNumberManager::render);
    }

    /** 收包入口(主线程)。 */
    public static void onNumber(double x, double y, double z, float amount, int kind) {
        if (amount <= 0) return;
        if (NUMS.size() >= MAX) NUMS.remove(0);
        // 随机散布:水平 ±0.45 格圆盘内一点,连击时数字错开不叠字
        double ang = RAND.nextDouble() * Math.PI * 2;
        double r = 0.15 + RAND.nextDouble() * 0.30;
        String text = amount >= 10f
                ? NumFmt.compact(Math.round(amount))
                : NumFmt.compact(Math.round(amount * 10f) / 10.0);
        NUMS.add(new Num(x, y, z, Math.cos(ang) * r, Math.sin(ang) * r,
                text, kind, System.nanoTime()));
    }

    private static void render(WorldRenderContext ctx) {
        if (NUMS.isEmpty()) return;
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableDamageNumbers) { NUMS.clear(); return; }
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        Vec3d cam = ctx.camera().getPos();
        Quaternionf rot = ctx.camera().getRotation();
        float cfgScale = (float) Math.max(0.3, Math.min(3.0, c.damageNumberScale));
        long now = System.nanoTime();

        Iterator<Num> it = NUMS.iterator();
        while (it.hasNext()) {
            Num n = it.next();
            long ageMs = (now - n.bornNanos) / 1_000_000L;
            if (ageMs >= LIFE_MS) { it.remove(); continue; }

            // 位置:ease-out 上浮 1.0 格 + 同曲线水平漂移
            double t = ageMs / (double) LIFE_MS;
            double rise = 1.0 - Math.pow(1.0 - t, 3);
            double px = n.x + n.dx * rise, py = n.y + 1.0 * rise, pz = n.z + n.dz * rise;
            double rx = px - cam.x, ry = py - cam.y, rz = pz - cam.z;
            if (rx * rx + ry * ry + rz * rz > MAX_DIST_SQ) continue;

            // 弹出过冲:0.4 → 1.35 → 1.0
            float pop;
            if (ageMs < POP_MS) pop = 0.4f + (ageMs / (float) POP_MS) * 0.95f;
            else if (ageMs < POP_MS + POP_BACK_MS) pop = 1.35f - ((ageMs - POP_MS) / (float) POP_BACK_MS) * 0.35f;
            else pop = 1.0f;

            // 基准字号:普通 0.022(略小于原版名牌 0.025 不喧宾),重击 ×1.45 金色大字
            float s = (n.kind == DamageKind.HEAVY ? 0.032f : 0.022f) * cfgScale * pop;

            // 末段淡出(alpha 钳 [8,255]:MC 对 alpha<0x04 强制不透明)
            int alpha = 255;
            long fadeStart = LIFE_MS - FADE_MS;
            if (ageMs > fadeStart) alpha = (int) (255 * (1.0 - (ageMs - fadeStart) / (double) FADE_MS));
            alpha = Math.max(8, Math.min(255, alpha));
            int rgb = n.kind == DamageKind.HEAVY ? 0xFFB428 : 0xFFF2E8;
            int argb = (alpha << 24) | rgb;

            Matrix4f m = new Matrix4f()
                    .translation((float) rx, (float) ry, (float) rz)
                    .rotate(rot)
                    .scale(-s, -s, s);
            float w = tr.getWidth(n.text);
            tr.draw(n.text, -w / 2f, -4f, argb, true, m, consumers,
                    TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        }
    }

    /** 与 DamageNumberPayload 的 kind 常量同口径(避免客户端类直接依赖 network 包常量名散落)。 */
    private static final class DamageKind {
        static final int HEAVY = 1;
    }
}
