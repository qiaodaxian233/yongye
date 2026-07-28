package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 精英战斗 AI(m309,作者点名)。由 {@link EliteHandler#register()} 的 tick 循环逐帧驱动,
 * 全部叠在原版 AI 之上(速度脉冲/寻路改道),零新 Goal、零 mixin:
 *
 * <ul>
 *   <li><b>跳劈</b>——持武器的精英(第 N 天配刀、或抢了玩家武器的)中距离起跳扑向目标,
 *       落地对脚下重击:伤害=其攻击力 × eliteLeapDamageMult(比平砍高),附带击退与烟尘。
 *       起跳/落地判定照抄 m268 红蜘蛛猛扑(addVelocity + grace + isOnGround)。</li>
 *   <li><b>精英骷髅走位</b>——目标在射程内时左右横移(每 2 秒换向,按实体 id 错相,
 *       一群小白不会集体同步跳舞)、贴脸(&lt;5 格)后撤拉开、偶尔小跳,纯速度脉冲不与
 *       原版 BowAttackGoal 抢移动控制。</li>
 *   <li><b>精英苦力怕自爆翻倍</b>——m304 单击钳制同款「取消+重放」:爆炸伤害 × eliteCreeperDamageMult,
 *       重放守卫防递归。苦力怕只通过自爆造成伤害,故不需再判伤害类型。</li>
 *   <li><b>血量低逃跑、回复了又来</b>——血量 &lt; eliteFleeHealthFraction 撒腿跑(清目标+背向最近
 *       玩家寻路逃离+速度Ⅱ),边逃边回血(每秒 最大生命×eliteFleeRegenPerSecond);
 *       回到 eliteFleeReturnFraction(或逃超 eliteFleeMaxTicks)→ 咆哮杀回,重新锁定玩家。
 *       苦力怕不逃——它的活法是自爆。逃跑接管当 tick,精英的远程技能/瞬移/感知全部暂停。</li>
 *   <li><b>跳搭</b>——近战精英(非骷髅/女巫/蜘蛛——前两者远程、蜘蛛会爬墙)发现目标在头顶
 *       (高差≥2.5、水平≤5.5)时原地起跳、越过起跳格瞬间在脚下垫圆石,两跳间隔
 *       eliteBuildIntervalTicks(默 8t≈每秒 2.5 格,作者点名「速度很快」);受 mobGriefing
 *       游戏规则约束,头顶两格内有方块不起跳(防撞头死循环)。</li>
 * </ul>
 */
public final class EliteCombatAI {
    private EliteCombatAI() {}

    // ===== 跳劈状态(照抄 m268 猛扑三件套) =====
    private static final Set<MobEntity> LEAP_AIR = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<MobEntity, Integer> LEAP_GRACE = new WeakHashMap<>();
    private static final Map<MobEntity, Integer> NEXT_LEAP_AGE = new WeakHashMap<>();

    // ===== 逃跑状态 =====
    private static final Set<MobEntity> FLEEING = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<MobEntity, Integer> FLEE_START_AGE = new WeakHashMap<>();
    private static final Map<MobEntity, Integer> FLEE_LOCK_AGE = new WeakHashMap<>(); // 回归后短期不再逃,防打摆子

    // ===== 跳搭状态 =====
    private static final Map<MobEntity, BlockPos> PILLAR_BASE = new WeakHashMap<>();
    private static final Map<MobEntity, Integer> PILLAR_GRACE = new WeakHashMap<>();
    private static final Map<MobEntity, Integer> NEXT_PILLAR_AGE = new WeakHashMap<>();

    // ===== 苦力怕翻倍重放守卫(m304 同款) =====
    private static final Set<UUID> CREEPER_REAPPLY = new HashSet<>();

    public static void register() {
        // —— 精英苦力怕自爆伤害翻倍(作者点名):取消原伤害,按倍率重放一次 ——
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (cfg.eliteCreeperDamageMult <= 1.0001) return true;
            if (!(source.getAttacker() instanceof CreeperEntity c)) return true;
            if (!c.getAttachedOrElse(ModAttachments.IS_ELITE, false)) return true;
            if (entity.getWorld().isClient) return true;
            if (CREEPER_REAPPLY.contains(entity.getUuid())) return true; // 重放:放行
            CREEPER_REAPPLY.add(entity.getUuid());
            try {
                entity.damage(source, (float) (amount * cfg.eliteCreeperDamageMult));
            } finally {
                CREEPER_REAPPLY.remove(entity.getUuid());
            }
            return false;
        });
        Yongye.LOGGER.info("[夜蚀] 精英战斗AI已挂载(跳劈/走位/自爆翻倍/逃跑回归/跳搭)");
    }

    /**
     * EliteHandler.tickElite 每 tick 调用。返回 true = 逃跑接管本 tick,
     * 调用方应直接 return(不感知、不放远程技能、不瞬移——逃命就是逃命)。
     */
    public static boolean tick(ServerWorld sw, MobEntity e, YongyeConfig cfg) {
        if (tickFlee(sw, e, cfg)) return true;

        LivingEntity t = e.getTarget();
        tickLeap(sw, e, t, cfg);
        if (LEAP_AIR.contains(e)) return false; // 空中人:不叠走位/跳搭
        tickStrafe(e, t, cfg);
        tickPillar(sw, e, t, cfg);
        return false;
    }

    // ==================== 逃跑 / 回归 ====================

    private static boolean tickFlee(ServerWorld sw, MobEntity e, YongyeConfig cfg) {
        if (!cfg.eliteFleeEnable) return false;
        if (e instanceof CreeperEntity) return false; // 苦力怕不逃:它的活法是自爆

        boolean fleeing = FLEEING.contains(e);
        if (!fleeing) {
            if (e.getHealth() >= e.getMaxHealth() * cfg.eliteFleeHealthFraction) return false;
            if (e.age < FLEE_LOCK_AGE.getOrDefault(e, 0)) return false; // 刚回归,先打一会
            FLEEING.add(e);
            FLEE_START_AGE.put(e, e.age);
            LEAP_AIR.remove(e); // 逃跑打断跳劈
        }

        // 边逃边回血 + 速度Ⅱ(不然逃不过玩家疾跑)
        e.heal((float) (e.getMaxHealth() * cfg.eliteFleeRegenPerSecond / 20.0));
        if (e.age % 20 == 0) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 30, 1, true, false, false));
        }
        e.setTarget(null); // 每 tick 压掉原版重新锁定

        PlayerEntity threat = sw.getClosestPlayer(e.getX(), e.getY(), e.getZ(), 24.0, true);
        if (threat != null && e.age % 10 == 0) {
            Vec3d away = new Vec3d(e.getX() - threat.getX(), 0, e.getZ() - threat.getZ());
            if (away.lengthSquared() < 0.01) away = new Vec3d(1, 0, 0);
            away = away.normalize();
            double ax = e.getX() + away.x * 12.0, az = e.getZ() + away.z * 12.0;
            if (!e.getNavigation().startMovingTo(ax, e.getY(), az, 1.45) || e.getNavigation().isIdle()) {
                e.getMoveControl().moveTo(ax, e.getY(), az, 1.45); // 找不到路也直线跑
            }
        }
        if (e.age % 8 == 0) {
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.2, e.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
        }

        boolean recovered = e.getHealth() >= e.getMaxHealth() * cfg.eliteFleeReturnFraction;
        boolean timeout = e.age - FLEE_START_AGE.getOrDefault(e, e.age) > Math.max(40, cfg.eliteFleeMaxTicks);
        if (recovered || timeout) {
            // —— 回复了,又来:咆哮 + 怒焰,重新锁定玩家杀回 ——
            FLEEING.remove(e);
            FLEE_LOCK_AGE.put(e, e.age + 100); // 5 秒内不再逃,防临界血量打摆子
            sw.playSound(null, e.getX(), e.getY(), e.getZ(),
                    SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.2f, 1.4f);
            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ(), 16, 0.4, 0.5, 0.4, 0.05);
            PlayerEntity back = sw.getClosestPlayer(e.getX(), e.getY(), e.getZ(), cfg.eliteSenseRadius, true);
            if (back != null) e.setTarget(back);
            return false; // 本 tick 起恢复正常行为
        }
        return true; // 逃跑接管
    }

    // ==================== 跳劈 ====================

    private static void tickLeap(ServerWorld sw, MobEntity e, LivingEntity t, YongyeConfig cfg) {
        // —— 落地结算(m268 猛扑同款:grace 防起跳瞬间误判 onGround) ——
        if (LEAP_AIR.contains(e)) {
            int g = LEAP_GRACE.getOrDefault(e, 0);
            if (g > 0) {
                LEAP_GRACE.put(e, g - 1);
            } else if (e.isOnGround()) {
                LEAP_AIR.remove(e);
                sw.spawnParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.2, e.getZ(), 18, 1.1, 0.15, 1.1, 0.04);
                sw.playSound(null, e.getX(), e.getY(), e.getZ(),
                        SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 1.2f, 0.8f);
                sw.playSound(null, e.getX(), e.getY(), e.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.2f, 0.7f);
                double dmg = e.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * cfg.eliteLeapDamageMult;
                LivingEntity tgt = e.getTarget();
                for (LivingEntity v : sw.getEntitiesByClass(LivingEntity.class,
                        e.getBoundingBox().expand(2.6, 1.5, 2.6),
                        v -> v != e && v.isAlive() && (v instanceof PlayerEntity || v == tgt))) {
                    v.damage(sw.getDamageSources().mobAttack(e), (float) dmg);
                    double dx = v.getX() - e.getX(), dz = v.getZ() - e.getZ();
                    v.takeKnockback(0.7, -dx, -dz);
                    if (v instanceof ServerPlayerEntity sp)
                        sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(v));
                }
            }
            return;
        }

        // —— 起跳:持武器(第 N 天配刀/抢来的都算)、中距离、冷却毕 ——
        if (!cfg.eliteLeapAttack || t == null || !t.isAlive() || !e.isOnGround()) return;
        if (!EquipmentEnhancer.isWeapon(e.getMainHandStack())) return;
        if (e.age < NEXT_LEAP_AGE.getOrDefault(e, 0)) return;
        double d = e.distanceTo(t);
        if (d < 3.0 || d > Math.max(3.5, cfg.eliteLeapMaxRange)) return;

        Vec3d dir = new Vec3d(t.getX() - e.getX(), 0, t.getZ() - e.getZ()).normalize();
        double f = Math.min(1.35, 0.45 + d * 0.11);
        e.addVelocity(dir.x * f, 0.55, dir.z * f);
        e.velocityModified = true;
        LEAP_AIR.add(e);
        LEAP_GRACE.put(e, 8);
        NEXT_LEAP_AGE.put(e, e.age + Math.max(20, cfg.eliteLeapCooldownTicks));
        sw.playSound(null, e.getX(), e.getY(), e.getZ(),
                SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.HOSTILE, 1.1f, 0.6f);
        sw.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 0.8, e.getZ(), 8, 0.3, 0.4, 0.3, 0.08);
    }

    // ==================== 精英骷髅走位 ====================

    private static void tickStrafe(MobEntity e, LivingEntity t, YongyeConfig cfg) {
        if (!cfg.eliteSkeletonStrafe || !(e instanceof AbstractSkeletonEntity)) return;
        if (t == null || !t.isAlive() || !e.isOnGround()) return;
        double d = e.distanceTo(t);
        if (d > 14.0 || e.age % 5 != 0) return;

        Vec3d to = new Vec3d(t.getX() - e.getX(), 0, t.getZ() - e.getZ());
        if (to.lengthSquared() < 0.01) return;
        to = to.normalize();
        Vec3d side = new Vec3d(-to.z, 0, to.x);
        // 每 2 秒换一次侧移方向;按实体 id 错相,一群精英小白不会集体同步跳同一支舞
        int dir = ((e.age / 40) + (e.getId() & 1)) % 2 == 0 ? 1 : -1;
        double s = cfg.eliteStrafeImpulse;
        double back = d < 5.0 ? -0.28 : (d > 11.0 ? 0.10 : 0.0); // 贴脸后撤拉开,过远微微逼近
        e.addVelocity(side.x * s * dir + to.x * back, 0, side.z * s * dir + to.z * back);
        e.velocityModified = true;
        if (e.getRandom().nextFloat() < 0.12f) { // 偶尔小跳,箭雨里像个活人
            e.addVelocity(0, 0.35, 0);
            e.velocityModified = true;
        }
    }

    // ==================== 跳搭(垫方块爬高) ====================

    private static void tickPillar(ServerWorld sw, MobEntity e, LivingEntity t, YongyeConfig cfg) {
        // —— 空中落块:越过起跳格 1 格即在脚下垫圆石,落上去就高了一格 ——
        BlockPos base = PILLAR_BASE.get(e);
        if (base != null) {
            if (e.getY() >= base.getY() + 1.05) {
                if (sw.getBlockState(base).isAir()) {
                    sw.setBlockState(base, Blocks.COBBLESTONE.getDefaultState());
                    // 【待编译验证】BLOCK_STONE_PLACE(标准常量首用;报错删掉这一句即无声垫块)
                    sw.playSound(null, base.getX(), base.getY(), base.getZ(),
                            SoundEvents.BLOCK_STONE_PLACE, SoundCategory.HOSTILE, 0.8f, 0.9f);
                }
                PILLAR_BASE.remove(e);
            } else {
                int g = PILLAR_GRACE.getOrDefault(e, 0);
                if (g > 0) PILLAR_GRACE.put(e, g - 1);
                else if (e.isOnGround()) PILLAR_BASE.remove(e); // 起跳被顶/被撞,放弃本次
            }
            return;
        }

        // —— 起跳:近战精英、目标在头顶、头上有净空、mobGriefing 允许 ——
        if (!cfg.eliteBuildBlocks || t == null || !t.isAlive() || !e.isOnGround()) return;
        if (e instanceof AbstractSkeletonEntity || e instanceof WitchEntity || e instanceof SpiderEntity) return;
        if (e.age < NEXT_PILLAR_AGE.getOrDefault(e, 0)) return;
        // 【待编译验证】GameRules.DO_MOB_GRIEFING(常量首用;KEEP_INVENTORY 同族取法在树已过编译。报错把本行删掉即不受规则约束)
        if (!sw.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_GRIEFING)) return;

        double dy = t.getY() - e.getY();
        double dhx = t.getX() - e.getX(), dhz = t.getZ() - e.getZ();
        double dh = Math.sqrt(dhx * dhx + dhz * dhz);
        if (dy < 2.5 || dh > 5.5) return;
        BlockPos feet = e.getBlockPos();
        if (!sw.getBlockState(feet.up(2)).isAir() || !sw.getBlockState(feet.up(3)).isAir()) return; // 防撞头死循环

        e.setVelocity(e.getVelocity().x * 0.1, 0.52, e.getVelocity().z * 0.1); // 原地直上,不漂
        e.velocityModified = true;
        PILLAR_BASE.put(e, feet);
        PILLAR_GRACE.put(e, 6);
        NEXT_PILLAR_AGE.put(e, e.age + Math.max(4, cfg.eliteBuildIntervalTicks));
    }
}
