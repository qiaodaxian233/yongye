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
 *
 * <p><b>m451 起本类还托管「剑客四技能」编排</b>(剑气波/招架反弹/剑气凌空/剑气层气场,见文件下半)。
 * 之所以不另开一个类:这里的多帧任务队列(含逐任务 try/catch)、ring/shoot/n 几个画法助手正是
 * 那四条要用的东西,项目守则第 4 条「不造新接口,先复用已有」;而且**不必在主类新增 register()**,
 * 少一处重复注册风险。两边各自有独立开关(weaponSkillFancyFx / swordsmanFancyFx),互不影响。
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
        ServerPlayNetworking.send(p, new CombatFxPayload(CombatFxPayload.HEAVY, 1.2f, 2.6f, false, false, 0));

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
        ServerPlayNetworking.send(p, new CombatFxPayload(CombatFxPayload.HEAVY, 0.9f, 2.0f, false, false, 0));

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
                ServerPlayNetworking.send(sp, new CombatFxPayload(CombatFxPayload.KILL, 1.8f, 3.5f, true, false, 0));
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

    // ==================== 剑客四技能(m451)====================
    // 节拍全部取自作者手绘 azure_soulblade bbmodel 里那五条骨骼动画的关键帧时刻。
    // 骨骼动画本身原版物品模型放不了(m447 已如实说明),但**它的节拍能放**——这正是 m448
    // 「万剑归一」的做法,本笔把剩下四条按同一条路子落地。
    //
    // 一处诚实的改编:作者的动画都从「起手蓄势」开始(剑气波 0→0.38s、凌空 0→0.38s),
    // 而游戏里这些技能是**命中那一瞬间触发**的——起手其实是玩家自己那一刀,已经挥完了。
    // 所以四条统一只取「出刀之后」的段落,段内相对时长按原比例保留:
    //   剑气波   0.55→1.4s  的 0.85s → 9t 推进(重音落在原 0.82 那一拍)
    //   招架反弹 0→1.05s    六拍全取 → 21t(它本来就从接触瞬间起算,无需裁)
    //   剑气凌空 0.38→1.65s 的 1.27s → 25t 四段(拉出/加粗/崩解/消散)
    //   身法如风 1.2s loop  → 24t 一圈,唯一的 loop 动画,对位的是「剑气层」这个持续状态
    //
    // 纪律:全服务端 spawnParticles(联机同屏可见,零客户端零协议);粒子/音效常量全部在树已用
    // (SWEEP_ATTACK/SOUL_FIRE_FLAME/ENCHANTED_HIT/END_ROD/CRIT · ATTACK_SWEEP/STRONG/CRIT/
    // KNOCKBACK/BELL_RESONATE),**零新 API 面**;伤害/判定/耗层一律不碰,四条全是纯演出。

    private static boolean swOff() { return !YongyeConfig.get().swordsmanFancyFx; }

    private static double swScale() {
        return MathHelper.clamp(YongyeConfig.get().swordsmanFxScale, 0.2, 3.0);
    }

    /**
     * 视线的水平右向量。直视天顶/脚底时 look×(0,1,0) 退化成零向量、normalize() 会返回 ZERO
     * 把整个扇面压成一点,所以这里用偏航角兜一个任意水平单位向量(此时任何水平方向都与视线正交)。
     */
    private static Vec3d rightOf(Vec3d look, float yawDeg) {
        Vec3d r = look.crossProduct(new Vec3d(0, 1, 0));
        if (r.lengthSquared() < 1.0E-6) {
            double a = Math.toRadians(yawDeg);
            r = new Vec3d(-Math.sin(a), 0, Math.cos(a));
        }
        return r.normalize();
    }

    /**
     * 剑气波(作者 skill.剑气波_sword_qi_wave 的出刀段):一道冰蓝横扇自身前推出到 range,
     * 越远越宽、两端后掠下垂,第 5t 一记重音(原 0.82 那一拍),末端拍散成星屑。
     * 此前这一招只有身前 1.5 格处 6 颗 SWEEP、**连音效都没有**——范围打到 4~6 格却看不见任何东西。
     */
    public static void swordQiWave(ServerWorld w, ServerPlayerEntity p, double range) {
        if (swOff()) return;
        final double s = swScale();
        final Vec3d eye = p.getEyePos().add(0, -0.25, 0);
        final Vec3d look = p.getRotationVector().normalize();
        final Vec3d right = rightOf(look, p.getYaw());
        final int ticks = 9;
        final double step = Math.max(1.0, range) / ticks;

        w.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.9f, 1.45f);

        run(ticks, t -> {
            double d = step * (t + 1);
            Vec3d c = eye.add(look.multiply(d));
            double wid = 1.2 + d * 0.42;                                 // 横扇越推越宽
            int m = n(7, s);
            for (int i = -m; i <= m; i++) {
                double f = i / (double) m;                               // -1..1 横向参数
                Vec3d q = c.add(right.multiply(f * wid))
                        .add(look.multiply(-Math.abs(f) * 1.1))          // 两端后掠成弯月
                        .add(new Vec3d(0, -f * f * 0.35, 0));            // 扇面两端下垂
                w.spawnParticles(ParticleTypes.SWEEP_ATTACK, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                if (i % 2 == 0) {
                    w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, q.x, q.y, q.z, 1, 0.04, 0.04, 0.04, 0.01);
                }
            }
            if (t == 4) {                                                // 原 0.82 那一记重音
                w.spawnParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, n(14, s), 0.5, 0.35, 0.5, 0.12);
                w.playSound(null, c.x, c.y, c.z,
                        SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.7f, 1.5f);
            }
            if (t == ticks - 1) {                                        // 末端拍散
                w.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, n(10, s), wid * 0.5, 0.3, wid * 0.5, 0.06);
            }
        });
    }

    /**
     * 招架反弹(作者 skill.招架反弹_parry_reflect,六拍全取:0 接触 / 0.24 卸力 / 0.43 反弹 /
     * 0.58 二段余劲 / 0.8 回位 / 1.05 收):面向攻击者立起一面冰蓝刃壁 → 向内收束卸力 →
     * 沿连线把力打回去 → 余劲 → 归位。此前这一招**只有一声盾响、零粒子**,看不出「挡住了」还是「挨了」。
     */
    public static void swordParry(ServerWorld w, ServerPlayerEntity p, Vec3d attackerPos) {
        if (swOff()) return;
        final double s = swScale();
        final Vec3d self = p.getPos().add(0, p.getStandingEyeHeight() * 0.6, 0);
        final Vec3d to = attackerPos.subtract(self);
        final Vec3d dir = (to.lengthSquared() < 1.0E-4 ? p.getRotationVector() : to).normalize();
        final Vec3d right = rightOf(dir, p.getYaw());
        final double dist = Math.min(6.0, to.length());
        final int ticks = 21;

        run(ticks, t -> {
            Vec3d wall = self.add(dir.multiply(1.1));
            if (t == 0) {                                                // 接触:刃壁立起
                for (int i = -3; i <= 3; i++) {
                    for (int j = 0; j <= 2; j++) {
                        Vec3d q = wall.add(right.multiply(i * 0.28)).add(new Vec3d(0, j * 0.32 - 0.32, 0));
                        w.spawnParticles(ParticleTypes.ENCHANTED_HIT, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
                    }
                }
            } else if (t == 4) {                                         // 卸力:向内收束
                ring(w, wall, 0.75, ParticleTypes.SOUL_FIRE_FLAME, n(12, s), 0.0);
            } else if (t == 8) {                                         // 反弹:沿连线把力送回去
                int seg = Math.max(2, (int) (dist * 2));
                for (int i = 0; i <= seg; i++) {
                    Vec3d q = self.add(dir.multiply(dist * i / (double) seg));
                    w.spawnParticles(ParticleTypes.CRIT, q.x, q.y, q.z, n(2, s), 0.06, 0.06, 0.06, 0.02);
                }
                w.playSound(null, p.getX(), p.getY(), p.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 0.7f, 1.6f);
            } else if (t == 12) {                                        // 二段余劲
                w.spawnParticles(ParticleTypes.END_ROD, wall.x, wall.y, wall.z, n(8, s), 0.4, 0.3, 0.4, 0.05);
            } else if (t == 16) {                                        // 回位
                ring(w, self, 0.9, ParticleTypes.SOUL_FIRE_FLAME, n(8, s), 0.2);
            }
        });
    }

    /**
     * 剑气凌空(作者 skill.剑气凌空_aerial_slash,五条动画里唯一没有 particles 通道的一条=纯刃势,
     * 所以这里把「光刃本体」当主角):0→6t 光刃沿视线逐段拉出、6→13t 留痕加粗、13→20t 崩解成刃屑、
     * 20→25t 消散。弹道在发射瞬间快照(伤害本就沿这条线结算过了,人走开光刃也该留在原处)。
     * 此前这一招是**在同一 tick 里每格画 1 颗 SWEEP** —— 12 格的穿透只闪一下,几乎看不见。
     */
    public static void swordAerial(ServerWorld w, ServerPlayerEntity p, double range) {
        if (swOff()) return;
        final double s = swScale();
        final Vec3d base = p.getPos().add(0, p.getStandingEyeHeight() * 0.9, 0);
        final Vec3d dir = p.getRotationVector().normalize();
        final Vec3d right = rightOf(dir, p.getYaw());
        final double len = Math.max(2.0, range);
        final int ticks = 25;

        run(ticks, t -> {
            if (t < 6) {                                                 // 光刃逐段拉出
                double f0 = t / 6.0, f1 = (t + 1) / 6.0;
                for (double d = len * f0; d < len * f1; d += 0.5) {
                    Vec3d q = base.add(dir.multiply(d));
                    w.spawnParticles(ParticleTypes.SWEEP_ATTACK, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.0);
                    w.spawnParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, n(1, s), 0.05, 0.05, 0.05, 0.01);
                }
                if (t == 5) {                                            // 拉到底:一记轻脆确认
                    Vec3d tip = base.add(dir.multiply(len));
                    w.playSound(null, tip.x, tip.y, tip.z,
                            SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.5f, 1.7f);
                }
            } else if (t < 13) {                                         // 留痕:刃身向两侧鼓出
                double off = 0.09 * (t - 5);
                for (double d = 0.5; d < len; d += 1.5) {
                    Vec3d c = base.add(dir.multiply(d));
                    for (int k = -1; k <= 1; k += 2) {
                        Vec3d q = c.add(right.multiply(k * off));
                        w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.0);
                    }
                }
            } else if (t < 20) {                                         // 崩解:碎成飘散刃屑
                for (double d = 1.0; d < len; d += 2.0) {
                    Vec3d c = base.add(dir.multiply(d));
                    w.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, n(1, s), 0.25, 0.25, 0.25, 0.03);
                }
            } else if (t % 2 == 0) {                                     // 消散
                Vec3d c = base.add(dir.multiply(len * 0.5));
                w.spawnParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z,
                        n(2, s), len * 0.25, 0.3, len * 0.25, 0.01);
            }
        });
    }

    /**
     * 剑气层气场(作者 skill.身法如风_windstep 是五条里唯一的 loop 动画,所以它对位的不是某一招、
     * 而是「剑气层」这个**持续状态**:24t 转一圈,环上光点数=当前层数)。
     *
     * <p>为什么值得做:剑气层此前只喂 MP 条上的数字,世界里毫无表现——而攒够层意味着
     * 「剑气凌空」已上膛,这件事只有盯着 HUD 才知道。上膛档改用 END_ROD 白芒 + 一记高音铃
     * (pitch 1.8,与武僧金钟罩的同一音效 pitch 1.0 明显错开),抬眼就能看出上没上膛。
     *
     * <p><b>为什么上膛档是 9 层而不是 10</b>:加层发生在同一次命中事件的前半段,凌空判定在后半段读
     * ≥10 —— 也就是说层数刚变成 10 的**那一刀自己就洞穿了**,10 层从来不会停留一刀的时间;而且
     * min(10,…) 封顶后每刀都会再次得到 10,拿 10 当触发档会变成每刀重复放。所以「下一刀(蓄满时)
     * 洞穿」的正确播报点是层数刚到 9。
     *
     * <p>调用侧只在层数 == 1 / 5 / 9 三档触发,不是每次命中都放——否则连击时任务会层层叠加。
     */
    public static void swordEdgeAura(ServerWorld w, ServerPlayerEntity p, int stacks, boolean armed) {
        if (swOff() || !YongyeConfig.get().swordsmanEdgeAuraFx) return;
        final double s = swScale();
        final int k = Math.max(1, Math.min(10, stacks));
        final boolean full = armed;
        final int ticks = 24;

        run(ticks, t -> {
            double a = Math.PI * 2 * t / (double) ticks;
            double r = 0.85 + 0.05 * k;
            for (int i = 0; i < k; i++) {
                double ang = a + Math.PI * 2 * i / k;
                double y = 0.35 + 0.55 * (0.5 + 0.5 * Math.sin(a * 2 + i));
                w.spawnParticles(full ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME,
                        p.getX() + Math.cos(ang) * r, p.getY() + y, p.getZ() + Math.sin(ang) * r,
                        n(1, s), 0.02, 0.02, 0.02, 0.0);
            }
            if (full && t == 0) {
                w.playSound(null, p.getX(), p.getY(), p.getZ(),
                        SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 0.7f, 1.8f);
            }
        });
    }
}
