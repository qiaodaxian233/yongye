package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.WeaponQuality;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * 强化武器暴击:玩家用强化武器近战命中时,按武器品质的暴击率掷骰;
 * 暴击则在原版攻击之前追加一次伤害,并清空目标无敌帧使两段伤害叠加。
 */
public final class WeaponCombatHandler {
    private WeaponCombatHandler() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) return ActionResult.PASS;

            ItemStack weapon = player.getMainHandStack();
            if (!EquipmentEnhancer.isWeapon(weapon)) return ActionResult.PASS;
            int lvl = EquipmentEnhancer.getLevel(weapon);
            if (lvl <= 0) return ActionResult.PASS;

            boolean charged = player.getAttackCooldownProgress(0.5f) >= 0.9f;

            // —— 后期吸血:武器强化达阈值(默认1000级)后,蓄满攻击命中按攻击力比例回血(0.几的吸血) ——
            YongyeConfig cfg = YongyeConfig.get();
            if (charged && cfg.enableWeaponLifesteal && lvl >= cfg.weaponLifestealMinLevel
                    && player.getHealth() < player.getMaxHealth()) {
                double frac = Math.min(cfg.weaponLifestealMax,
                        cfg.weaponLifestealBase + (lvl - cfg.weaponLifestealMinLevel) * cfg.weaponLifestealPerLevel);
                double atk = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                float heal = (float) (atk * frac);
                if (heal > 0f) player.heal(heal);
            }

            // —— 横扫之刃:自定义武器非 SwordItem,原版横扫不触发,这里手搓 ——
            // 武器附了横扫之刃 + 蓄满 + 在地面 + 非疾跑时,对周围敌人补一圈 AOE(尊重攻速,连点不横扫)
            if (charged && player.isOnGround() && !player.isSprinting()) {
                trySweep(player, world, target, weapon);
            }

            // 仅在攻击基本蓄满时才可暴击(连点刷子不吃暴击,尊重攻速)
            if (!charged) return ActionResult.PASS;

            WeaponQuality q = WeaponQuality.forLevel(lvl);
            if (q.critChance <= 0) return ActionResult.PASS;
            if (player.getRandom().nextDouble() >= q.critChance) return ActionResult.PASS;

            float bonus = EquipmentEnhancer.critBonusDamage(weapon);
            if (bonus <= 0) return ActionResult.PASS;

            // 追加暴击伤害,然后清无敌帧,让随后的原版攻击照常生效(叠加而非替换)
            target.damage(world.getDamageSources().playerAttack(player), bonus);
            target.timeUntilRegen = 0;

            if (world instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.6), target.getZ(),
                        12, 0.3, 0.3, 0.3, 0.4);
                sw.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.8f, 1.2f);
            }
            return ActionResult.PASS;
        });

        Yongye.LOGGER.info("[永夜] 武器暴击系统已挂载");
    }

    /**
     * 手搓横扫:读武器上的「横扫之刃」等级,对主目标周围的敌人补一圈 AOE。
     * 仅给本 mod 自定义武器用(它们 extends Item、非 SwordItem,吃不到原版横扫);
     * 原版剑不会进这里(调用前已 isWeapon 过滤),不会双重横扫。
     */
    private static void trySweep(PlayerEntity player, World world, LivingEntity target, ItemStack weapon) {
        // 待编译验证:1.21 数据驱动附魔取等级——注册表查 SWEEPING_EDGE 条目 + EnchantmentHelper.getLevel(RegistryEntry, ItemStack)。
        // 若 get(...) 报错可改 getOrThrow(...);若 getEntry 返回类型不符按 IDEA 提示调。
        RegistryEntry<Enchantment> sweeping = world.getRegistryManager()
                .get(RegistryKeys.ENCHANTMENT)
                .getEntry(Enchantments.SWEEPING_EDGE)
                .orElse(null);
        if (sweeping == null) return;
        int level = EnchantmentHelper.getLevel(sweeping, weapon);
        if (level <= 0) return;

        // 横扫伤害 = 1 + 攻击力 * level/(level+1),贴近原版横扫之刃手感
        double atk = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float sweepDmg = 1.0f + (float) atk * (level / (level + 1.0f));

        float yawRad = player.getYaw() * ((float) Math.PI / 180f);
        boolean hit = false;
        for (LivingEntity nearby : world.getNonSpectatingEntities(LivingEntity.class,
                target.getBoundingBox().expand(1.0, 0.25, 1.0))) {
            if (nearby == player || nearby == target) continue;
            if (player.isTeammate(nearby)) continue;
            if (player.squaredDistanceTo(nearby) >= 9.0) continue;
            nearby.takeKnockback(0.4,
                    (double) MathHelper.sin(yawRad),
                    (double) -MathHelper.cos(yawRad));
            nearby.damage(world.getDamageSources().playerAttack(player), sweepDmg);
            hit = true;
        }

        if (hit && world instanceof ServerWorld sw) {
            double dx = -MathHelper.sin(yawRad);
            double dz = MathHelper.cos(yawRad);
            sw.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                    player.getX() + dx, player.getBodyY(0.5), player.getZ() + dz,
                    0, dx, 0.0, dz, 0.0);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }
}
