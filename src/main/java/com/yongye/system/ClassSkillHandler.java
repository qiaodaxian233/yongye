package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.PlayerClass;
import com.yongye.item.ClassWeaponItem;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 职业专属技能(m41,触发型,纯 Fabric 事件实现,不依赖 mixin)。
 * 战士:吸血 / 斩杀     坦克:嘲讽 / 护盾(被动抗性=守护者天赋)
 * 刺客:背刺 / 闪避 / 脱战加速   术士:潜行攻击牺牲生命换范围伤害
 * 武僧:空手连击 / 缴械(拳意见 m37)   剑客:剑气波 / 格挡反击
 * 追加伤害沿用 WeaponCombatHandler 的做法:target.damage(...) 后 timeUntilRegen=0,与原版攻击叠加。
 */
public final class ClassSkillHandler {
    private ClassSkillHandler() {}

    // 瞬态状态(无需持久化,relog 重置即可)
    private static final Map<UUID, Integer> comboCount = new HashMap<>();
    private static final Map<UUID, Integer> comboTarget = new HashMap<>();
    private static final Map<UUID, Long> comboUntil = new HashMap<>();
    private static final Map<UUID, Long> lastCombat = new HashMap<>();

    private static boolean isBossLike(LivingEntity e) {
        return e.getAttachedOrElse(ModAttachments.IS_BOSS, false)
                || e.getAttachedOrElse(ModAttachments.IS_PAIN, false);
    }

    public static void register() {
        // 武僧:挖掘/破坏方块也额外损耗 1 点耐久(与攻击磨损一起 = 武器耐久×2 全用途;纯事件不依赖 mixin)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity p)) return;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.monkWeaponDurabilityPenalty || !ClassManager.isActive(p, PlayerClass.MONK)) return;
            ItemStack main = p.getMainHandStack();
            if (!main.isEmpty() && main.isDamageable()
                    && !ClassWeaponItem.held(p, PlayerClass.MONK)
                    && main.getDamage() < main.getMaxDamage()) {
                main.setDamage(main.getDamage() + 1);
            }
        });

        // ===== 近战命中触发 =====
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity p)) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) return ActionResult.PASS;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableClassSkills) return ActionResult.PASS;

            lastCombat.put(p.getUuid(), world.getTime());
            boolean charged = p.getAttackCooldownProgress(0.5f) >= 0.9f;
            DamageSource atkSrc = world.getDamageSources().playerAttack(p);
            boolean empty = p.getMainHandStack().isEmpty();

            // 战士:吸血 + 斩杀
            if (charged && ClassManager.isActive(p, PlayerClass.WARRIOR)) {
                boolean wep = ClassWeaponItem.held(p, PlayerClass.WARRIOR);
                double atk = p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                float heal = (float) (atk * Math.max(0.0, cfg.warriorLifestealFraction) * (wep ? 2.0 : 1.0));
                if (heal > 0) p.heal(heal);
                double thr = wep ? Math.max(cfg.warriorExecuteThreshold, 0.35) : cfg.warriorExecuteThreshold;
                if (!(target instanceof PlayerEntity) && !isBossLike(target)
                        && target.getHealth() / target.getMaxHealth() <= thr) {
                    float exec = (float) (target.getMaxHealth() * Math.max(0.0, cfg.warriorExecuteBonusFraction));
                    bonusHit(target, atkSrc, exec, world);
                    feedback(world, target, ParticleTypes.DAMAGE_INDICATOR, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.4f);
                }
            }

            // 刺客:背刺(从目标背后命中追加伤害)
            if (charged && ClassManager.isActive(p, PlayerClass.ASSASSIN)) {
                Vec3d face = target.getRotationVector();
                Vec3d toAtk = p.getPos().subtract(target.getPos());
                double fh = Math.hypot(face.x, face.z), th = Math.hypot(toAtk.x, toAtk.z);
                if (fh > 1e-4 && th > 1e-4) {
                    double dot = (face.x * toAtk.x + face.z * toAtk.z) / (fh * th);
                    if (dot < -0.2) {
                        double mult = ClassWeaponItem.held(p, PlayerClass.ASSASSIN) ? 2.0 : 1.0;
                        bonusHit(target, atkSrc, (float) (cfg.assassinBackstabBonus * mult), world);
                        feedback(world, target, ParticleTypes.CRIT, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.5f);
                    }
                }
            }

            // 刺客:职业暴击(概率追加伤害,持影刺概率更高)——对应设定「更容易出现暴击」
            if (charged && ClassManager.isActive(p, PlayerClass.ASSASSIN)) {
                double cc = cfg.assassinCritChance + (ClassWeaponItem.held(p, PlayerClass.ASSASSIN) ? 0.15 : 0.0);
                if (p.getRandom().nextDouble() < cc) {
                    double atk = p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                    float bonus = (float) (atk * Math.max(0.0, cfg.assassinCritBonusFraction));
                    if (bonus > 0) {
                        bonusHit(target, atkSrc, bonus, world);
                        feedback(world, target, ParticleTypes.CRIT, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.3f);
                    }
                }
            }

            // 武僧:持(非拳套)武器攻击 → 额外损耗 1 点耐久(等效耐久×2,逼你用拳)——对应设定「武器耐久翻倍」
            if (cfg.monkWeaponDurabilityPenalty && ClassManager.isActive(p, PlayerClass.MONK)
                    && !empty && !ClassWeaponItem.held(p, PlayerClass.MONK)) {
                ItemStack main = p.getMainHandStack();
                if (main.isDamageable() && main.getDamage() < main.getMaxDamage()) {
                    main.setDamage(main.getDamage() + 1);
                }
            }

            // 武僧:空手连击(连续命中同一目标叠加伤害)+ 缴械;手持鬼神拳套视为空手且更狠
            boolean monkWep = ClassWeaponItem.held(p, PlayerClass.MONK);
            if (charged && (empty || monkWep) && ClassManager.isActive(p, PlayerClass.MONK)) {
                long now = world.getTime();
                UUID u = p.getUuid();
                int cnt = (comboTarget.getOrDefault(u, -1) == target.getId()
                        && now <= comboUntil.getOrDefault(u, 0L)) ? comboCount.getOrDefault(u, 0) + 1 : 1;
                comboCount.put(u, cnt);
                comboTarget.put(u, target.getId());
                comboUntil.put(u, now + cfg.monkComboWindowTicks);
                int maxStacks = cfg.monkComboMaxStacks + (monkWep ? 3 : 0);
                int stacks = Math.min(cnt - 1, maxStacks);
                if (stacks > 0) {
                    double per = cfg.monkComboBonusPerHit * (monkWep ? 1.5 : 1.0);
                    bonusHit(target, atkSrc, (float) (stacks * per), world);
                    feedback(world, target, ParticleTypes.SWEEP_ATTACK, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0f + 0.05f * stacks);
                }
                if (target instanceof HostileEntity && !target.getMainHandStack().isEmpty()
                        && p.getRandom().nextDouble() < cfg.monkDisarmChance) {
                    ItemStack w = target.getMainHandStack().copy();
                    target.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    target.dropStack(w);
                    p.sendMessage(Text.literal("缴械!").formatted(Formatting.GOLD), true);
                }
            }

            // 术士:潜行近战 → 牺牲生命,范围魔法伤害;手持噬魂杖更强更省血
            if (charged && p.isSneaking() && ClassManager.isActive(p, PlayerClass.WARLOCK)) {
                boolean wep = ClassWeaponItem.held(p, PlayerClass.WARLOCK);
                double cost = Math.max(0.0, cfg.warlockAoeHpCost - (wep ? 2.0 : 0.0));
                if (p.getHealth() > cost + 1.0f) {
                    if (cost > 0) p.setHealth(Math.max(1.0f, p.getHealth() - (float) cost));
                    DamageSource magic = world.getDamageSources().magic();
                    double radius = cfg.warlockAoeRadius + (wep ? 2.0 : 0.0);
                    float dmg = (float) (cfg.warlockAoeDamage * (wep ? 1.5 : 1.0));
                    Box area = target.getBoundingBox().expand(radius);
                    for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, area,
                            e -> e.isAlive() && e != p && !(e instanceof PlayerEntity))) {
                        le.damage(magic, dmg);
                        le.timeUntilRegen = 0;
                    }
                    feedback(world, target, ParticleTypes.SOUL_FIRE_FLAME, SoundEvents.ENTITY_WITHER_SHOOT, 1.0f);
                }
            }

            // 剑客:持剑命中 → 前方剑气波;专属武器流光也触发,且更广更痛
            boolean swWep = ClassWeaponItem.held(p, PlayerClass.SWORDSMAN);
            if (charged && ClassManager.isActive(p, PlayerClass.SWORDSMAN)
                    && (swWep || EquipmentEnhancer.isWeapon(p.getMainHandStack()))) {
                DamageSource src = world.getDamageSources().playerAttack(p);
                Vec3d dir = p.getRotationVector();
                double range = cfg.swordsmanWaveRange + (swWep ? 2.0 : 0.0);
                float dmg = (float) (cfg.swordsmanWaveDamage * (swWep ? 1.5 : 1.0));
                Box wave = p.getBoundingBox().expand(range).offset(dir.x * range, 0, dir.z * range);
                for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, wave,
                        e -> e.isAlive() && e != p && e != target && !(e instanceof PlayerEntity))) {
                    le.damage(src, dmg);
                    le.timeUntilRegen = 0;
                }
                if (world instanceof ServerWorld sw) {
                    Vec3d c = p.getPos().add(dir.x * 1.5, p.getStandingEyeHeight() * 0.6, dir.z * 1.5);
                    sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 6, 0.6, 0.2, 0.6, 0.0);
                }
            }

            return ActionResult.PASS;
        });

        // ===== 受到伤害触发:刺客闪避 / 剑客格挡反击 =====
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity p)) return true;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableClassSkills) return true;
            if (source.getAttacker() == null) return true; // 仅闪避/格挡来自实体的攻击

            lastCombat.put(p.getUuid(), p.getWorld().getTime());

            // 坦克:持磐盾格挡被近战命中 → 反震(不否决,叠在原版格挡减伤之上)
            if (p.isBlocking() && ClassManager.isActive(p, PlayerClass.TANK)
                    && p.getOffHandStack().getItem() instanceof com.yongye.item.TankShieldItem
                    && source.getAttacker() instanceof LivingEntity tankAtk
                    && tankAtk.distanceTo(p) <= 5.0) {
                tankAtk.damage(p.getWorld().getDamageSources().playerAttack(p), (float) cfg.tankShieldReflect);
                tankAtk.timeUntilRegen = 0;
            }

            // 剑客:举盾格挡时被近战命中 → 否决并反伤
            if (p.isBlocking() && ClassManager.isActive(p, PlayerClass.SWORDSMAN)
                    && source.getAttacker() instanceof LivingEntity attacker
                    && attacker.distanceTo(p) <= 5.0) {
                double rmult = ClassWeaponItem.held(p, PlayerClass.SWORDSMAN) ? 1.5 : 1.0;
                attacker.damage(p.getWorld().getDamageSources().playerAttack(p), (float) (cfg.swordsmanParryReflect * rmult));
                attacker.timeUntilRegen = 0;
                if (p.getWorld() instanceof ServerWorld sw) {
                    sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.4f);
                }
                p.sendMessage(Text.literal("格挡反击!").formatted(Formatting.AQUA), true);
                return false;
            }

            // 刺客:概率闪避(完全免疫这次伤害);手持影刺几率更高
            double dodge = cfg.assassinDodgeChance + (ClassWeaponItem.held(p, PlayerClass.ASSASSIN) ? 0.12 : 0.0);
            if (ClassManager.isActive(p, PlayerClass.ASSASSIN) && p.getRandom().nextDouble() < dodge) {
                if (p.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getBodyY(0.5), p.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                    sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.8f, 1.6f);
                }
                p.sendMessage(Text.literal("闪避!").formatted(Formatting.GREEN), true);
                return false;
            }
            return true;
        });

        // ===== 被动 tick:坦克嘲讽/护盾、刺客脱战加速 =====
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableClassSkills) return;
            boolean slow = server.getTicks() % Math.max(1, cfg.tankTauntIntervalTicks) == 0;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                // 坦克护盾(吸收)每秒续命;手持镇魂 +1 级,副手磐盾再 +1 级
                if (server.getTicks() % 20 == 0 && ClassManager.isActive(p, PlayerClass.TANK)) {
                    int amp = Math.max(0, cfg.tankShieldAmplifier)
                            + (ClassWeaponItem.held(p, PlayerClass.TANK) ? 1 : 0)
                            + (p.getOffHandStack().getItem() instanceof com.yongye.item.TankShieldItem ? 1 : 0);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 60,
                            amp, true, false, false));
                }
                // 坦克嘲讽:周期性把附近怪物的目标拉到自己身上;手持镇魂半径×1.5
                if (slow && ClassManager.isActive(p, PlayerClass.TANK) && p.getWorld() instanceof ServerWorld sw) {
                    double r = cfg.tankTauntRadius * (ClassWeaponItem.held(p, PlayerClass.TANK) ? 1.5 : 1.0);
                    Box box = p.getBoundingBox().expand(r);
                    for (HostileEntity mob : sw.getEntitiesByClass(HostileEntity.class, box,
                            e -> e.isAlive() && e.getTarget() != p)) {
                        mob.setTarget(p);
                    }
                }
                // 刺客脱战加速:离开战斗一段时间后获得迅捷
                if (server.getTicks() % 20 == 0 && ClassManager.isActive(p, PlayerClass.ASSASSIN)) {
                    long last = lastCombat.getOrDefault(p.getUuid(), 0L);
                    if (p.getWorld().getTime() - last >= cfg.assassinUncombatTicks) {
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40,
                                Math.max(0, cfg.assassinSprintAmplifier), true, false, false));
                    }
                }
            }
        });

        Yongye.LOGGER.info("[永夜] 职业专属技能已挂载");
    }

    private static void bonusHit(LivingEntity target, DamageSource src, float dmg, net.minecraft.world.World world) {
        if (dmg <= 0) return;
        target.damage(src, dmg);
        target.timeUntilRegen = 0;
    }

    private static void feedback(net.minecraft.world.World world, LivingEntity target,
                                 net.minecraft.particle.ParticleEffect particle,
                                 net.minecraft.sound.SoundEvent sound, float pitch) {
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(particle, target.getX(), target.getBodyY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
            sw.playSound(null, target.getX(), target.getY(), target.getZ(), sound, SoundCategory.PLAYERS, 0.8f, pitch);
        }
    }
}
