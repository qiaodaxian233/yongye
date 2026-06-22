package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 怪物基础增强(文档第 5 章)。
 * 在实体加载进世界时,对敌对生物(Monster)施加属性增强,并按概率附带随机正面药水。
 * 用持久修饰符 + MOB_ENHANCED 标记,保证只增强一次。
 */
public final class MobEnhancementHandler {
    private MobEnhancementHandler() {}

    private static final Identifier ID_HEALTH = Identifier.of(Yongye.MOD_ID, "mob_health");
    private static final Identifier ID_ATTACK = Identifier.of(Yongye.MOD_ID, "mob_attack");
    private static final Identifier ID_SPEED = Identifier.of(Yongye.MOD_ID, "mob_speed");
    private static final Identifier ID_KB = Identifier.of(Yongye.MOD_ID, "mob_knockback");
    private static final Identifier ID_FOLLOW = Identifier.of(Yongye.MOD_ID, "mob_follow");
    private static final Identifier ID_SCALE_HP = Identifier.of(Yongye.MOD_ID, "mob_scale_hp");
    private static final Identifier ID_SCALE_ATK = Identifier.of(Yongye.MOD_ID, "mob_scale_atk");
    private static final Identifier ID_NIGHTFALL_HP = Identifier.of(Yongye.MOD_ID, "mob_nightfall_hp");

    private static final List<RegistryEntry<net.minecraft.entity.effect.StatusEffect>> POTION_POOL = List.of(
            StatusEffects.SPEED,
            StatusEffects.STRENGTH,
            StatusEffects.RESISTANCE,
            StatusEffects.FIRE_RESISTANCE,
            StatusEffects.JUMP_BOOST,
            StatusEffects.REGENERATION,
            StatusEffects.INVISIBILITY,
            StatusEffects.NIGHT_VISION,
            StatusEffects.ABSORPTION,
            StatusEffects.SLOW_FALLING
    );

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableMobEnhancement) return;
            if (!(entity instanceof MobEntity mob) || !(entity instanceof Monster)) return;
            if (BossHandler.isBoss(entity)) return; // Boss 走翻倍系统,不吃基础增强
            if (mob.getAttachedOrElse(ModAttachments.IS_PAIN, false)) return;
            if (mob.getAttachedOrElse(ModAttachments.IS_HIM, false)) return;
            if (mob.getAttachedOrElse(ModAttachments.MOB_ENHANCED, false)) return;

            mob.setAttached(ModAttachments.MOB_ENHANCED, true);

            addMultiplier(mob, EntityAttributes.GENERIC_MAX_HEALTH, ID_HEALTH, cfg.mobHealthMultiplier);
            addMultiplier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_ATTACK, cfg.mobAttackMultiplier);
            addMultiplier(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, ID_SPEED, cfg.mobSpeedMultiplier);
            addFlat(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, ID_KB, cfg.mobKnockbackResistanceAdd);
            addFlat(mob, EntityAttributes.GENERIC_FOLLOW_RANGE, ID_FOLLOW, cfg.mobFollowRangeAdd);

            // 随进度递增:怪物血量/攻击随永夜等级、天数、附近玩家强度成长
            if (cfg.enableMobScaling) {
                double prog = progressionMultiplier(mob, cfg);
                if (prog > 1.0) {
                    addMultiplier(mob, EntityAttributes.GENERIC_MAX_HEALTH, ID_SCALE_HP, prog);
                    double atkProg = 1.0 + (prog - 1.0) * cfg.mobScalingAttackRatio;
                    addMultiplier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_SCALE_ATK, atkProg);
                }
            }

            // 永夜 V5(灭世)之后:每多一级线性增怪血(仅血量、独立于上面的封顶上限)——失败越多世界越凶
            int nf = NightfallManager.getLevel();
            if (cfg.enableNightfall && nf > 5) {
                double abyssHp = 1.0 + (nf - 5) * cfg.nightfallBeyondHpPerLevel;
                addMultiplier(mob, EntityAttributes.GENERIC_MAX_HEALTH, ID_NIGHTFALL_HP, abyssHp);
            }

            // 加血后补满血量
            mob.setHealth(mob.getMaxHealth());

            // 随机正面药水
            if (mob.getRandom().nextDouble() < cfg.mobRandomPotionChance) {
                var effect = POTION_POOL.get(mob.getRandom().nextInt(POTION_POOL.size()));
                mob.addStatusEffect(new StatusEffectInstance(
                        effect, StatusEffectInstance.INFINITE, 0, true, false, false));
            }
        });
        Yongye.LOGGER.info("[永夜] 怪物增强系统已挂载");
    }

    /** 进度倍率:基于永夜等级 + 游戏天数 + 附近玩家最大生命。 */
    private static double progressionMultiplier(MobEntity mob, YongyeConfig cfg) {
        double prog = 1.0;
        prog += NightfallManager.getLevel() * cfg.mobScalingPerNightfall;

        long day = mob.getWorld().getTimeOfDay() / 24000L;
        prog += Math.min(day, cfg.mobScalingMaxDays) * cfg.mobScalingPerDay;

        var nearest = mob.getWorld().getClosestPlayer(mob, 128.0);
        if (nearest != null) {
            double extra = (nearest.getMaxHealth() - 20.0) / 20.0; // 超出基础 20 的比例
            if (extra > 0) prog += extra * cfg.mobScalingPlayerHealthFactor;
        }
        return Math.min(prog * ProgressionManager.evolutionMultiplier(mob.getWorld()), cfg.mobScalingMaxMultiplier);
    }

    private static void addMultiplier(LivingEntity e,
                                      RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                                      Identifier id, double multiplier) {
        if (multiplier == 1.0) return;
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPersistentModifier(new EntityAttributeModifier(
                id, multiplier - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void addFlat(LivingEntity e,
                                RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                                Identifier id, double value) {
        if (value == 0.0) return;
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPersistentModifier(new EntityAttributeModifier(
                id, value, EntityAttributeModifier.Operation.ADD_VALUE));
    }
}
