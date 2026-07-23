package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.network.CombatFxPayload;
import com.yongye.network.MagicFxPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 武器技能特效编排器(m255)——作者:「混沌斩、吞噬、终焉这些特效要夸张一些,现在看着没效果」。
 * 核心=一个极简多帧任务队列(END_SERVER_TICK 驱动,每任务≤20t,逐条 try/catch 不拖垮服务端),
 * 让特效能「演出来」:剑气月牙逐帧向前推进、灵魂逐帧被吸进玩家、冲击波逐帧扩散——
 * 单帧粒子堆得再多也只是「一坨」,分帧才有招式感。
 * 三招编排(粒子/音效全部在树,魔法阵复用 m246 管线,震屏复用 m239 CombatFxPayload):
 *  - 混沌斩:出手环爆+凋灵射击音+震屏 → 剑气月牙(SWEEP+END_ROD 弯月阵型)沿视线 6t 推进,
 *    月牙随距离展开变大,末端爆炸收尾;
 *  - 深渊吞噬:唤魔者吟唱音+黑洞开启 → 12t 内每个受害者位置持续放出灵魂(SOUL 定向粒子)
 *    飞向玩家(逐帧追玩家当前位置,人在移动灵魂会拐弯跟着追),玩家周身漩涡环逐帧收缩+
 *    魂火盘旋上升,收尾图腾金光爆发(真回到血才有)+心形;
 *  - 终焉降临:脚下血红巨型魔法阵(m246 索引色4)+凋灵诞生音+周围玩家集体震屏闪光 →
 *    16t 内天光柱(END_ROD)从玩家头顶节节升起、地面冲击波环(EXPLOSION+FLAME)逐帧扩散到技能半径、
 *    每个受害者脚下魂火柱冲天,终帧多点大爆+爆炸音。
 * 开关 weaponSkillFancyFx(关=只留三招原有的简版粒子);weaponSkillFxScale 统一缩放粒子密度(0.2~3)。
 */
public final class WeaponSkillFx {
    private WeaponSkillFx() {}

    /** 多帧任务:age 从 0 数到 total-1,每 tick 执行一次 step(age)。 */
    private static final class Task {
        int age;
        final int total;
        final IntConsumer step;
        Task(int total, IntConsumer step) { this.total = total; this.step = step; }
    }

    private static final List<Task> TASKS = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (TASKS.isEmpty()) return;
            TASKS.removeIf(t -> {
                try {
                    t.step.accept(t.age);
                } catch (Exception e) {
                    return true; // 单任务异常直接移除,不拖垮服务端
                }
                return ++t.age >= t.total;
            });
        });
        Yongye.LOGGER.info("[夜蚀] 武器技能特效编排器已挂载");
    }

    private static void run(int ticks, IntConsumer step) {
        TASKS.add(new Task(ticks, step));
    }

    private static boolean off() { return !YongyeConfig.get().weaponSkillFancyFx; }

    private static double scale() {
        return MathHelper.clamp(YongyeConfig.get().weaponSkillFxScale, 0.2, 3.0);
    }

    /** 粒子数缩放:base × scale,至少 1。 */
    private static int n(int base, double s) { return Math.max(1, (int) Math.round(base * s)); }

    /** 水平圆环(每点带一点点随机抖动)。 */
    private static void ring(ServerWorld w, Vec3d c, double r, ParticleEffect pt, int count, double yOff) {
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 * i) / count;
            w.spawnParticles(pt, c.x + Math.cos(a) * r, c.y + yOff, c.z + Math.sin(a) * r,
                    1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /** 定向粒子(原版 count=0 语义:delta 当方向、speed 当速度,发射一颗沿该方向飞的粒子)。 */
    private static void shoot(ServerWorld w, Vec3d from, Vec3d dir, ParticleEffect pt, double speed) {
        w.spawnParticles(pt, from.x, from.y, from.z, 0, dir.x, dir.y, dir.z, speed);
    }

    // ==================== 混沌斩 ====================

    /** 剑气月牙沿视线推进(在伤害结算之外叠加,纯演出)。 */
    public static void chaosSlash(ServerWorld w, ServerPlayerEntity p, double range) {
        if (off()) return;
        double s = scale();
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVector().normalize();
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d up = right.crossProduct(look).normalize();

        // 出手一瞬:施法者足下魔气环爆 + 凋灵射击音 + 自己震屏
        ring(w, p.getPos(), 1.4, ParticleTypes.ENCHANTED_HIT, n(26, s), 1.0);
        w.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 0.8f, 1.35f);
        ServerPlayNetworking.send(p, new CombatFxPayload(CombatFxPayload.HEAVY, 1.2f, 2.6f, false, false));

        int ticks = 6;
        double step = Math.max(1.0, range) / ticks;
        run(ticks, t -> {
            double d = step * (t + 1);
            Vec3d c = eye.add(look.multiply(d));
            int m = n(8, s);
            double wid = 1.5 + d * 0.28;                       // 月牙随距离展开变大
            for (int i = -m; i <= m; i++) {
                double f = i / (double) m;                     // -1..1 横向参数
                Vec3d q = c.add(right.multiply(f * wid))
                        .add(up.multiply((1 - f * f) * 0.45 - 0.2))   // 中间微凸的月牙拱
                        .add(look.multiply(-Math.abs(f) * 0.8));      // 两端后掠成弯月
                w.spawnParticles(ParticleTypes.SWEEP_ATTACK, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                w.spawnParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.02);
            }
            w.spawnParticles(ParticleTypes.CRIT, c.x, c.y, c.z, n(10, s), 0.6, 0.5, 0.6, 0.3);
            if (t == ticks - 1) {                              // 末端炸开收尾
                w.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.5, 0.4, 0.5, 0);
                w.playSound(null, c.x, c.y, c.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 0.6f);
            }
        });
    }

    // ==================== 深渊吞噬 ====================

    /** 灵魂从每个受害者处被持续吸向玩家 + 周身漩涡收缩,healed=真回到血才放图腾金光。 */
    public static void abyssDevour(ServerWorld w, ServerPlayerEntity p, double radius,
                                   List<Vec3d> victims, boolean healed) {
        if (off()) return;
        double s = scale();
        w.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 1.0f, 0.7f);
        ServerPlayNetworking.send(p, new CombatFxPayload(CombatFxPayload.HEAVY, 0.9f, 2.0f, false, false));

        List<Vec3d> pts = victims.size() > 12 ? victims.subList(0, 12) : victims; // 灵魂源封顶防刷屏
        int ticks = 12;
        run(ticks, t -> {
            Vec3d me = p.getPos().add(0, 1.0, 0);              // 逐帧追玩家当前位置,人动灵魂跟着拐
            for (Vec3d v : pts) {
                Vec3d from = v.add((w.random.nextDouble() - 0.5) * 0.6,
                        0.6 + w.random.nextDouble() * 0.8,
                        (w.random.nextDouble() - 0.5) * 0.6);
                Vec3d dir = me.subtract(from);
                double len = dir.length();
                if (len < 0.05) continue;
                dir = dir.multiply(1 / len);
                shoot(w, from, dir, ParticleTypes.SOUL, 0.45 + len * 0.04);   // 灵魂被吸走
                if (t % 2 == 0) shoot(w, from, dir, ParticleTypes.PORTAL, 0.8);
            }
            double r = Math.max(0.4, radius * (1.0 - t / (double) ticks));     // 漩涡环逐帧收缩
            ring(w, p.getPos(), r, ParticleTypes.REVERSE_PORTAL, n(14, s), 0.15);
            double a = t * 0.9;                                                // 魂火双螺旋盘升
            for (int k = 0; k < 2; k++) {
                double ang = a + k * Math.PI;
                w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        p.getX() + Math.cos(ang) * 0.9, p.getY() + 0.2 + t * 0.18, p.getZ() + Math.sin(ang) * 0.9,
                        1, 0.02, 0.02, 0.02, 0.01);
            }
            if (t == ticks - 1 && healed) {                    // 收尾:真吸到血才有金光爆发
                w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        p.getX(), p.getBodyY(0.7), p.getZ(), n(36, s), 0.5, 0.7, 0.5, 0.35);
                w.spawnParticles(ParticleTypes.HEART,
                        p.getX(), p.getY() + 2.0, p.getZ(), 5, 0.4, 0.3, 0.4, 0.1);
                w.playSound(null, p.getX(), p.getY(), p.getZ(),
                        SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 0.6f);
            }
        });
    }

    // ==================== 终焉降临 ====================

    /** 血红巨阵 + 天光柱 + 扩散冲击波 + 受害者魂火柱 + 周围玩家集体震屏闪光。 */
    public static void finality(ServerWorld w, ServerPlayerEntity p, double radius, List<Vec3d> victims) {
        if (off()) return;
        double s = scale();
        // 脚下血红巨型魔法阵(m246 管线,索引色 4=红),64 格广播照 SkillFxHelper 口径
        if (YongyeConfig.get().magicCircleEnabled) {
            MagicFxPayload circle = new MagicFxPayload(4, p.getX(), p.getY() + 0.06, p.getZ(),
                    (float) Math.min(radius, 8.0));
            for (ServerPlayerEntity sp : w.getServer().getPlayerManager().getPlayerList()) {
                if (sp.getWorld() == w && sp.squaredDistanceTo(p) < 64 * 64) {
                    ServerPlayNetworking.send(sp, circle);
                }
            }
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.9f, 1.3f);
        // 周围玩家集体重震 + 整屏闪光(末日感)
        for (ServerPlayerEntity sp : w.getServer().getPlayerManager().getPlayerList()) {
            if (sp.getWorld() == w && sp.squaredDistanceTo(p) < 24 * 24) {
                ServerPlayNetworking.send(sp, new CombatFxPayload(CombatFxPayload.KILL, 1.8f, 3.5f, true, false));
            }
        }

        List<Vec3d> pts = victims.size() > 8 ? victims.subList(0, 8) : victims;   // 魂火柱封顶
        int ticks = 16;
        run(ticks, t -> {
            // 天光柱:从玩家头顶节节升起
            double h = 1.5 + t * 0.9;
            w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + h, p.getZ(),
                    n(6, s), 0.25, 0.45, 0.25, 0.02);
            // 地面冲击波环:逐帧扩散到技能半径(EXPLOSION 隔帧放,FLAME 补密度)
            double r = radius * (t + 1) / ticks;
            if (t % 2 == 0) ring(w, p.getPos(), r, ParticleTypes.EXPLOSION, Math.min(24, n((int) (r * 2.0), s)), 0.3);
            ring(w, p.getPos(), r, ParticleTypes.FLAME, Math.min(40, n((int) (r * 3.0), s)), 0.15);
            if (t % 3 == 0) w.spawnParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 0.4, p.getZ(),
                    n(4, s), r * 0.5, 0.2, r * 0.5, 0.0);
            // 每个受害者脚下魂火柱冲天
            for (Vec3d v : pts) {
                w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, v.x, v.y + t * 0.35, v.z,
                        2, 0.15, 0.2, 0.15, 0.02);
            }
            if (t == ticks - 1) {                              // 终帧:多点大爆 + 爆炸音
                for (int k = 0; k < 4; k++) {
                    double a = Math.PI * 0.5 * k + 0.6;
                    w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                            p.getX() + Math.cos(a) * radius * 0.5, p.getY() + 0.5, p.getZ() + Math.sin(a) * radius * 0.5,
                            1, 0, 0, 0, 0);
                }
                w.playSound(null, p.getX(), p.getY(), p.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 0.75f);
            }
        });
    }
}
