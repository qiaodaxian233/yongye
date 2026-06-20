package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents;
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
            if (mob.getAttachedOrElse(ModAttachments.MOB_ENHANCED, false)) return;

            mob.setAttached(ModAttachments.MOB_ENHANCED, true);

            addMultiplier(mob, EntityAttributes.GENERIC_MAX_HEALTH, ID_HEALTH, cfg.mobHealthMultiplier);
            addMultiplier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_ATTACK, cfg.mobAttackMultiplier);
            addMultiplier(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, ID_SPEED, cfg.mobSpeedMultiplier);
            addFlat(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, ID_KB, cfg.mobKnockbackResistanceAdd);
            addFlat(mob, EntityAttributes.GENERIC_FOLLOW_RANGE, ID_FOLLOW, cfg.mobFollowRangeAdd);

            // 加血后补满血量
            mob.setHealth(mob.getMaxHealth());

            // 随机正面药水
            if (mob.getRandom().nextDouble() < cfg.mobRandomPotionChance) {
                var effect = POTION_POOL.get(mob.getRandom().nextInt(POTION_POOL.size()));
                mob.addStatusEffect(new StatusEffectInstance(
                        effect, StatusEffectInstance.INFINITE, 0, true, false, false));
            }
        });
        Yongye.LOGGER.info("[亡途荒夜] 怪物增强系统已挂载");
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
