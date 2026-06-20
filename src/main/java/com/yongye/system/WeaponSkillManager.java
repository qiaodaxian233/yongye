package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.WeaponSkill;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.ItemStack;
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
import java.util.Map;
import java.util.UUID;

/**
 * 武器主动技能:按品质解锁,按键触发(经 SkillUsePayload),服务端结算 + 冷却。
 * 全部用公开 API(伤害/击退/治疗/粒子),零 mixin。
 */
public final class WeaponSkillManager {
    private WeaponSkillManager() {}

    // 每名玩家 3 个技能的冷却结束时刻(server.getTicks())
    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

    public static void use(ServerPlayerEntity player, int index) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableWeaponSkills) return;
        if (index < 0 || index >= WeaponSkill.values().length) return;
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        ItemStack weapon = player.getMainHandStack();
        if (!EquipmentEnhancer.isWeapon(weapon)) {
            actionbar(player, "需手持强化武器", Formatting.GRAY);
            return;
        }
        int level = EquipmentEnhancer.getLevel(weapon);
        WeaponSkill skill = WeaponSkill.values()[index];
        boolean chaosBlade = weapon.getItem() == com.yongye.registry.ModItems.CHAOS_BLADE;
        if (!chaosBlade && !skill.isUnlocked(level)) {
            actionbar(player, "【" + skill.cn + "】未解锁(需品质「" + skill.unlockTier.cn + "」)", Formatting.GRAY);
            return;
        }

        long now = player.server.getTicks();
        long[] cds = COOLDOWNS.computeIfAbsent(player.getUuid(), k -> new long[WeaponSkill.values().length]);
        if (now < cds[index]) {
            int left = (int) Math.ceil((cds[index] - now) / 20.0);
            actionbar(player, "【" + skill.cn + "】冷却中 " + left + "s", Formatting.RED);
            return;
        }

        int cd;
        switch (skill) {
            case SLASH -> { cd = cfg.skillSlashCooldown; chaosSlash(world, player, level, cfg); }
            case DEVOUR -> { cd = cfg.skillDevourCooldown; abyssDevour(world, player, level, cfg); }
            default -> { cd = cfg.skillFinalityCooldown; finality(world, player, level, cfg); }
        }
        cds[index] = now + cd;
        actionbar(player, "施放【" + skill.cn + "】!", Formatting.LIGHT_PURPLE);
    }

    /** 混沌斩:面朝方向锥形范围伤害 + 击退。 */
    private static void chaosSlash(ServerWorld world, ServerPlayerEntity player, int level, YongyeConfig cfg) {
        double dmg = cfg.skillSlashDamage + level * cfg.skillSlashDamagePerLevel;
        double range = cfg.skillSlashRange;
        Vec3d look = player.getRotationVector().normalize();
        Vec3d eye = player.getEyePos();
        DamageSource src = world.getDamageSources().playerAttack(player);

        Box box = player.getBoundingBox().expand(range);
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && isHostileTarget(e))) {
            Vec3d to = le.getPos().subtract(eye).normalize();
            if (look.dotProduct(to) < 0.35) continue; // 仅前方扇形
            le.damage(src, (float) dmg);
            le.takeKnockback(1.6, -look.x, -look.z);
        }
        for (int i = 1; i <= (int) range; i++) {
            Vec3d p = eye.add(look.multiply(i));
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 2, 0.5, 0.3, 0.5, 0.0);
            world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 6, 0.4, 0.4, 0.4, 0.2);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    /** 深渊吞噬:半径内敌人受伤,按比例治疗自己。 */
    private static void abyssDevour(ServerWorld world, ServerPlayerEntity player, int level, YongyeConfig cfg) {
        double dmg = cfg.skillDevourDamage + level * cfg.skillDevourDamagePerLevel;
        double radius = cfg.skillDevourRadius;
        DamageSource src = world.getDamageSources().magic();
        Box box = player.getBoundingBox().expand(radius);
        double healed = 0;
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && isHostileTarget(e))) {
            le.damage(src, (float) dmg);
            healed += dmg * cfg.skillDevourHealRatio;
            world.spawnParticles(ParticleTypes.PORTAL, le.getX(), le.getBodyY(0.6), le.getZ(),
                    10, 0.3, 0.5, 0.3, 0.4);
        }
        // 禁疗时不回血;单次治疗上限按最大生命百分比封顶
        boolean healBlocked = player.getAttachedOrElse(ModAttachments.NO_HEAL_UNTIL, 0L) > player.getWorld().getTime();
        if (healed > 0 && !healBlocked) {
            double cap = player.getMaxHealth() * cfg.skillDevourHealMaxPct;
            player.heal((float) Math.min(healed, cap));
        }
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getBodyY(0.6), player.getZ(),
                40, 0.5, 0.8, 0.5, 0.3);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    /** 终焉降临:大范围毁灭性打击 + 上抛。 */
    private static void finality(ServerWorld world, ServerPlayerEntity player, int level, YongyeConfig cfg) {
        double dmg = cfg.skillFinalityDamage + level * cfg.skillFinalityDamagePerLevel;
        double radius = cfg.skillFinalityRadius;
        DamageSource src = world.getDamageSources().playerAttack(player);
        Box box = player.getBoundingBox().expand(radius);
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && isHostileTarget(e))) {
            le.damage(src, (float) dmg);
            le.addVelocity(0, 0.8, 0);
            le.velocityModified = true;
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 1, player.getZ(), 3, 1, 1, 1, 0);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(),
                120, radius / 2, 1.0, radius / 2, 0.2);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.0f, 0.7f);
    }

    private static void actionbar(ServerPlayerEntity player, String msg, Formatting color) {
        player.sendMessage(Text.literal(msg).formatted(color), true);
    }

    /** 只对敌对生物(怪物/我方标记的 Boss/长门)生效,避免误伤玩家/村民/宠物/动物。 */
    private static boolean isHostileTarget(LivingEntity e) {
        return e instanceof Monster
                || e.getAttachedOrElse(ModAttachments.IS_BOSS, false)
                || e.getAttachedOrElse(ModAttachments.IS_PAIN, false);
    }

    public static void init() {
        // 玩家退出时清理冷却表,避免内存堆积
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                COOLDOWNS.remove(handler.player.getUuid()));
        Yongye.LOGGER.info("[亡途荒夜] 武器主动技能系统已挂载");
    }
}
