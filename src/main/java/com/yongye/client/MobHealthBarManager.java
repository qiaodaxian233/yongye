package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 怪物头顶微型血条(m385,3A 打磨路线图第 13 项,高风险性能项按 m379 评审全约束):
 * 被玩家<b>最近命中</b>的怪头顶显示 3 秒微型血条后渐隐——打了多少一眼可见。
 *
 * <p><b>评审约束逐条落地:</b>
 * <ul>
 *   <li>只追踪最近命中:命中信号复用 DamageNumberPayload(m385 起尾加 targetId 字段),
 *       不扫世界不轮询,谁被打谁上榜;</li>
 *   <li>每玩家上限:同时最多追踪 12 条(超出挤掉最久未命中的),LOW 档再随 FxBudget 缩;</li>
 *   <li>距离裁剪:24 格外只老化不画(FxBudget 再按档缩距)——近距离才画,兼作遮挡问题的兜底;</li>
 *   <li>隐身不显:isInvisible() 的怪保留追踪但跳过绘制;</li>
 *   <li>插值平滑:显示血量每帧向真值收敛(×min(1,dt×10)),真值=客户端数据追踪器同步的
 *       getHealth/getMaxHealth(读取零开销,无需自建低频采样通道);</li>
 *   <li>精英形态差异:精英(名牌含「精英」,EliteSkinFeatureRenderer 在树同口径)=更宽更高的
 *       紫色条+顶部金线+左端菱形徽记——不只换色;普通怪=细红条;</li>
 *   <li>BOSS 不重复:名牌含 BOSS/佩恩/长门 的走 m181 画框大血条,本项主动剔除。</li>
 * </ul>
 *
 * <p>渲染:AFTER_TRANSLUCENT,EntityTranslucentEmissive(4×4 纯白贴图)顶点染色
 * (半透明层能画深色底,规避 getLightning 加色混合画不出暗底的坑),相机四元数变换
 * 局部偏移出正对屏幕的广告牌,双面绕序;顶点链与 MagicCircleFxManager 逐字同款(在树已编)。
 */
public final class MobHealthBarManager {
    private MobHealthBarManager() {}

    private static final Identifier WHITE = Identifier.of(Yongye.MOD_ID, "textures/fx/white.png");
    private static final int MAX_TRACKED = 12;
    private static final long SHOW_MS = 3000, FADE_MS = 400;
    private static final double MAX_DIST_SQ = 24 * 24;

    private static final class Bar {
        long lastHitNanos;
        float displayed = -1f;   // 显示血量占比(插值);-1=首帧直接对齐真值
    }

    private static final Map<Integer, Bar> BARS = new HashMap<>();
    private static long lastFrameNanos = System.nanoTime();

    /** 命中信号入口(DamageNumberPayload 接收处调,主线程)。 */
    public static void onHit(int entityId) {
        if (entityId < 0) return;
        if (!YongyeConfig.get().enableMobHealthBar || !FxBudget.on()) return;
        Bar b = BARS.get(entityId);
        if (b == null) {
            int cap = Math.max(4, FxBudget.scaleCount(MAX_TRACKED));
            while (BARS.size() >= cap) {                       // 挤掉最久未命中的
                Integer oldest = null; long oldestT = Long.MAX_VALUE;
                for (Map.Entry<Integer, Bar> en : BARS.entrySet()) {
                    if (en.getValue().lastHitNanos < oldestT) { oldestT = en.getValue().lastHitNanos; oldest = en.getKey(); }
                }
                if (oldest == null) break;
                BARS.remove(oldest);
            }
            b = new Bar();
            BARS.put(entityId, b);
        }
        b.lastHitNanos = System.nanoTime();
    }

    /** 客户端初始化时挂世界渲染(YongyeClient 调)。 */
    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(MobHealthBarManager::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (BARS.isEmpty()) return;
        if (!YongyeConfig.get().enableMobHealthBar || !FxBudget.on()) { BARS.clear(); return; }
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) { BARS.clear(); return; }

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        Vec3d cam = ctx.camera().getPos();
        Quaternionf rot = ctx.camera().getRotation();
        // 广告牌基向量:相机右/上
        Vector3f rv = rot.transform(new Vector3f(1, 0, 0));
        Vector3f uv = rot.transform(new Vector3f(0, 1, 0));
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        long life = FxBudget.scaleLife(SHOW_MS);

        Iterator<Map.Entry<Integer, Bar>> it = BARS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Bar> en = it.next();
            Bar b = en.getValue();
            long ageMs = (now - b.lastHitNanos) / 1_000_000L;
            if (ageMs >= life) { it.remove(); continue; }

            if (!(mc.world.getEntityById(en.getKey()) instanceof LivingEntity liv) || !liv.isAlive()) {
                it.remove(); continue;                          // 死亡/卸载即撤条
            }
            // BOSS 有画框大血条,不重复;隐身怪保留追踪不画
            String n = liv.hasCustomName() && liv.getCustomName() != null ? liv.getCustomName().getString() : "";
            if (n.contains("BOSS") || n.contains("佩恩") || n.contains("长门")) { it.remove(); continue; }
            if (liv.isInvisible()) continue;
            if (liv.squaredDistanceTo(mc.player) > FxBudget.scaleDistSq(MAX_DIST_SQ)) continue;

            // 插值平滑(真值=数据追踪器同步的血量,读取零开销)
            float real = Math.max(0f, Math.min(1f, liv.getHealth() / Math.max(1f, liv.getMaxHealth())));
            b.displayed = b.displayed < 0 ? real : b.displayed + (real - b.displayed) * Math.min(1f, dt * 10f);

            float fade = ageMs > life - FADE_MS ? (life - ageMs) / (float) FADE_MS : 1f;
            boolean elite = n.contains("精英");
            float w = elite ? 1.15f : 0.90f;
            float h = elite ? 0.11f : 0.07f;

            float cx = (float) (liv.getX() - cam.x);
            float cy = (float) (liv.getY() + liv.getHeight() + 0.38 - cam.y);
            float cz = (float) (liv.getZ() - cam.z);

            // 底(深灰蓝,带 0.02 描边余量)
            quad(vc, cx, cy, cz, rv, uv, 0f, 0f, w / 2 + 0.02f, h / 2 + 0.02f,
                    0x14, 0x16, 0x1C, (int) (190 * fade));
            // 填充(左锚,红/精英紫)
            float fillW = w * Math.max(0f, Math.min(1f, b.displayed));
            int fr = elite ? 0xB4 : 0xE0, fg = elite ? 0x4C : 0x45, fb = elite ? 0xFF : 0x45;
            quad(vc, cx, cy, cz, rv, uv, -w / 2 + fillW / 2, 0f, fillW / 2, h / 2,
                    fr, fg, fb, (int) (235 * fade));
            if (elite) {                                        // 形态差异:顶部金线 + 左端菱形徽记
                quad(vc, cx, cy, cz, rv, uv, 0f, h / 2 + 0.028f, w / 2 + 0.02f, 0.012f,
                        0xFF, 0xC3, 0x32, (int) (220 * fade));
                diamond(vc, cx, cy, cz, rv, uv, -w / 2 - 0.09f, 0f, 0.055f,
                        0xFF, 0xC3, 0x32, (int) (235 * fade));
            }
        }
    }

    /** 相机对齐广告牌矩形:中心+局部偏移(ox,oy)+半宽半高,双面绕序(顶点链照 MagicCircle 在树)。 */
    private static void quad(VertexConsumer vc, float cx, float cy, float cz,
                             Vector3f rv, Vector3f uv, float ox, float oy, float hw, float hh,
                             int r, int g, int b, int a) {
        if (a < 8) return;
        float[][] c = new float[4][];
        float[][] off = {{ox - hw, oy - hh}, {ox + hw, oy - hh}, {ox + hw, oy + hh}, {ox - hw, oy + hh}};
        for (int i = 0; i < 4; i++) {
            c[i] = new float[]{
                    cx + rv.x * off[i][0] + uv.x * off[i][1],
                    cy + rv.y * off[i][0] + uv.y * off[i][1],
                    cz + rv.z * off[i][0] + uv.z * off[i][1]};
        }
        float[][] t = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 1f}};
        for (int i = 0; i < 4; i++) v(vc, c[i], t[i], r, g, b, a, 1);
        for (int i = 3; i >= 0; i--) v(vc, c[i], t[i], r, g, b, a, -1);
    }

    /** 相机对齐菱形(精英徽记):四顶点=上右下上… 以两三角拼(用两个绕序面)。 */
    private static void diamond(VertexConsumer vc, float cx, float cy, float cz,
                                Vector3f rv, Vector3f uv, float ox, float oy, float rad,
                                int r, int g, int b, int a) {
        if (a < 8) return;
        float[][] off = {{ox, oy + rad}, {ox + rad, oy}, {ox, oy - rad}, {ox - rad, oy}};
        float[][] c = new float[4][];
        for (int i = 0; i < 4; i++) {
            c[i] = new float[]{
                    cx + rv.x * off[i][0] + uv.x * off[i][1],
                    cy + rv.y * off[i][0] + uv.y * off[i][1],
                    cz + rv.z * off[i][0] + uv.z * off[i][1]};
        }
        float[][] t = {{0.5f, 0f}, {1f, 0.5f}, {0.5f, 1f}, {0f, 0.5f}};
        for (int i = 0; i < 4; i++) v(vc, c[i], t[i], r, g, b, a, 1);
        for (int i = 3; i >= 0; i--) v(vc, c[i], t[i], r, g, b, a, -1);
    }

    private static void v(VertexConsumer vc, float[] p, float[] t, int r, int g, int b, int a, int ny) {
        vc.vertex(p[0], p[1], p[2]).color(r, g, b, a).texture(t[0], t[1])
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, ny, 0);
    }
}
