package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 精英怪系统(文档第 6 章)。
 *  - 怪物按概率精英化:叠加额外属性 + 发光 + 专属名牌。
 *  - 精英骷髅:一秒五箭(普通/中毒/迟缓/虚弱/发光/击退)。
 *  - 精英女巫:一秒五喷(剧毒/迟缓/虚弱/伤害/失明/饥饿/凋零/反胃/挖掘疲劳)+ 治疗增益附近怪物。
 *  - 精英瞬移:与目标拉远/卡位时瞬移到目标附近,带粒子与音效提示。
 */
public final class EliteHandler {
    private EliteHandler() {}

    private static final Identifier ID_HEALTH = Identifier.of(Yongye.MOD_ID, "elite_health");
    private static final Identifier ID_ATTACK = Identifier.of(Yongye.MOD_ID, "elite_attack");
    private static final Identifier ID_SPEED = Identifier.of(Yongye.MOD_ID, "elite_speed");
    private static final Identifier ID_KB = Identifier.of(Yongye.MOD_ID, "elite_knockback");
    private static final Identifier ID_FOLLOW = Identifier.of(Yongye.MOD_ID, "elite_follow");

    private static final Set<MobEntity> ELITES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<MobEntity, Integer> LAST_TELEPORT_AGE = new WeakHashMap<>();

    private static final List<RegistryEntry<StatusEffect>> ARROW_EFFECTS = List.of(
            StatusEffects.POISON, StatusEffects.SLOWNESS, StatusEffects.WEAKNESS, StatusEffects.NAUSEA);

    private static final List<RegistryEntry<StatusEffect>> WITCH_EFFECTS = List.of(
            StatusEffects.POISON, StatusEffects.SLOWNESS, StatusEffects.WEAKNESS, StatusEffects.INSTANT_DAMAGE,
            StatusEffects.BLINDNESS, StatusEffects.HUNGER, StatusEffects.WITHER, StatusEffects.NAUSEA,
            StatusEffects.MINING_FATIGUE);

    private static final List<RegistryEntry<StatusEffect>> WITCH_BUFFS = List.of(
            StatusEffects.STRENGTH, StatusEffects.SPEED, StatusEffects.RESISTANCE, StatusEffects.REGENERATION);

    public static void register() {
        // —— 精英缴械:命中玩家时概率夺走主手武器,精英死亡掉落(击杀夺回)——
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.eliteCanDisarm || cfg.eliteDisarmChance <= 0) return true;
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            if (!(source.getAttacker() instanceof MobEntity attacker)) return true;
            if (!attacker.getAttachedOrElse(ModAttachments.IS_ELITE, false)) return true;
            long now = player.getWorld().getTime();
            if (now < player.getAttachedOrElse(ModAttachments.DISARM_COOLDOWN_UNTIL, 0L)) return true;
            ItemStack held = player.getMainHandStack();
            if (held.isEmpty() || !EquipmentEnhancer.isWeapon(held)) return true;
            if (held.getOrDefault(com.yongye.registry.ModComponents.DISARM_PROOF, false)) return true; // 守护武器不被夺
            if (player.getRandom().nextDouble() >= cfg.eliteDisarmChance) return true;
            // 抢夺:精英装上玩家武器,死亡掉落;玩家主手清空
            attacker.equipStack(EquipmentSlot.MAINHAND, held.copy());
            attacker.setEquipmentDropChance(EquipmentSlot.MAINHAND, 1.0f);
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.setAttached(ModAttachments.DISARM_COOLDOWN_UNTIL, now + cfg.eliteDisarmCooldownTicks);
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
            player.sendMessage(Text.literal("精英怪夺走了你的武器!击杀它夺回").formatted(Formatting.RED), true);
            return true;
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableElite) return;
            if (!(entity instanceof MobEntity mob) || !(entity instanceof Monster)) return;
            if (BossHandler.isBoss(mob)) return;
            if (mob.getAttachedOrElse(ModAttachments.IS_PAIN, false)) return;
            if (mob.getAttachedOrElse(ModAttachments.IS_HIM, false)) return;
            if (mob.getAttachedOrElse(ModAttachments.IS_ELITE, false)) {
                ELITES.add(mob); // 重新加载时恢复追踪
                return;
            }
            if (mob.getRandom().nextDouble() >= cfg.eliteChance * NightfallManager.getEliteChanceMultiplier()) return;

            makeElite(mob, cfg);
            ELITES.add(mob);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ELITES.isEmpty()) return;
            YongyeConfig cfg = YongyeConfig.get();
            Iterator<MobEntity> it = ELITES.iterator();
            while (it.hasNext()) {
                MobEntity e = it.next();
                if (e == null || !e.isAlive() || e.isRemoved()) {
                    it.remove();
                    LAST_TELEPORT_AGE.remove(e);
                    continue;
                }
                if (!(e.getWorld() instanceof ServerWorld sw)) continue;
                tickElite(sw, e, cfg);
            }
        });

        Yongye.LOGGER.info("[亡途荒夜] 精英怪系统已挂载");
    }

    private static void makeElite(MobEntity mob, YongyeConfig cfg) {
        mob.setAttached(ModAttachments.IS_ELITE, true);

        addMultiplier(mob, EntityAttributes.GENERIC_MAX_HEALTH, ID_HEALTH, cfg.eliteHealthMultiplier);
        addMultiplier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_ATTACK, cfg.eliteAttackMultiplier);
        addMultiplier(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, ID_SPEED, cfg.eliteSpeedMultiplier);
        addFlat(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, ID_KB, cfg.eliteKnockbackResistanceAdd);
        addFlat(mob, EntityAttributes.GENERIC_FOLLOW_RANGE, ID_FOLLOW, cfg.eliteFollowRangeAdd);
        mob.setHealth(mob.getMaxHealth());

        // 持续发光(默认关:实体描边会触发部分渲染mod崩溃;精英已有金色名牌识别)
        if (cfg.eliteGlowing) {
            mob.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.GLOWING, StatusEffectInstance.INFINITE, 0, true, false, false));
        }

        // 专属名牌
        Text typeName = mob.getType().getName();
        mob.setCustomName(Text.literal("✦ 精英·").formatted(Formatting.GOLD).append(typeName));
        mob.setCustomNameVisible(true);
    }

    private static void tickElite(ServerWorld sw, MobEntity e, YongyeConfig cfg) {
        LivingEntity target = e.getTarget();

        // 精英主动感知:没有目标(或目标已死)时,锁定感知半径内最近的玩家
        if (target == null || !target.isAlive()) {
            net.minecraft.entity.player.PlayerEntity nearest =
                    sw.getClosestPlayer(e.getX(), e.getY(), e.getZ(), cfg.eliteSenseRadius, true);
            if (nearest != null) {
                e.setTarget(nearest);
                target = nearest;
            }
        }

        // —— 远程技能 ——
        if (target != null && target.isAlive()) {
            if (e instanceof AbstractSkeletonEntity) {
                int interval = Math.max(1, 20 / Math.max(1, cfg.eliteSkeletonArrowsPerSecond));
                if (e.age % interval == 0) shootArrow(sw, e, target);
            } else if (e instanceof WitchEntity) {
                int interval = Math.max(1, 20 / Math.max(1, cfg.eliteWitchPotionsPerSecond));
                if (e.age % interval == 0) throwPotion(sw, e, target);
                if (e.age % Math.max(1, cfg.eliteWitchSupportIntervalTicks) == 0) supportNearby(sw, e);
            }
        }

        // —— 瞬移 —— 每秒检测一次:目标太远 或 被墙卡住 都会瞬移到目标附近
        if (target != null && e.age % 20 == 0) {
            double dx = target.getX() - e.getX();
            double dz = target.getZ() - e.getZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            int last = LAST_TELEPORT_AGE.getOrDefault(e, -100000);
            boolean farAway = horiz > cfg.eliteTeleportTriggerDistance;
            boolean stuck = e.horizontalCollision && horiz > 5.0;
            if ((farAway || stuck) && (e.age - last) >= cfg.eliteTeleportCooldownTicks) {
                teleportNear(sw, e, target);
                LAST_TELEPORT_AGE.put(e, e.age);
            }
        }
    }

    private static void shootArrow(ServerWorld sw, MobEntity skeleton, LivingEntity target) {
        Random r = skeleton.getRandom();
        double roll = r.nextDouble();
        ItemStack stack;
        if (roll < 0.45) {
            stack = new ItemStack(Items.TIPPED_ARROW);
            RegistryEntry<StatusEffect> eff = ARROW_EFFECTS.get(r.nextInt(ARROW_EFFECTS.size()));
            stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(
                    Optional.empty(), Optional.empty(),
                    List.of(new StatusEffectInstance(eff, 120, 0))));
        } else {
            stack = new ItemStack(Items.ARROW);
        }

        ArrowEntity arrow = new ArrowEntity(sw, skeleton, stack, new ItemStack(Items.BOW));

        double dx = target.getX() - skeleton.getX();
        double dy = target.getBodyY(0.3333) - arrow.getY();
        double dz = target.getZ() - skeleton.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        // 高初速 + 低散布 = 高精度高射程
        arrow.setVelocity(dx, dy + dist * 0.2, dz, 2.0f, 0.5f);

        sw.spawnEntity(arrow);
        sw.playSound(null, skeleton.getX(), skeleton.getY(), skeleton.getZ(),
                SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.HOSTILE, 1.0f, 1.0f);
    }

    private static void throwPotion(ServerWorld sw, MobEntity witch, LivingEntity target) {
        Random r = witch.getRandom();
        RegistryEntry<StatusEffect> eff = WITCH_EFFECTS.get(r.nextInt(WITCH_EFFECTS.size()));
        boolean instant = eff == StatusEffects.INSTANT_DAMAGE;
        int dur = instant ? 1 : 160;

        ItemStack potion = new ItemStack(Items.SPLASH_POTION);
        potion.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(
                Optional.empty(), Optional.of(0x6A0DAD),
                List.of(new StatusEffectInstance(eff, dur, 0))));

        PotionEntity thrown = new PotionEntity(sw, witch);
        thrown.setItem(potion);
        double dx = target.getX() - witch.getX();
        double dy = target.getY() + target.getHeight() * 0.5 - witch.getY();
        double dz = target.getZ() - witch.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        thrown.setVelocity(dx, dy + dist * 0.2, dz, 0.75f, 8.0f);

        sw.spawnEntity(thrown);
        sw.playSound(null, witch.getX(), witch.getY(), witch.getZ(),
                SoundEvents.ENTITY_WITCH_THROW, SoundCategory.HOSTILE, 1.0f, 0.8f);
    }

    private static void supportNearby(ServerWorld sw, MobEntity witch) {
        Random r = witch.getRandom();
        Box box = witch.getBoundingBox().expand(8.0);
        List<MobEntity> nearby = sw.getEntitiesByClass(MobEntity.class, box,
                m -> m != witch && m.isAlive() && m instanceof Monster);
        int buffed = 0;
        for (MobEntity m : nearby) {
            if (buffed >= 6) break;
            m.heal(4.0f); // 治疗
            RegistryEntry<StatusEffect> buff = WITCH_BUFFS.get(r.nextInt(WITCH_BUFFS.size()));
            m.addStatusEffect(new StatusEffectInstance(buff, 200, 0, true, false, false));
            buffed++;
        }
    }

    private static void teleportNear(ServerWorld sw, MobEntity e, LivingEntity target) {
        Random r = e.getRandom();
        double angle = r.nextDouble() * Math.PI * 2.0;
        double radius = 6.0 + r.nextDouble() * 6.0; // 5~12 格区间
        double tx = target.getX() + Math.cos(angle) * radius;
        double tz = target.getZ() + Math.sin(angle) * radius;
        double ty = target.getY();

        // 出发提示
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getBodyY(0.5), e.getZ(),
                20, 0.3, 0.5, 0.3, 0.02);
        sw.playSound(null, e.getX(), e.getY(), e.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 0.9f);

        e.requestTeleport(tx, ty, tz);

        // 落点提示
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, tx, ty + 1.0, tz,
                20, 0.3, 0.5, 0.3, 0.02);
        sw.playSound(null, tx, ty, tz,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 0.9f);
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
