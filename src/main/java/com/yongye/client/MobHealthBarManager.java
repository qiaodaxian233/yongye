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
 *
 * <p><b>m389 评审修补:</b>①quad/diamond 改纯标量顶点计算,世界渲染热路径不再每帧
 * new float[][](多怪持续显示时的短命数组 GC 抖动);基向量改复用静态 scratch Vector3f
 * (渲染单线程安全);②记录世界引用,换维度立即 BARS.clear()——防新世界撞相同实体
 * 网络 id 时旧血条短暂错绑;③BOSS/精英判定改复用 {@link MobAuraFeatureRenderer#tierOf}
 * 统一口径(五皮肤 BOSS 类/HIM/「xx BOSS」全覆盖,精英连带覆盖毒蛛/巨蟹实体类),
 * 不再维护第二套名字表。
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
    private static Object lastWorldRef = null;                      // m389:换世界即清防实体 id 撞车
    // m389:广告牌基向量 scratch(渲染单线程,复用免每帧分配)
    private static final Vector3f RIGHT = new Vector3f();
    private static final Vector3f UP = new Vector3f();

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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != lastWorldRef) {                             // m389:换维度/退出即清,防 id 撞车错绑
            lastWorldRef = mc.world;
            BARS.clear();
        }
        if (BARS.isEmpty()) return;
        if (!YongyeConfig.get().enableMobHealthBar || !FxBudget.on()) { BARS.clear(); return; }
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;
        if (mc.world == null || mc.player == null) { BARS.clear(); return; }

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        Vec3d cam = ctx.camera().getPos();
        Quaternionf rot = ctx.camera().getRotation();
        // 广告牌基向量:相机右/上(m389:scratch 复用,不再每帧 new)
        Vector3f rv = rot.transform(RIGHT.set(1, 0, 0));
        Vector3f uv = rot.transform(UP.set(0, 1, 0));
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
            // BOSS 有画框大血条,不重复(m389:复用 MobAuraFeatureRenderer.tierOf 统一口径);
            // 隐身怪保留追踪不画
            int mobTier = MobAuraFeatureRenderer.tierOf(liv);
            if (mobTier >= 3) { it.remove(); continue; }
            if (liv.isInvisible()) continue;
            if (liv.squaredDistanceTo(mc.player) > FxBudget.scaleDistSq(MAX_DIST_SQ)) continue;

            // 插值平滑(真值=数据追踪器同步的血量,读取零开销)
            float real = Math.max(0f, Math.min(1f, liv.getHealth() / Math.max(1f, liv.getMaxHealth())));
            b.displayed = b.displayed < 0 ? real : b.displayed + (real - b.displayed) * Math.min(1f, dt * 10f);

            float fade = ageMs > life - FADE_MS ? (life - ageMs) / (float) FADE_MS : 1f;
            boolean elite = mobTier == 2;                           // m389:统一口径(名牌精英+毒蛛/巨蟹)
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

    /** 相机对齐广告牌矩形:中心+局部偏移(ox,oy)+半宽半高,双面绕序(顶点链照 MagicCircle 在树)。
     *  m389:纯标量顶点计算,渲染热路径零数组分配。四角局部偏移=(ox±hw, oy±hh)。 */
    private static void quad(VertexConsumer vc, float cx, float cy, float cz,
                             Vector3f rv, Vector3f uv, float ox, float oy, float hw, float hh,
                             int r, int g, int b, int a) {
        if (a < 8) return;
        float x0 = ox - hw, x1 = ox + hw, y0 = oy - hh, y1 = oy + hh;
        float ax = cx + rv.x * x0 + uv.x * y0, ay = cy + rv.y * x0 + uv.y * y0, az = cz + rv.z * x0 + uv.z * y0;
        float bx = cx + rv.x * x1 + uv.x * y0, by = cy + rv.y * x1 + uv.y * y0, bz = cz + rv.z * x1 + uv.z * y0;
        float qx = cx + rv.x * x1 + uv.x * y1, qy = cy + rv.y * x1 + uv.y * y1, qz = cz + rv.z * x1 + uv.z * y1;
        float dx = cx + rv.x * x0 + uv.x * y1, dy = cy + rv.y * x0 + uv.y * y1, dz = cz + rv.z * x0 + uv.z * y1;
        // 正面
        v(vc, ax, ay, az, 0f, 0f, r, g, b, a, 1);
        v(vc, bx, by, bz, 1f, 0f, r, g, b, a, 1);
        v(vc, qx, qy, qz, 1f, 1f, r, g, b, a, 1);
        v(vc, dx, dy, dz, 0f, 1f, r, g, b, a, 1);
        // 背面(反绕序)
        v(vc, dx, dy, dz, 0f, 1f, r, g, b, a, -1);
        v(vc, qx, qy, qz, 1f, 1f, r, g, b, a, -1);
        v(vc, bx, by, bz, 1f, 0f, r, g, b, a, -1);
        v(vc, ax, ay, az, 0f, 0f, r, g, b, a, -1);
    }

    /** 相机对齐菱形(精英徽记):四顶点=上/右/下/左,双面绕序。m389:同样纯标量零分配。 */
    private static void diamond(VertexConsumer vc, float cx, float cy, float cz,
                                Vector3f rv, Vector3f uv, float ox, float oy, float rad,
                                int r, int g, int b, int a) {
        if (a < 8) return;
        float ax = cx + rv.x * ox + uv.x * (oy + rad), ay = cy + rv.y * ox + uv.y * (oy + rad), az = cz + rv.z * ox + uv.z * (oy + rad);
        float bx = cx + rv.x * (ox + rad) + uv.x * oy, by = cy + rv.y * (ox + rad) + uv.y * oy, bz = cz + rv.z * (ox + rad) + uv.z * oy;
        float qx = cx + rv.x * ox + uv.x * (oy - rad), qy = cy + rv.y * ox + uv.y * (oy - rad), qz = cz + rv.z * ox + uv.z * (oy - rad);
        float dx = cx + rv.x * (ox - rad) + uv.x * oy, dy = cy + rv.y * (ox - rad) + uv.y * oy, dz = cz + rv.z * (ox - rad) + uv.z * oy;
        // 正面
        v(vc, ax, ay, az, 0.5f, 0f, r, g, b, a, 1);
        v(vc, bx, by, bz, 1f, 0.5f, r, g, b, a, 1);
        v(vc, qx, qy, qz, 0.5f, 1f, r, g, b, a, 1);
        v(vc, dx, dy, dz, 0f, 0.5f, r, g, b, a, 1);
        // 背面(反绕序)
        v(vc, dx, dy, dz, 0f, 0.5f, r, g, b, a, -1);
        v(vc, qx, qy, qz, 0.5f, 1f, r, g, b, a, -1);
        v(vc, bx, by, bz, 1f, 0.5f, r, g, b, a, -1);
        v(vc, ax, ay, az, 0.5f, 0f, r, g, b, a, -1);
    }

    private static void v(VertexConsumer vc, float x, float y, float z, float u, float tv,
                          int r, int g, int b, int a, int ny) {
        vc.vertex(x, y, z).color(r, g, b, a).texture(u, tv)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, ny, 0);
    }
}
