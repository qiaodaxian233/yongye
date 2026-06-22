package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.function.Function;

/**
 * Boss 翻倍系统(文档第 7 章)。
 * 对 Boss 类实体施加血量/攻击/移速/抗性翻倍,并在击杀时给予翻倍大奖励。
 * "技能频率/召唤数量翻倍"涉及各 Boss 的具体 AI,需 mixin,留待后续细化。
 */
public final class BossHandler {
    private BossHandler() {}

    private static final Identifier ID_HEALTH = Identifier.of(Yongye.MOD_ID, "boss_health");
    private static final Identifier ID_ATTACK = Identifier.of(Yongye.MOD_ID, "boss_attack");
    private static final Identifier ID_SPEED = Identifier.of(Yongye.MOD_ID, "boss_speed");
    private static final Identifier ID_KB = Identifier.of(Yongye.MOD_ID, "boss_knockback");

    /** 是否属于 Boss 类实体。MobEnhancementHandler 会用它跳过 Boss,避免重复增强。 */
    public static boolean isBoss(Entity entity) {
        if (entity instanceof WitherEntity) return true;
        if (entity instanceof WardenEntity) return true;
        if (entity instanceof ElderGuardianEntity) return true;
        if (entity instanceof EnderDragonEntity) return true;
        if (entity instanceof RaiderEntity raider) return raider.isPatrolLeader();
        return false;
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableBoss) return;
            if (!(entity instanceof MobEntity mob) || !isBoss(mob)) return;
            if (mob.getAttachedOrElse(ModAttachments.IS_BOSS, false)) return;

            // 掠夺者队长(巡逻队长)加天数门控:早于设定天数不强化为 Boss
            // ——掠夺者巡逻队在主世界自然刷新、不受永夜/天数限制,会导致开局就遇到 Boss 级队长。
            // 真·Boss(凋灵/监守者/远古守卫/末影龙)不在此限,仍始终强化。
            if (mob instanceof RaiderEntity
                    && ProgressionManager.gameDay(world) < cfg.bossRaidCaptainMinDay) {
                return;
            }

            mob.setAttached(ModAttachments.IS_BOSS, true);

            addMultiplier(mob, EntityAttributes.GENERIC_MAX_HEALTH, ID_HEALTH, cfg.bossHealthMultiplier);
            addMultiplier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_ATTACK, cfg.bossAttackMultiplier);
            addMultiplier(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, ID_SPEED, cfg.bossSpeedMultiplier);
            addFlat(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, ID_KB, cfg.bossKnockbackResistanceAdd);
            mob.setHealth(mob.getMaxHealth());
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!YongyeConfig.get().enableBoss) return;
            if (!entity.getAttachedOrElse(ModAttachments.IS_BOSS, false)) return;
            if (!(entity.getWorld() instanceof ServerWorld world)) return;
            dropBossRewards(world, entity);
        });

        Yongye.LOGGER.info("[永夜] Boss 翻倍系统已挂载");
    }

    private static void dropBossRewards(ServerWorld world, LivingEntity boss) {
        YongyeConfig cfg = YongyeConfig.get();
        Random r = boss.getRandom();
        double m = cfg.bossDropMultiplier;

        dropMany(world, boss, scale(3, m), rr -> HealthSkillBookItem.create(10 + rr.nextInt(11))); // V10~V20
        // 属性技能书:随机类型 V3~V8,数本
        dropMany(world, boss, scale(3, m), rr -> {
            com.yongye.item.SkillType[] ts = com.yongye.item.SkillType.values();
            return com.yongye.item.SkillBookItem.create(ts[rr.nextInt(ts.length)], 3 + rr.nextInt(6));
        });
        dropMany(world, boss, scale(4, m), rr -> new ItemStack(ModItems.LIFE_CRYSTAL));
        dropMany(world, boss, scale(2, m), rr -> new ItemStack(ModItems.LIFE_CORE));
        dropMany(world, boss, scale(1, m), rr -> new ItemStack(ModItems.CATASTROPHE_BLOOD_CORE));
        dropMany(world, boss, scale(2, m), rr -> new ItemStack(Items.DIAMOND_BLOCK));
        dropMany(world, boss, scale(2, m), rr -> new ItemStack(Items.NETHERITE_INGOT));
        dropMany(world, boss, scale(2, m), rr -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        dropMany(world, boss, scale(1, m), rr -> new ItemStack(Items.TOTEM_OF_UNDYING));
        // Boss 掉落随机职业专属武器(EPIC):默认 1 把,随 Boss 倍率放大
        dropMany(world, boss, scale(1, m), rr -> {
            com.yongye.item.PlayerClass[] cls = com.yongye.item.PlayerClass.values();
            return new ItemStack(ModItems.getClassWeapon(cls[rr.nextInt(cls.length)]));
        });
        // 小概率掉落终极材料
        if (r.nextDouble() < 0.25 * m) {
            drop(world, boss, new ItemStack(ModItems.ENDING_ESSENCE));
        }
    }

    private static int scale(int base, double mult) {
        return Math.max(1, (int) Math.round(base * mult));
    }

    private static void dropMany(ServerWorld world, LivingEntity boss, int count, Function<Random, ItemStack> factory) {
        for (int i = 0; i < count; i++) {
            drop(world, boss, factory.apply(boss.getRandom()));
        }
    }

    private static void drop(ServerWorld world, LivingEntity boss, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity ie = new ItemEntity(world, boss.getX(), boss.getY() + 0.5, boss.getZ(), stack);
        ie.setToDefaultPickupDelay();
        world.spawnEntity(ie);
    }

    private static void addMultiplier(LivingEntity e, RegistryEntry<EntityAttribute> attr, Identifier id, double multiplier) {
        if (multiplier == 1.0) return;
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPersistentModifier(new EntityAttributeModifier(
                id, multiplier - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void addFlat(LivingEntity e, RegistryEntry<EntityAttribute> attr, Identifier id, double value) {
        if (value == 0.0) return;
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPersistentModifier(new EntityAttributeModifier(
                id, value, EntityAttributeModifier.Operation.ADD_VALUE));
    }
}
