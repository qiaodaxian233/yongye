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

import java.util.ArrayList;
import java.util.Collections;
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
        // —— 精英格挡:第 N 天起,持盾精英按概率完全格挡一次「来自实体的攻击」(近战/弹射);环境/穿透伤害不挡 ——
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.eliteUseEquipment || cfg.eliteBlockChance <= 0) return true;
            if (!(entity instanceof MobEntity mob)) return true;
            if (!mob.getAttachedOrElse(ModAttachments.IS_ELITE, false)) return true;
            if (!mob.getOffHandStack().isOf(Items.SHIELD)) return true;
            if (source.getAttacker() == null) return true;
            if (mob.getRandom().nextDouble() >= cfg.eliteBlockChance) return true;
            if (mob.getWorld() instanceof ServerWorld sw) {
                sw.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                        SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.0f, 1.0f);
                sw.spawnParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + 1.2, mob.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
            }
            return false; // 格挡成功:本次伤害无效
        });

        // —— 词缀·死亡效果(m73):爆裂 AoE(不破坏地形)/ 分裂出 2 只小怪 ——
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof MobEntity mob)) return;
            int affix = mob.getAttachedOrElse(ModAttachments.ELITE_AFFIX, 0);
            if (affix == 0) return;
            if (!(mob.getWorld() instanceof ServerWorld sw)) return;
            YongyeConfig cfg = YongyeConfig.get();
            if ((affix & AF_EXPLODE) != 0) {
                for (LivingEntity le : sw.getEntitiesByClass(LivingEntity.class,
                        mob.getBoundingBox().expand(4.0), l -> l != mob && l.isAlive())) {
                    le.damage(sw.getDamageSources().magic(), (float) cfg.eliteAffixExplodeDamage);
                }
                sw.spawnParticles(ParticleTypes.EXPLOSION, mob.getX(), mob.getY() + 1, mob.getZ(), 6, 1, 1, 1, 0.1);
                sw.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.0f, 1.0f);
            }
            if ((affix & AF_SPLIT) != 0) {
                for (int i = 0; i < 2; i++) {
                    net.minecraft.entity.mob.ZombieEntity z =
                            new net.minecraft.entity.mob.ZombieEntity(net.minecraft.entity.EntityType.ZOMBIE, sw);
                    z.refreshPositionAndAngles(mob.getX() + (sw.getRandom().nextDouble() - 0.5) * 2,
                            mob.getY(), mob.getZ() + (sw.getRandom().nextDouble() - 0.5) * 2, 0, 0);
                    sw.spawnEntity(z);
                }
            }
        });

        // —— 精英缴械:命中玩家时概率夺走主手武器 + 随机一件穿戴的护甲,精英死亡掉落(击杀夺回)——
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            if (!(source.getAttacker() instanceof MobEntity attacker)) return true;
            if (!attacker.getAttachedOrElse(ModAttachments.IS_ELITE, false)) return true;
            // 嗜血词缀:命中玩家按比例回血(独立于缴械开关)
            if ((attacker.getAttachedOrElse(ModAttachments.ELITE_AFFIX, 0) & AF_LIFESTEAL) != 0) {
                attacker.heal((float) (amount * cfg.eliteLifestealRatio));
            }
            if (!cfg.eliteCanDisarm || cfg.eliteDisarmChance <= 0) return true;
            if (attacker.getAttachedOrElse(ModAttachments.STOLE_GEAR, false)) return true; // 一只怪只行窃一次,防累计丢失
            long now = player.getWorld().getTime();
            if (now < player.getAttachedOrElse(ModAttachments.DISARM_COOLDOWN_UNTIL, 0L)) return true;

            boolean stole = false;

            // 夺武器(覆盖精英自带的剑——那是免费的;玩家武器记下强化等级供找回)
            ItemStack held = player.getMainHandStack();
            if (!held.isEmpty() && EquipmentEnhancer.isWeapon(held)
                    && !held.getOrDefault(com.yongye.registry.ModComponents.DISARM_PROOF, false)
                    && player.getRandom().nextDouble() < cfg.eliteDisarmChance) {
                player.setAttached(ModAttachments.LOST_WEAPON_ENHANCE, Math.max(
                        player.getAttachedOrElse(ModAttachments.LOST_WEAPON_ENHANCE, 0), EquipmentEnhancer.getLevel(held)));
                attacker.equipStack(EquipmentSlot.MAINHAND, held.copy());
                attacker.setEquipmentDropChance(EquipmentSlot.MAINHAND, 1.0f);
                player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                player.sendMessage(Text.literal("精英怪夺走了你的武器!击杀它夺回").formatted(Formatting.RED), true);
                stole = true;
            }

            // 夺护甲/副手盾(随机一件已穿戴、未被守护、且精英对应槽为空的;抢到直接穿身上,击杀夺回)
            if (cfg.eliteStealArmor && player.getRandom().nextDouble() < cfg.eliteStealArmorChance) {
                // 四件护甲 + 副手(盾)。副手只夺「装备类」(盾/武器),不夺火把、食物、方块等杂物。
                EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND };
                java.util.List<EquipmentSlot> avail = new java.util.ArrayList<>();
                for (EquipmentSlot s : slots) {
                    ItemStack worn = player.getEquippedStack(s);
                    if (worn.isEmpty()) continue;                                                            // 空槽
                    if (worn.getOrDefault(com.yongye.registry.ModComponents.DISARM_PROOF, false)) continue;  // 已被守护
                    if (s == EquipmentSlot.OFFHAND) {
                        // 副手:只夺装备类(盾/武器),可覆盖精英自带的免费盾(同主手夺武器逻辑,后期精英也带盾)
                        if (!EquipmentEnhancer.isWardable(worn)) continue;
                    } else {
                        // 四件护甲:精英该槽已占则不夺(精英本不戴甲,正常为空)
                        if (!attacker.getEquippedStack(s).isEmpty()) continue;
                    }
                    avail.add(s);
                }
                if (!avail.isEmpty()) {
                    EquipmentSlot s = avail.get(player.getRandom().nextInt(avail.size()));
                    attacker.equipStack(s, player.getEquippedStack(s).copy());
                    attacker.setEquipmentDropChance(s, 1.0f);
                    player.equipStack(s, ItemStack.EMPTY);
                    String what = s == EquipmentSlot.OFFHAND ? "副手盾" : "护甲";
                    player.sendMessage(Text.literal("精英怪扒走了你的" + what + "!击杀它夺回").formatted(Formatting.RED), true);
                    stole = true;
                }
            }

            if (stole) {
                attacker.setAttached(ModAttachments.STOLE_GEAR, true);
                attacker.setPersistent(); // 抢了装备的精英不自然消失,确保玩家能击杀夺回
                player.setAttached(ModAttachments.DISARM_COOLDOWN_UNTIL, now + cfg.eliteDisarmCooldownTicks);
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
            }
            return true;
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableElite) return;
            if (!(entity instanceof MobEntity mob) || !(entity instanceof Monster)) return;
            if (BossHandler.isBoss(mob)) return;
            if (mob.getAttachedOrElse(ModAttachments.IS_BOSS, false)) return; // 已是(怪物)BOSS版,不再精英化
            if (mob.getAttachedOrElse(ModAttachments.IS_PAIN, false)) return;
            if (mob.getAttachedOrElse(ModAttachments.IS_HIM, false)) return;
            if (mob.getAttachedOrElse(ModAttachments.IS_ELITE, false)) {
                ELITES.add(mob); // 重新加载时恢复追踪
                return;
            }
            if (mob.getRandom().nextDouble() >= cfg.eliteChance * NightfallManager.getEliteChanceMultiplier() * ProgressionManager.eliteChanceMultiplier(mob.getWorld())) return;

            makeElite(mob, cfg);
            ELITES.add(mob);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ELITES.isEmpty()) return;
            YongyeConfig cfg = YongyeConfig.get();
            // 关键修复(m88):必须遍历"快照",不能直接迭代 ELITES 本体。
            // 原因:带「召唤」词缀的精英会在 tickElite 内调 sw.spawnEntity 召唤僵尸,
            //   而 spawnEntity 同步触发 ServerEntityEvents.ENTITY_LOAD → 该回调对刚生成的
            //   怪掷精英概率,命中即 ELITES.add(...)。这就是"遍历途中结构性修改集合",
            //   直接迭代下一次 next() 必抛 ConcurrentModificationException,打崩服务器 tick
            //   (双人在线时召唤怪更易命中精英化,故复现稳定)。这是单线程重入,与多线程、
            //   与 WeakHashMap 的 GC 回收均无关(GC 清理弱键不改 modCount)。
            // 解法:拷一份快照来遍历,死亡精英延后统一删除,对任何重入修改免疫——
            //   召唤出的怪照常能进 ELITES,只是本 tick 不处理、下一 tick 才纳入,互不干扰。
            List<MobEntity> snapshot = new ArrayList<>(ELITES);
            List<MobEntity> toRemove = null; // 待删的死亡精英;为空则保持 null,省去分配
            for (MobEntity e : snapshot) {
                if (e == null || !e.isAlive() || e.isRemoved()) {
                    if (toRemove == null) toRemove = new ArrayList<>();
                    toRemove.add(e);
                    continue;
                }
                if (!(e.getWorld() instanceof ServerWorld sw)) continue;
                tickElite(sw, e, cfg);
            }
            if (toRemove != null) {
                for (MobEntity dead : toRemove) {
                    ELITES.remove(dead);
                    LAST_TELEPORT_AGE.remove(dead);
                }
            }
        });

        Yongye.LOGGER.info("[永夜] 精英怪系统已挂载");
    }

    /**
     * 测试用:把玩家附近 radius 格内、尚未精英化的敌对怪物就地变成精英(复用 makeElite)。
     * 供 /yongye elite 命令调用,方便实机查看精英光环/属性,免去干等 4% 概率刷新。返回精英化的数量。
     */
    public static int makeNearbyElite(ServerPlayerEntity p, double radius) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return 0;
        YongyeConfig cfg = YongyeConfig.get();
        Box box = p.getBoundingBox().expand(radius);
        List<MobEntity> mobs = sw.getEntitiesByClass(MobEntity.class, box,
                m -> m.isAlive() && m instanceof Monster
                        && !BossHandler.isBoss(m)
                        && !m.getAttachedOrElse(ModAttachments.IS_PAIN, false)
                        && !m.getAttachedOrElse(ModAttachments.IS_HIM, false)
                        && !m.getAttachedOrElse(ModAttachments.IS_ELITE, false));
        int count = 0;
        for (MobEntity mob : mobs) {
            makeElite(mob, cfg);
            ELITES.add(mob);
            count++;
        }
        return count;
    }

    private static void makeElite(MobEntity mob, YongyeConfig cfg) {
        mob.setAttached(ModAttachments.IS_ELITE, true);

        addMultiplier(mob, EntityAttributes.GENERIC_MAX_HEALTH, ID_HEALTH, cfg.eliteHealthMultiplier);
        addMultiplier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_ATTACK, cfg.eliteAttackMultiplier);
        addMultiplier(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, ID_SPEED, cfg.eliteSpeedMultiplier);
        addFlat(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, ID_KB, cfg.eliteKnockbackResistanceAdd);
        addFlat(mob, EntityAttributes.GENERIC_FOLLOW_RANGE, ID_FOLLOW, cfg.eliteFollowRangeAdd);
        mob.setHealth(mob.getMaxHealth());

        // 第 N 天起:精英持武器(主手为空才给,不覆盖骷髅的弓)+ 盾牌(副手,用于格挡)
        if (cfg.eliteUseEquipment && ProgressionManager.gameDay(mob.getWorld()) >= cfg.eliteEquipStartDay) {
            if (mob.getMainHandStack().isEmpty()) {
                net.minecraft.item.Item[] weapons = { Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.IRON_AXE, Items.DIAMOND_AXE };
                mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(weapons[mob.getRandom().nextInt(weapons.length)]));
                mob.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
            }
            mob.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            mob.setEquipmentDropChance(EquipmentSlot.OFFHAND, 0.0f);
        }

        // 持续发光(默认关:实体描边会触发部分渲染mod崩溃;精英已有金色名牌识别)
        if (cfg.eliteGlowing) {
            mob.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.GLOWING, StatusEffectInstance.INFINITE, 0, true, false, false));
        }

        // 词缀(m73):按概率随机带 1~2 个,影响名牌与行为
        int affix = 0;
        if (cfg.enableEliteAffix && mob.getRandom().nextDouble() < cfg.eliteAffixChance) {
            affix = rollAffixes(mob.getRandom());
            mob.setAttached(ModAttachments.ELITE_AFFIX, affix);
        }
        // 专属名牌
        Text typeName = mob.getType().getName();
        net.minecraft.text.MutableText name = Text.literal("✦ 精英·").formatted(Formatting.GOLD).append(typeName);
        if (affix != 0) name.append(Text.literal(" " + affixNames(affix)).formatted(Formatting.RED));
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);
    }

    // ===== m73 精英词缀 =====
    static final int AF_EXPLODE = 1, AF_SPLIT = 2, AF_LIFESTEAL = 4, AF_POISON = 8, AF_SUMMON = 16;

    private static int rollAffixes(net.minecraft.util.math.random.Random r) {
        int[] all = { AF_EXPLODE, AF_SPLIT, AF_LIFESTEAL, AF_POISON, AF_SUMMON };
        int count = 1 + r.nextInt(2);
        int mask = 0;
        for (int i = 0; i < count; i++) mask |= all[r.nextInt(all.length)];
        return mask;
    }

    private static String affixNames(int a) {
        StringBuilder sb = new StringBuilder("【");
        if ((a & AF_EXPLODE) != 0) sb.append("爆裂");
        if ((a & AF_SPLIT) != 0) sb.append("分裂");
        if ((a & AF_LIFESTEAL) != 0) sb.append("嗜血");
        if ((a & AF_POISON) != 0) sb.append("剧毒");
        if ((a & AF_SUMMON) != 0) sb.append("召唤");
        return sb.append("】").toString();
    }

    private static void tickElite(ServerWorld sw, MobEntity e, YongyeConfig cfg) {
        // 精英光环特效:每 eliteAuraIntervalTicks tick 在周身喷一圈幽蓝魂火(纯服务端 spawnParticles,
        // 自动广播给附近玩家;不走发光描边,无 m21 那类渲染mod崩溃风险)。与金色名牌一样常显,作精英标识。
        if (cfg.eliteAuraEffect && e.age % Math.max(1, cfg.eliteAuraIntervalTicks) == 0) {
            spawnAura(sw, e);
        }

        // 词缀行为(m73):剧毒光环 / 召唤援军(按 age 错峰,避免每 tick 触发)
        int affix = e.getAttachedOrElse(ModAttachments.ELITE_AFFIX, 0);
        if (affix != 0) {
            if ((affix & AF_POISON) != 0 && e.age % 40 == 0) {
                for (net.minecraft.server.network.ServerPlayerEntity p : sw.getEntitiesByClass(
                        net.minecraft.server.network.ServerPlayerEntity.class,
                        e.getBoundingBox().expand(4.0), pl -> pl.isAlive())) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 0));
                }
            }
            if ((affix & AF_SUMMON) != 0 && e.age % 120 == 0 && e.getTarget() != null) {
                net.minecraft.entity.mob.ZombieEntity add =
                        new net.minecraft.entity.mob.ZombieEntity(net.minecraft.entity.EntityType.ZOMBIE, sw);
                add.refreshPositionAndAngles(e.getX() + (sw.getRandom().nextDouble() - 0.5) * 3,
                        e.getY(), e.getZ() + (sw.getRandom().nextDouble() - 0.5) * 3, 0, 0);
                sw.spawnEntity(add);
                add.setTarget(e.getTarget());
            }
        }

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

    /** 精英光环:脚下一圈随时间旋转的幽蓝魂火 + 少量上升魂粒,营造"被诅咒的强敌"气场。纯服务端粒子,零渲染风险。 */
    private static void spawnAura(ServerWorld sw, MobEntity e) {
        double cx = e.getX();
        double cy = e.getY();
        double cz = e.getZ();
        double h = e.getHeight();
        // 脚下一圈魂火,随 age 缓慢旋转(约 2 秒一圈),形成环绕的火环
        final int points = 5;
        final double radius = 0.55;
        double base = (e.age % 40) / 40.0 * (Math.PI * 2.0);
        for (int i = 0; i < points; i++) {
            double a = base + i * (Math.PI * 2.0 / points);
            double px = cx + Math.cos(a) * radius;
            double pz = cz + Math.sin(a) * radius;
            // count=1、零速度 = 在该点定住一簇幽蓝魂火
            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, cy + 0.15, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // 周身少量上升魂粒
        sw.spawnParticles(ParticleTypes.SOUL, cx, cy + h * 0.5, cz, 1, 0.25, h * 0.35, 0.25, 0.01);
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
