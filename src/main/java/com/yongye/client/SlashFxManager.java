package com.yongye.client;

import com.yongye.YongyeConfig;
import com.yongye.item.ChaosBladeItem;
import com.yongye.item.ClassWeaponItem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 拔刀剑式攻击动画(m240)——斩击轨迹弧光,学习 SlashBlade-Refabricated 的做法:
 * 他们的斩击特效是「带 yaw/pitch/roll 的弧面 + 随进度旋转扫出」(SlashEffectRenderer 里 -135°×progress),
 * 玩家动作则依赖外部 player-animator 库 + VMD 动作文件。
 * 本实现取其神不引其依赖:
 *  ① 轨迹 = 纯客户端弧面网格(内圈透明→白色刀芯→外圈武器色,附加混合发光),
 *    按「揭示进度」从一侧扫到另一侧再整体淡出,复刻拔刀的斩击感;零实体零网络包零伤害改动;
 *  ② 三段连击循环(斜劈→反手→横扫),姿态由 {@code SlashPoseMixin} 配合;
 *  ③ 颜色接 m211 强化等级染色管线——+100 前银白,往上冰蓝→紫→红,刀光随武器一起变色;
 *  ④ 顶点全部自算基向量(yaw/pitch/roll 手工旋转),只用 vertex(float,float,float)+color(int×4),
 *    不赌 Matrix4f/MatrixStack 重载在本映射下的签名。
 * 触发双保险:PlayerSlashSwingMixin 钩 doAttack(含挥空),AttackEntityCallback 兜底(mixin 不挂时命中仍有轨迹),
 * 50ms 去重防双触发。计时用 System.nanoTime(不依赖 tickDelta,帧率无关的平滑淡出)。
 */
public final class SlashFxManager {
    private SlashFxManager() {}

    private static final long LIFE_MS = 320;    // 轨迹总寿命
    private static final long REVEAL_MS = 95;   // 扫出用时(拔刀剑的 progress 扫动)
    private static final int MAX_TRAILS = 10;   // 同屏上限(狂点保护)
    private static final int SEGMENTS = 18;     // 弧面分段

    private static final List<Trail> TRAILS = new ArrayList<>();
    private static long lastSwingNanos = 0L;
    private static int combo = 0; // 地面连击 0 斜劈 / 1 反手 / 2 上撩 / 3 横扫收式,1.2 秒不出刀回到 0

    // 动作编号(m242,学 SlashBlade 的 upperslash / aerial_cleave / piercing / circle_slash 状态触发式):
    // 0~3 = 地面四连击;4 = 空中回旋斩;5 = 疾跑突刺;6 = 潜行居合。
    private static final int V_AERIAL = 4, V_LUNGE = 5, V_IAI = 6;

    /** 一道斩击:原点 + 朝向(yaw/pitch/roll)+ 扫向/张角/半径 + 武器色 + 出生时刻。 */
    private record Trail(double ox, double oy, double oz,
                         float yawDeg, float pitchDeg, float rollDeg,
                         int sweepDir, float sweepDeg, float radius,
                         int rgb, long bornNanos) {}

    /** 客户端初始化时挂世界渲染事件(YongyeClient 调)。 */
    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SlashFxManager::render);
    }

    /**
     * 攻击触发入口(doAttack mixin 与 AttackEntityCallback 兜底共用,50ms 去重)。
     * 只对本地玩家生效(两个触发点天然只有本地输入会走到)。
     */
    public static void trySpawn(PlayerEntity player) {
        if (player == null || player.isSpectator()) return;
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableSlashFx) return;
        ItemStack stack = player.getMainHandStack();
        if (!eligible(cfg, stack)) return;

        long now = System.nanoTime();
        long gap = now - lastSwingNanos;
        if (gap < 50_000_000L) return;                       // 双触发点去重
        int variant = contextVariant(player);                // 空中/疾跑/潜行的状态动作优先
        if (variant < 0) {                                   // 地面普通挥砍才推进连击链
            combo = (gap > 1_200_000_000L) ? 0 : (combo + 1) % 4; // 断连回到第一式
            variant = combo;
        }
        lastSwingNanos = now;

        // 七式参数:roll=斩面倾角(绕视线),dir=扫出方向
        float roll, sweep, radius; int dir;
        switch (variant) {
            case 0 ->      { roll = -52f; dir =  1; sweep = 128f; radius = 1.60f; } // 右上→左下斜劈
            case 1 ->      { roll =  52f; dir = -1; sweep = 128f; radius = 1.60f; } // 反手回斩
            case 2 ->      { roll =  96f; dir = -1; sweep = 136f; radius = 1.65f; } // 上撩斩(近垂直斩面向上挑)
            case V_AERIAL -> { roll = 10f; dir =  1; sweep = 300f; radius = 1.70f; } // 空中回旋斩(近整圈)
            case V_LUNGE ->  { roll =  0f; dir =  1; sweep =  26f; radius = 2.60f; } // 疾跑突刺(窄长向前)
            case V_IAI ->    { roll =   3f; dir = -1; sweep = 205f; radius = 1.90f; } // 潜行居合(低平大横斩)
            default ->     { roll =   6f; dir =  1; sweep = 168f; radius = 1.95f; } // 横扫收式(第四击,更大)
        }
        radius *= (float) Math.max(0.3, cfg.slashFxSize);

        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        double ox = eye.x + look.x * 0.50;
        double oy = eye.y + look.y * 0.50 - 0.18; // 略低于视线,更像手上挥出
        double oz = eye.z + look.z * 0.50;

        int rgb = YongyeClient.weaponTintColor(stack) & 0xFFFFFF; // m211 管线:+100 前白,往上蓝→紫→红

        if (TRAILS.size() >= MAX_TRAILS) TRAILS.remove(0);
        TRAILS.add(new Trail(ox, oy, oz, player.getYaw(), player.getPitch(), roll,
                dir, sweep, radius, rgb, now));
    }

    /**
     * 状态动作判定(m242):空中→回旋斩 / 疾跑→突刺 / 潜行→居合,都不是→-1(走地面连击链)。
     * 玩家的 onGround/sprinting/sneaking 都是同步旗标,远端玩家的姿态也能按真实状态匹配。
     */
    public static int contextVariant(LivingEntity e) {
        if (!YongyeConfig.get().slashFxContextMoves) return -1;
        if (!e.isOnGround()) return V_AERIAL;
        if (e.isSprinting()) return V_LUNGE;
        if (e.isSneaking()) return V_IAI;
        return -1;
    }

    /** 姿态用:当前动作式(状态动作优先;地面链本地玩家=真实连击计数,其他玩家按 age 伪随机)。 */
    public static int poseVariant(LivingEntity e) {
        int cv = contextVariant(e);
        if (cv >= 0) return cv;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && e == mc.player) return combo;
        return (e.age / 18) % 4;
    }

    /** 姿态用:该实体当前是否该摆拔刀姿态(开关 + 主手武器判定与轨迹同一套)。 */
    public static boolean poseEligible(LivingEntity e) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.slashFxPose) return false;
        return eligible(cfg, e.getMainHandStack());
    }

    /** 武器判定:本模组武器恒生效;原版近战(剑/斧/三叉戟)按开关;
     *  外模组武器即便 extends SwordItem 也不给——它们的伤害本就被 m189 过滤,假刀不配发光。 */
    private static boolean eligible(YongyeConfig cfg, ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        Item it = st.getItem();
        if (it instanceof ClassWeaponItem || it instanceof ChaosBladeItem) return true;
        if (!cfg.slashFxVanillaWeapons) return false;
        if (!(it instanceof SwordItem || it instanceof AxeItem || it instanceof TridentItem)) return false;
        String ns = Registries.ITEM.getId(it).getNamespace();
        return "minecraft".equals(ns) || "yongye".equals(ns);
    }

    // ==================== 渲染 ====================

    private static void render(WorldRenderContext ctx) {
        if (TRAILS.isEmpty()) return;
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;
        double alphaCfg = Math.max(0.0, Math.min(1.0, YongyeConfig.get().slashFxAlpha));
        if (alphaCfg <= 0.0) { TRAILS.clear(); return; }

        Vec3d cam = ctx.camera().getPos();
        long now = System.nanoTime();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning()); // 位置+颜色,附加混合=发光

        Iterator<Trail> it = TRAILS.iterator();
        while (it.hasNext()) {
            Trail t = it.next();
            double ageMs = (now - t.bornNanos) / 1.0e6;
            if (ageMs >= LIFE_MS) { it.remove(); continue; }

            double fade = Math.pow(1.0 - ageMs / LIFE_MS, 1.4) * alphaCfg; // 整体淡出
            double reveal = Math.min(1.0, ageMs / (double) REVEAL_MS);     // 扫出进度
            int aCore = (int) Math.round(230 * fade);                      // 白色刀芯
            int aEdge = (int) Math.round(80 * fade);                       // 外缘武器色
            if (aCore <= 1) { it.remove(); continue; }
            int cr = (t.rgb >> 16) & 0xFF, cg = (t.rgb >> 8) & 0xFF, cb = t.rgb & 0xFF;

            // 手工基向量:F=视线,R=水平右手(F×上),再绕 F 转 roll 得斩面内的 R'
            double yaw = Math.toRadians(t.yawDeg), pit = Math.toRadians(t.pitchDeg);
            double fx = -Math.sin(yaw) * Math.cos(pit), fy = -Math.sin(pit), fz = Math.cos(yaw) * Math.cos(pit);
            double rx = -Math.cos(yaw), ry = 0.0, rz = -Math.sin(yaw);
            double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx; // U = R×F
            double rol = Math.toRadians(t.rollDeg);
            double c = Math.cos(rol), s = Math.sin(rol);
            double sx = rx * c + ux * s, sy = ry * c + uy * s, sz = rz * c + uz * s;       // R' 斩面横轴

            double half = Math.toRadians(t.sweepDeg) / 2.0;
            double aStart = t.sweepDir > 0 ? -half : half;
            double step = t.sweepDir * (Math.toRadians(t.sweepDeg) * reveal) / SEGMENTS;
            if (step == 0.0) continue;

            double rIn = t.radius * 0.50, rMid = t.radius * 0.82, rOut = t.radius;
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = aStart + step * i, a1 = a0 + step;
                // 斩面内的点:P = O + F·(r·cos a) + R'·(r·sin a)
                double s0 = Math.sin(a0), c0 = Math.cos(a0), s1 = Math.sin(a1), c1 = Math.cos(a1);
                // 内带:内圈全透明 → 刀芯白
                quad(vc, cam, t,
                        fx, fy, fz, sx, sy, sz,
                        rIn, s0, c0, rIn, s1, c1, rMid, s1, c1, rMid, s0, c0,
                        255, 255, 255, 0, 255, 255, 255, aCore);
                // 外带:刀芯白 → 外缘武器色渐隐
                quad(vc, cam, t,
                        fx, fy, fz, sx, sy, sz,
                        rMid, s0, c0, rMid, s1, c1, rOut, s1, c1, rOut, s0, c0,
                        255, 255, 255, aCore, cr, cg, cb, aEdge);
            }
        }
    }

    /** 画一个双面四边形:innerA(r,s,c)→innerB→outerB→outerA,内缘色/外缘色各带 alpha。 */
    private static void quad(VertexConsumer vc, Vec3d cam, Trail t,
                             double fx, double fy, double fz, double sx, double sy, double sz,
                             double ra, double sa, double ca, double rb, double sb, double cb2,
                             double rc, double sc, double cc, double rd, double sd, double cd,
                             int ir, int ig, int ib, int ia, int or_, int og, int ob, int oa) {
        float ax = px(t.ox, cam.x, fx, sx, ra, ca, sa), ay = px(t.oy, cam.y, fy, sy, ra, ca, sa), az = px(t.oz, cam.z, fz, sz, ra, ca, sa);
        float bx = px(t.ox, cam.x, fx, sx, rb, cb2, sb), by = px(t.oy, cam.y, fy, sy, rb, cb2, sb), bz = px(t.oz, cam.z, fz, sz, rb, cb2, sb);
        float cx = px(t.ox, cam.x, fx, sx, rc, cc, sc), cy = px(t.oy, cam.y, fy, sy, rc, cc, sc), cz = px(t.oz, cam.z, fz, sz, rc, cc, sc);
        float dx = px(t.ox, cam.x, fx, sx, rd, cd, sd), dy = px(t.oy, cam.y, fy, sy, rd, cd, sd), dz = px(t.oz, cam.z, fz, sz, rd, cd, sd);
        // 正面
        vc.vertex(ax, ay, az).color(ir, ig, ib, ia);
        vc.vertex(bx, by, bz).color(ir, ig, ib, ia);
        vc.vertex(cx, cy, cz).color(or_, og, ob, oa);
        vc.vertex(dx, dy, dz).color(or_, og, ob, oa);
        // 反面(倒绕序,免疫背面剔除)
        vc.vertex(dx, dy, dz).color(or_, og, ob, oa);
        vc.vertex(cx, cy, cz).color(or_, og, ob, oa);
        vc.vertex(bx, by, bz).color(ir, ig, ib, ia);
        vc.vertex(ax, ay, az).color(ir, ig, ib, ia);
    }

    /** 单轴分量:origin + F·(r·cosA) + R'·(r·sinA) − cam。 */
    private static float px(double o, double cam, double f, double s, double r, double cosA, double sinA) {
        return (float) (o + f * r * cosA + s * r * sinA - cam);
    }
}
