package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.item.PlayerClass;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
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
 * 职业小技能(m232)。按键(默认 C)→ ClassMinorSkillPayload → 此处施放本命职业的小技能。
 * 与大招(ClassUltimateManager)完全独立:各自冷却互不占用——铁傀儡召唤从大招挪到这里,
 * 应作者「召唤铁傀儡不该占用大招 CD」+「每个职业都设计一个小技能」。
 * 全部套用大招管理器的已验证写法(getEntitiesByClass / damage+timeUntilRegen / 状态效果 /
 * 粒子声音 / EntityVelocityUpdateS2CPacket 速度同步=PursuitHandler m152 先例),零新 API 面。
 *
 * 七职业小技能:
 *  肉盾·盾击   = 周身小范围重击+击退+缓慢(自带控制的小输出)
 *  战士·战吼   = 震慑周围(虚弱+缓慢),自身短时力量
 *  术士·暗影分身(m262,原生命虹吸) = 召唤 2 个分身(主人 50% 血 / 100% 攻,30 秒)
 *  剑客·剑气斩  = 前方短距剑气(万剑归一的迷你版)
 *  武僧·金钟罩  = 短时抗性II+回复(防御脉冲)
 *  刺客·疾影步  = 向前猛冲+短暂加速(服务端改速度必须发同步包,客户端权威)
 *  召唤师·召唤  = 5 座强化铁傀儡(独立冷却;大招 X 专职癫狂)
 */
public final class ClassMinorSkillManager {
    private ClassMinorSkillManager() {}

    private static final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public static void use(ServerPlayerEntity p) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableClassMinorSkill) { msg(p, "职业小技能未启用", Formatting.RED); return; }

        List<String> learned = ClassManager.learnedList(p);
        if (learned.isEmpty()) { msg(p, "你还没有职业", Formatting.RED); return; }
        PlayerClass c = ClassManager.effectiveMain(p);   // m336:跟手——拿着啥职业武器就放啥职业的招
        if (c == null || !ClassManager.isActive(p, c)) { msg(p, "本命职业当前未生效", Formatting.RED); return; }

        long now = p.server.getTicks();   // m321:与武器技能(WeaponSkillManager)统一时基,三处 CD 同口径
        long until = cooldownUntil.getOrDefault(p.getUuid(), 0L);
        if (now < until) {
            long left = (until - now) / 20 + 1;
            msg(p, "小技能冷却中:" + left + " 秒", Formatting.GRAY);
            return;
        }

        boolean ok = cast(p, c, cfg);
        if (ok) {
            cooldownUntil.put(p.getUuid(), now + Math.max(20L, cfg.minorSkillCooldownTicks));
        }
    }

    private static boolean cast(ServerPlayerEntity p, PlayerClass c, YongyeConfig cfg) {
        ServerWorld sw = (ServerWorld) p.getWorld();
        DamageSource src = sw.getDamageSources().playerAttack(p);
        switch (c) {
            case TANK -> {
                // 盾击:周身小范围重击 + 击退 + 缓慢
                int hit = 0;
                for (LivingEntity le : sw.getEntitiesByClass(LivingEntity.class, box(p, cfg.minorTankRadius),
                        e -> e.isAlive() && e != p && !(e instanceof PlayerEntity))) {
                    le.damage(src, (float) (cfg.minorTankDamage + atk(p) * cfg.minorTankAttackRatio));
                    le.timeUntilRegen = 0;
                    Vec3d kb = le.getPos().subtract(p.getPos());
                    le.takeKnockback(1.0, -kb.x, -kb.z);
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1, true, false, true));
                    hit++;
                }
                burst(sw, p, ParticleTypes.CRIT, SoundEvents.ITEM_SHIELD_BLOCK);
                msg(p, "盾击!击退并减速 " + hit + " 个目标", Formatting.AQUA);
            }
            case WARRIOR -> {
                // 战吼:震慑周围怪物(虚弱+缓慢),自身短时力量
                int n = 0;
                for (HostileEntity mob : sw.getEntitiesByClass(HostileEntity.class, box(p, cfg.minorWarriorRadius), e -> e.isAlive())) {
                    mob.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, cfg.minorWarriorDurationTicks, 0, true, false, true));
                    mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, cfg.minorWarriorDurationTicks, 0, true, false, true));
                    n++;
                }
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, cfg.minorWarriorDurationTicks, 0, true, false, true));
                burst(sw, p, ParticleTypes.ANGRY_VILLAGER, SoundEvents.ENTITY_RAVAGER_ROAR);
                msg(p, "战吼!震慑 " + n + " 个目标,自身力量提升", Formatting.AQUA);
            }
            case WARLOCK -> {
                // m262:改「暗影分身」(作者点名替换生命虹吸)——召唤 N 个分身,
                // 血量=主人×50%、攻击=主人×100%(快照,均可配),寿命 30 秒,出生继承主人当前仇恨目标。
                int count = Math.max(1, cfg.minorWarlockCloneCount);
                var target = p.getAttacking() instanceof net.minecraft.entity.LivingEntity t && t.isAlive() ? t : null;
                int spawned = 0;
                for (int i = 0; i < count; i++) {
                    double ang = Math.PI * 2 * i / count + 0.8;
                    var clone = new com.yongye.entity.WarlockCloneEntity(
                            com.yongye.registry.ModEntities.WARLOCK_CLONE, sw);
                    clone.refreshPositionAndAngles(p.getX() + Math.cos(ang) * 1.6, p.getY(),
                            p.getZ() + Math.sin(ang) * 1.6, p.getYaw(), 0);
                    clone.snapshotFrom(p);
                    if (target != null) clone.setTarget(target);
                    if (sw.spawnEntity(clone)) spawned++;
                }
                burst(sw, p, ParticleTypes.WITCH, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON);
                msg(p, "暗影分身!×" + spawned + "(50%血/100%攻,30秒)", Formatting.LIGHT_PURPLE);
            }
            case SWORDSMAN -> {
                // 剑气斩:前方短距剑气(万剑归一迷你版)
                Vec3d dir = p.getRotationVector();
                double r = cfg.minorSwordsmanRange;
                Box wave = p.getBoundingBox().expand(r * 0.5).offset(dir.x * r * 0.6, 0, dir.z * r * 0.6);
                float mwDmg = (float) (cfg.minorSwordsmanDamage + atk(p) * cfg.minorSwordsmanAttackRatio);
                int hit = 0;
                for (LivingEntity le : sw.getEntitiesByClass(LivingEntity.class, wave,
                        e -> e.isAlive() && e != p && !(e instanceof PlayerEntity))) {
                    le.damage(src, mwDmg);
                    le.timeUntilRegen = 0;
                    hit++;
                }
                Vec3d cc = p.getPos().add(dir.x * 1.5, p.getStandingEyeHeight() * 0.6, dir.z * 1.5);
                sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, cc.x, cc.y, cc.z, 8, 0.8, 0.3, 0.8, 0.0);
                sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.1f);
                msg(p, "剑气斩!命中 " + hit + " 个目标", Formatting.AQUA);
            }
            case MONK -> {
                // 金钟罩:短时抗性II+回复(防御脉冲)
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, cfg.minorMonkDurationTicks, 1, true, false, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, cfg.minorMonkDurationTicks, 0, true, false, true));
                burst(sw, p, ParticleTypes.ENCHANTED_HIT, SoundEvents.BLOCK_BELL_RESONATE);
                msg(p, "金钟罩!抗性II+回复护体", Formatting.AQUA);
            }
            case ASSASSIN -> {
                // 疾影步:向前猛冲 + 短暂加速。服务端改玩家速度必须显式发同步包(客户端权威,m152 先例)
                Vec3d dir = p.getRotationVector();
                double h = Math.max(0.1, cfg.minorAssassinDashStrength);
                p.setVelocity(dir.x * h, 0.22, dir.z * h);
                p.velocityModified = true;
                p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, cfg.minorAssassinSpeedTicks, 1, true, false, true));
                burst(sw, p, ParticleTypes.CLOUD, SoundEvents.ENTITY_PHANTOM_FLAP);
                msg(p, "疾影步!", Formatting.AQUA);
            }
            case SUMMONER -> {
                // 召唤:5 座强化铁傀儡(m232 从大招挪来,独立冷却;大招 X 专职癫狂)
                int n = SummonerHandler.summonGolems(p);
                burst(sw, p, ParticleTypes.POOF, SoundEvents.BLOCK_ANVIL_PLACE);
                msg(p, "召唤!" + n + " 座强化铁傀儡拔地而起(大招键=癫狂请朋友助阵)", Formatting.AQUA);
            }
        }
        return true;
    }

    /** 以玩家为中心的范围伤害;knockFrom != null 时附带远离该点的击退(与大招管理器同款)。 */
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

    /** m234:玩家当前攻击力——伤害型小技能统一「基础值+攻击×倍率」(照 m72 武器技能公式)。 */
    private static double atk(ServerPlayerEntity p) {
        return p.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
    }

    private static void burst(ServerWorld sw, ServerPlayerEntity p, net.minecraft.particle.ParticleEffect particle,
                              net.minecraft.sound.SoundEvent sound) {
        sw.spawnParticles(particle, p.getX(), p.getBodyY(0.6), p.getZ(), 18, 1.0, 0.5, 1.0, 0.08);
        sw.playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static void msg(ServerPlayerEntity p, String s, Formatting f) {
        p.sendMessage(Text.literal(s).formatted(f), true);
    }
}
