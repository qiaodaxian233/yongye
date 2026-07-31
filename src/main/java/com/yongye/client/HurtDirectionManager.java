package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 受击方向指示器(m374,3A 打磨路线图第 2 项):挨打瞬间在准星四周对应方向弹出
 * 红色弧形指示,存的是<b>来源世界坐标</b>——方位角逐帧用「玩家当前视角 + 来源坐标」
 * 重算(与灾厄核心箭头同口径),所以转视角时弧段实时贴着来源方向走,背后有怪一眼可辨。
 *
 * <p>视觉:半径 46px(前 120ms 从 52 收拢到 46 的入场收缩),弧段=5 个绕准星中心
 * 各转 ±12° 的小矩形拼近似圆弧(fill 走当前矩阵栈,旋转后照画,灾厄箭头在树先例),
 * 两端 alpha 递减出羽化;寿命 700ms 线性淡出;浓度随伤害占比(severity)上浮。
 * 同屏上限 8 条(超出挤掉最旧)。全部 fill/矩阵旋转均在树,零新 API 面。
 */
public final class HurtDirectionManager {
    private HurtDirectionManager() {}

    private static final int MAX = 8;
    private static final long LIFE_MS = 700, POP_MS = 120;
    /** 弧段基准半径(GUI 像素,准星为圆心)。 */
    private static final float RADIUS = 46f;
    /** 五个子段的角偏移与端点羽化系数。 */
    private static final float[] SEG_DEG = {-24f, -12f, 0f, 12f, 24f};
    private static final float[] SEG_A   = {0.35f, 0.70f, 1.0f, 0.70f, 0.35f};

    /** 一次受击:来源世界水平坐标 + 浓度 + 出生时刻。 */
    private record Hit(double srcX, double srcZ, float severity, long bornNanos) {}

    private static final List<Hit> HITS = new ArrayList<>();

    /** 收包入口(主线程)。 */
    public static void onHurt(double srcX, double srcZ, float severity) {
        if (HITS.size() >= MAX) HITS.remove(0);
        HITS.add(new Hit(srcX, srcZ, Math.max(0f, Math.min(1f, severity)), System.nanoTime()));
    }

    /** 客户端初始化时挂 HUD 渲染(YongyeClient 调)。 */
    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (HITS.isEmpty()) return;
            if (!YongyeConfig.get().enableHurtDirectionFx || !FxBudget.on()) { HITS.clear(); return; } // m381
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;

            int cx = ctx.getScaledWindowWidth() / 2;
            int cy = ctx.getScaledWindowHeight() / 2;

            // 玩家水平朝向单位向量(MC yaw:0=+Z/南,90=-X/西;与灾厄核心箭头逐字同口径)
            float yawRad = (float) Math.toRadians(mc.player.getYaw());
            double fx = -Math.sin(yawRad);
            double fz = Math.cos(yawRad);

            long now = System.nanoTime();
            Iterator<Hit> it = HITS.iterator();
            while (it.hasNext()) {
                Hit h = it.next();
                long ageMs = (now - h.bornNanos) / 1_000_000L;
                if (ageMs >= LIFE_MS) { it.remove(); continue; }

                // 相对方位角:0=正前,+向右(atan2(cross,dot),在树口径)
                double dx = h.srcX - mc.player.getX();
                double dz = h.srcZ - mc.player.getZ();
                if (dx * dx + dz * dz < 0.01) continue; // 来源就在脚下(自爆/贴脸重叠)方位无意义,不画
                double dot = fx * dx + fz * dz;
                double cross = fx * dz - fz * dx;
                float bearingDeg = (float) Math.toDegrees(Math.atan2(cross, dot));

                // 入场收缩 + 线性淡出;浓度随伤害占比上浮(0.55 起步,重伤 1.0)
                float r = ageMs < POP_MS ? 52f - (ageMs / (float) POP_MS) * (52f - RADIUS) : RADIUS;
                float fade = 1f - ageMs / (float) LIFE_MS;
                float base = (0.55f + 0.45f * h.severity) * fade;

                boolean low = FxBudget.lowDetail(); // m381 LOW 减子段
                for (int i = 0; i < SEG_DEG.length; i++) {
                    if (low && (i == 0 || i == SEG_DEG.length - 1)) continue;
                    int a = (int) (255 * Math.min(1f, base * SEG_A[i]));
                    if (a < 8) continue;
                    int col = (a << 24) | 0xE83030;
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(cx, cy, 0);
                    ctx.getMatrices().multiply(
                            RotationAxis.POSITIVE_Z.rotationDegrees(bearingDeg + SEG_DEG[i]));
                    // 子段:窄矩形立在半径外沿(中段稍厚,端段稍薄出锥形)
                    int half = i == 2 ? 6 : 5;
                    int thick = i == 2 ? 5 : 4;
                    ctx.fill(-half, (int) -r - thick, half, (int) -r, col);
                    ctx.getMatrices().pop();
                }
            }
        });
    }
}
