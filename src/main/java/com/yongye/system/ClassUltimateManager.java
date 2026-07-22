package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.PlayerClass;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 职业大招(m47,主动技能)。按键 → ClassUltimatePayload → 此处施放本命职业(第一职业)的主动技能,带冷却。
 * 全部用已确认的 Fabric/原版 API(getEntitiesByClass / damage+timeUntilRegen / 状态效果 / 粒子声音),不依赖 mixin。
 */
public final class ClassUltimateManager {
    private ClassUltimateManager() {}

    private static final Map<UUID, Long> cooldownUntil = new HashMap<>();

    /** m224:外部缩减大招冷却(晚安·极限生电光环用)。 */
    public static void reduceCooldown(UUID id, long ticks) {
        cooldownUntil.computeIfPresent(id, (k, v) -> v - ticks);
    }

    public static void use(ServerPlayerEntity p) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableClassUltimate) { msg(p, "职业大招未启用", Formatting.RED); return; }

        // 本命职业 = 第一职业,且需当前生效
        List<String> learned = ClassManager.learnedList(p);
        if (learned.isEmpty()) { msg(p, "你还没有职业", Formatting.RED); return; }
        PlayerClass c = PlayerClass.byId(learned.get(0));
        if (c == null || !ClassManager.isActive(p, c)) { msg(p, "本命职业当前未生效", Formatting.RED); return; }

        long now = p.getWorld().getTime();
        long until = cooldownUntil.getOrDefault(p.getUuid(), 0L);
        if (now < until) {
            long left = (until - now) / 20 + 1;
            msg(p, "大招冷却中:" + left + " 秒", Formatting.GRAY);
            return;
        }

        boolean ok = cast(p, c, cfg);
        if (ok) {
            cooldownUntil.put(p.getUuid(), now + Math.max(20L, cfg.ultimateCooldownTicks));
        }
    }

    private static boolean cast(ServerPlayerEntity p, PlayerClass c, YongyeConfig cfg) {
        ServerWorld sw = (ServerWorld) p.getWorld();
        DamageSource src = sw.getDamageSources().playerAttack(p);
        switch (c) {
            case WARRIOR -> {
                // 旋风斩:周身范围一击
                int hit = aoe(p, sw, src, cfg.ultWarriorRadius, (float) (cfg.ultWarriorDamage + atk(p) * cfg.ultWarriorAttackRatio), null);
                burst(sw, p, ParticleTypes.SWEEP_ATTACK, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP);
                msg(p, "旋风斩!命中 " + hit + " 个目标", Formatting.GOLD);
            }
            case TANK -> {
                // 不动如山:强抗性+吸收,并嘲讽周围
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, cfg.ultTankDurationTicks, 2, true, false, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, cfg.ultTankDurationTicks, 3, true, false, true));
                int n = 0;
                for (HostileEntity mob : sw.getEntitiesByClass(HostileEntity.class, box(p, cfg.ultTankRadius), e -> e.isAlive())) {
                    mob.setTarget(p);
                    n++;
                }
                burst(sw, p, ParticleTypes.ENCHANTED_HIT, SoundEvents.ITEM_SHIELD_BLOCK);
                msg(p, "不动如山!嘲讽 " + n + " 个,获得抗性III+吸收IV", Formatting.GOLD);
            }
            case ASSASSIN -> {
                // 影遁:短暂隐身+迅捷,脱战突袭
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, cfg.ultAssassinDurationTicks, 0, true, false, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, cfg.ultAssassinDurationTicks, 2, true, false, true));
                burst(sw, p, ParticleTypes.CLOUD, SoundEvents.ENTITY_PHANTOM_FLAP);
                msg(p, "影遁!隐身+迅捷III", Formatting.GOLD);
            }
            case WARLOCK -> {
                // 灭世:大范围魔法爆发(消耗生命)
                if (p.getHealth() <= cfg.ultWarlockHpCost + 1.0f) { msg(p, "生命不足,无法施放", Formatting.RED); return false; }
                p.setHealth(Math.max(1.0f, p.getHealth() - (float) cfg.ultWarlockHpCost));
                int hit = aoe(p, sw, sw.getDamageSources().magic(), cfg.ultWarlockRadius, (float) (cfg.ultWarlockDamage + atk(p) * cfg.ultWarlockAttackRatio), null);
                burst(sw, p, ParticleTypes.SOUL_FIRE_FLAME, SoundEvents.ENTITY_WITHER_SPAWN);
                msg(p, "灭世!焚尽 " + hit + " 个目标(献祭生命)", Formatting.GOLD);
            }
            case SUMMONER -> {
                // 癫狂(m232 起大招专职此技,不再需要潜行;铁傀儡召唤挪到小技能键,独立冷却):
                // 献祭生命 → 力量+速度,并请五位朋友(肝帝天团)前来助阵
                if (p.getHealth() <= cfg.ultSummonerFrenzyHpCost + 1.0f) { msg(p, "生命不足,无法癫狂", Formatting.RED); return false; }
                p.setHealth(Math.max(1.0f, p.getHealth() - (float) cfg.ultSummonerFrenzyHpCost));
                // m233:增益等级可配,默认力量III(amp 2)+速度II(amp 1)——三技能职业,每一发都要够硬
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, cfg.ultSummonerFrenzyDurationTicks, Math.max(0, cfg.ultSummonerFrenzyPowerAmp), true, false, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, cfg.ultSummonerFrenzyDurationTicks, Math.max(0, cfg.ultSummonerFrenzySpeedAmp), true, false, true));
                int got = SummonerHandler.summonGanDi(p);
                burst(sw, p, ParticleTypes.SOUL_FIRE_FLAME, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON);
                msg(p, got > 0 ? "癫狂!朋友们来助阵了:岛风·晚安·不爱肝·迷人·芥末!(并肩作战 " + cfg.gandiLifeSec + " 秒;小技能键=召唤傀儡)"
                               : "癫狂!献祭生命,力量+速度暴涨", Formatting.GOLD);
            }
            case MONK -> {
                // 百裂拳:周身连击+强力击退
                int hit = aoe(p, sw, src, cfg.ultMonkRadius, (float) (cfg.ultMonkDamage + atk(p) * cfg.ultMonkAttackRatio), p.getPos());
                burst(sw, p, ParticleTypes.SWEEP_ATTACK, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG);
                msg(p, "百裂拳!击退并重创 " + hit + " 个目标", Formatting.GOLD);
            }
            case SWORDSMAN -> {
                // 万剑归一:前方大范围剑气
                Vec3d dir = p.getRotationVector();
                double r = cfg.ultSwordsmanRange;
                Box wave = p.getBoundingBox().expand(r).offset(dir.x * r, 0, dir.z * r);
                float swDmg = (float) (cfg.ultSwordsmanDamage + atk(p) * cfg.ultSwordsmanAttackRatio);
                int hit = 0;
                for (LivingEntity le : sw.getEntitiesByClass(LivingEntity.class, wave,
                        e -> e.isAlive() && e != p && !(e instanceof PlayerEntity))) {
                    le.damage(src, swDmg);
                    le.timeUntilRegen = 0;
                    hit++;
                }
                Vec3d cc = p.getPos().add(dir.x * 2, p.getStandingEyeHeight() * 0.6, dir.z * 2);
                sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, cc.x, cc.y, cc.z, 20, 1.2, 0.4, 1.2, 0.0);
                sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.8f);
                msg(p, "万剑归一!洞穿 " + hit + " 个目标", Formatting.GOLD);
            }
        }
        return true;
    }

    /** 以玩家为中心的范围伤害;knockFrom != null 时附带远离该点的击退。 */
    private static int aoe(ServerPlayerEntity p, ServerWorld sw, DamageSource src, double radius, float dmg, Vec3d knockFrom) {
        int n = 0;
        for (LivingEntity le : sw.getEntitiesByClass(LivingEntity.class, box(p, radius),
                e -> e.isAlive() && e != p && !(e instanceof PlayerEntity))) {
            le.damage(src, dmg);
            le.timeUntilRegen = 0;
            if (knockFrom != null) {
                Vec3d kb = le.getPos().subtract(knockFrom);
                le.takeKnockback(1.2, -kb.x, -kb.z);
            }
            n++;
        }
        return n;
    }

    private static Box box(ServerPlayerEntity p, double r) {
        return p.getBoundingBox().expand(r);
    }

    /** m234:玩家当前攻击力——伤害型大招统一「基础值+攻击×倍率」,后期不脱节(照 m72 武器技能公式)。 */
    private static double atk(ServerPlayerEntity p) {
        return p.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
    }

    private static void burst(ServerWorld sw, ServerPlayerEntity p, net.minecraft.particle.ParticleEffect particle,
                              net.minecraft.sound.SoundEvent sound) {
        sw.spawnParticles(particle, p.getX(), p.getBodyY(0.6), p.getZ(), 28, 1.4, 0.6, 1.4, 0.1);
        sw.playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static void msg(ServerPlayerEntity p, String s, Formatting f) {
        p.sendMessage(Text.literal(s).formatted(f), true);
    }
}
