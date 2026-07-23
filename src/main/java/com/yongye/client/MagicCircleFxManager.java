package com.yongye.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 地面魔法阵渲染(m246)——法师技能包素材:5 色 × 18 帧「从小圈长成满阵」序列帧。
 * 一枚阵的生命:0~650ms 逐帧生长(1→18)→ 定帧缓旋 → 尾段 500ms 淡出,全程慢速自转;
 * 全亮度(light 0xF000F0)+ 半透明发光层,夜里也醒目。计时 System.nanoTime 帧率无关(照 SlashFx)。
 * 顶点走 [pos+color+texture+overlay+light+normal] 全链(实体半透明发光层的规格),
 * 相机相对坐标(照 SlashFx 减 camera pos),正反两面各画一份防止仰视消失。
 * 帧贴图懒加载:RenderLayer 按 Identifier 取,不需要预注册。
 */
public final class MagicCircleFxManager {
    private MagicCircleFxManager() {}

    private static final long GROW_MS = 650, HOLD_MS = 1400, FADE_MS = 500;
    private static final long LIFE_MS = GROW_MS + HOLD_MS + FADE_MS;
    private static final int MAX_CIRCLES = 8;
    private static final String[] COLORS = {"blue", "green", "lime", "pink", "red"};
    /** [色][帧] 的贴图 id,静态建表避免每帧拼字符串。 */
    private static final Identifier[][] FRAMES = new Identifier[COLORS.length][18];
    static {
        for (int c = 0; c < COLORS.length; c++)
            for (int f = 0; f < 18; f++)
                FRAMES[c][f] = Identifier.of("yongye", "textures/vfx/magic_" + COLORS[c] + "_" + (f + 1) + ".png");
    }

    private record Circle(int color, double x, double y, double z, float radius, long bornNanos) {}
    private static final List<Circle> CIRCLES = new ArrayList<>();

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(MagicCircleFxManager::render);
    }

    /** 收包入口(主线程)。 */
    public static void onCircle(int color, double x, double y, double z, float radius) {
        if (CIRCLES.size() >= MAX_CIRCLES) CIRCLES.remove(0);
        CIRCLES.add(new Circle(Math.max(0, Math.min(COLORS.length - 1, color)), x, y, z,
                Math.max(0.5f, radius), System.nanoTime()));
    }

    private static void render(WorldRenderContext ctx) {
        if (CIRCLES.isEmpty()) return;
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;
        Vec3d cam = ctx.camera().getPos();
        long now = System.nanoTime();

        Iterator<Circle> it = CIRCLES.iterator();
        while (it.hasNext()) {
            Circle c = it.next();
            long ageMs = (now - c.bornNanos) / 1_000_000L;
            if (ageMs >= LIFE_MS) { it.remove(); continue; }

            int frame;
            float alpha;
            if (ageMs < GROW_MS) {
                frame = (int) (ageMs * 18 / GROW_MS);       // 0..17 逐帧生长
                alpha = 0.95f;
            } else if (ageMs < GROW_MS + HOLD_MS) {
                frame = 17;
                alpha = 0.95f;
            } else {
                frame = 17;
                alpha = 0.95f * (1f - (ageMs - GROW_MS - HOLD_MS) / (float) FADE_MS);
            }
            frame = Math.max(0, Math.min(17, frame));
            int a = Math.max(0, Math.min(255, (int) (alpha * 255)));

            // 慢速自转:30°/秒
            double rot = Math.toRadians(ageMs * 0.030d);
            double cos = Math.cos(rot), sin = Math.sin(rot);
            float r = c.radius;
            // 四角(绕 y 轴旋转后的水平正方形),相机相对
            double cx = c.x - cam.x, cy = c.y - cam.y, cz = c.z - cam.z;
            double[][] corners = new double[4][];
            double[][] base = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
            for (int i = 0; i < 4; i++) {
                double bx = base[i][0], bz = base[i][1];
                corners[i] = new double[]{cx + bx * cos - bz * sin, cy, cz + bx * sin + bz * cos};
            }
            VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(FRAMES[c.color][frame]));
            float[][] uv = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 1f}};
            int light = 0xF000F0;
            for (int i = 0; i < 4; i++) quadVertex(vc, corners[i], uv[i], a, light, 1);
            for (int i = 3; i >= 0; i--) quadVertex(vc, corners[i], uv[i], a, light, -1); // 背面
        }
    }

    private static void quadVertex(VertexConsumer vc, double[] p, float[] uv, int a, int light, int ny) {
        vc.vertex((float) p[0], (float) p[1], (float) p[2])
                .color(255, 255, 255, a)
                .texture(uv[0], uv[1])
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0, ny, 0);
    }
}
