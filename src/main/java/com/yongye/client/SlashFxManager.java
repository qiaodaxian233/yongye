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
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
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

    /** m256:刀光拖尾贴图(程序化拉丝质感,学 EpicACG 的贴图化刀光路线;白色亮度图,颜色由顶点色染)。 */
    private static final Identifier TRAIL_TEX = Identifier.of("yongye", "textures/vfx/slash_trail.png");

    private static final List<Trail> TRAILS = new ArrayList<>();
    private static long lastSwingNanos = 0L;
    /** m254:player-animator 桥接是否就绪(YongyeClient 注册成功后置 true;运行期出错自动回落 false)。 */
    public static boolean animLibOk = false;
    /** m254:真动作播放让位窗——窗内本地玩家不再叠程序化姿态,避免双重动作。 */
    private static long poseSuppressUntil = 0L;
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

        // m258:空中回旋斩上报服务端结算一圈伤害(转一圈就该扫一圈;冷却与校验在服务端)
        if (variant == V_AERIAL) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                    .send(new com.yongye.network.SpinSlashPayload());
        }

        // m254:真·骨骼动作(player-animator):本地玩家成功播放真动作,则 0.8 秒窗内程序化姿态让位。
        // 运行期任何异常(库版本冲突等)一次性降级回程序化姿态,不再重试、不崩游戏。
        if (animLibOk && player instanceof net.minecraft.client.network.AbstractClientPlayerEntity acp) {
            try {
                if (SlashAnimManager.playFor(acp, variant)) poseSuppressUntil = now + 800_000_000L;
            } catch (Throwable t) {
                animLibOk = false;
                com.yongye.Yongye.LOGGER.warn("[夜蚀] 拔刀动作库运行期不可用,退回程序化姿态", t);
            }
        }

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

    /** 姿态用:该实体当前是否该摆拔刀姿态(开关 + 主手武器判定与轨迹同一套)。
     *  m254:本地玩家在真动作让位窗内不摆程序化姿态(远端玩家仍走程序化,真动作不同步给别人)。 */
    public static boolean poseEligible(LivingEntity e) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.slashFxPose) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (e == mc.player && System.nanoTime() < poseSuppressUntil) return false;
        return eligible(cfg, e.getMainHandStack());
    }

    /** m257 蓄力重斩用:该实体主手武器是否够格出刀光(公开口径,mixin/客户端管理器共用)。 */
    public static boolean weaponEligible(LivingEntity e) {
        return eligible(YongyeConfig.get(), e.getMainHandStack());
    }

    /** m257 蓄力重斩用:本地即刻放一道加大刀光(sweep 200°、半径 2.3×,重斩观感)。 */
    public static void spawnHeavy(PlayerEntity player) {
        if (player == null) return;
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableSlashFx) return;
        ItemStack stack = player.getMainHandStack();
        if (!eligible(cfg, stack)) return;
        long now = System.nanoTime();
        lastSwingNanos = now;                                 // 与普通刀光共用去重时钟
        float radius = 2.3f * (float) Math.max(0.3, cfg.slashFxSize);
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        int rgb = YongyeClient.weaponTintColor(stack) & 0xFFFFFF;
        if (TRAILS.size() >= MAX_TRAILS) TRAILS.remove(0);
        TRAILS.add(new Trail(eye.x + look.x * 0.50, eye.y + look.y * 0.50 - 0.18, eye.z + look.z * 0.50,
                player.getYaw(), player.getPitch(), 8f, 1, 200f, radius, rgb, now));
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
        // m256:贴图化刀身(EntityTranslucentEmissive=m246 已编译验证的同一条链):
        // 开着时,旧的纯色三带降为 45% 透明度当「辉光」底层,贴图刀身压在上面;关=回旧纯色观感。
        boolean textured = YongyeConfig.get().slashFxTextured;
        VertexConsumer tvc = textured ? consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TRAIL_TEX)) : null;

        Iterator<Trail> it = TRAILS.iterator();
        while (it.hasNext()) {
            Trail t = it.next();
            double ageMs = (now - t.bornNanos) / 1.0e6;
            if (ageMs >= LIFE_MS) { it.remove(); continue; }

            double fade = Math.pow(1.0 - ageMs / LIFE_MS, 1.4) * alphaCfg; // 整体淡出
            double reveal = Math.min(1.0, ageMs / (double) REVEAL_MS);     // 扫出进度
            double halo = textured ? 0.45 : 1.0;                           // m256:贴图开时纯色带降档当辉光
            int aCore = (int) Math.round(230 * fade * halo);               // 白色刀芯
            int aEdge = (int) Math.round(80 * fade * halo);                // 外缘武器色
            if (aCore <= 1 && !textured) { it.remove(); continue; }
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
                // m256:贴图刀身(内缘白热→外缘武器色,亮度/拉丝形状由贴图承担;U=沿扫掠,V=径向)
                if (tvc != null) {
                    float u0 = i / (float) SEGMENTS, u1 = (i + 1) / (float) SEGMENTS;
                    int aTex = (int) Math.round(250 * fade);
                    texQuad(tvc, cam, t, fx, fy, fz, sx, sy, sz,
                            t.radius * 0.42, s0, c0, s1, c1, rOut,
                            u0, u1, aTex, cr, cg, cb);
                }
            }
        }
    }

    /** m256:画一段贴图刀身(双面):内缘(v=0,白)→外缘(v=1,武器色);顶点链照 m246 已验证写法。 */
    private static void texQuad(VertexConsumer vc, Vec3d cam, Trail t,
                                double fx, double fy, double fz, double sx, double sy, double sz,
                                double rIn, double s0, double c0, double s1, double c1, double rOut,
                                float u0, float u1, int a, int cr, int cg, int cb) {
        float ax = px(t.ox, cam.x, fx, sx, rIn, c0, s0), ay = px(t.oy, cam.y, fy, sy, rIn, c0, s0), az = px(t.oz, cam.z, fz, sz, rIn, c0, s0);
        float bx = px(t.ox, cam.x, fx, sx, rIn, c1, s1), by = px(t.oy, cam.y, fy, sy, rIn, c1, s1), bz = px(t.oz, cam.z, fz, sz, rIn, c1, s1);
        float cx = px(t.ox, cam.x, fx, sx, rOut, c1, s1), cy = px(t.oy, cam.y, fy, sy, rOut, c1, s1), cz = px(t.oz, cam.z, fz, sz, rOut, c1, s1);
        float dx = px(t.ox, cam.x, fx, sx, rOut, c0, s0), dy = px(t.oy, cam.y, fy, sy, rOut, c0, s0), dz = px(t.oz, cam.z, fz, sz, rOut, c0, s0);
        int light = 0xF000F0;
        // 正面
        tv(vc, ax, ay, az, 255, 255, 255, a, u0, 0f, light);
        tv(vc, bx, by, bz, 255, 255, 255, a, u1, 0f, light);
        tv(vc, cx, cy, cz, cr, cg, cb, a, u1, 1f, light);
        tv(vc, dx, dy, dz, cr, cg, cb, a, u0, 1f, light);
        // 反面(倒绕序,免疫背面剔除)
        tv(vc, dx, dy, dz, cr, cg, cb, a, u0, 1f, light);
        tv(vc, cx, cy, cz, cr, cg, cb, a, u1, 1f, light);
        tv(vc, bx, by, bz, 255, 255, 255, a, u1, 0f, light);
        tv(vc, ax, ay, az, 255, 255, 255, a, u0, 0f, light);
    }

    private static void tv(VertexConsumer vc, float x, float y, float z,
                           int r, int g, int b, int a, float u, float v, int light) {
        vc.vertex(x, y, z).color(r, g, b, a).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
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
