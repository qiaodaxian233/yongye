package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.WeaponQuality;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

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
}
