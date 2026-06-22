package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.ArtifactItem;
import com.yongye.item.ArtifactType;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * 背包神器生效逻辑(文档第 14 章)。神器放在背包/物品栏即生效,同类只取最高等级。
 */
public final class ArtifactManager {
    private ArtifactManager() {}

    private static final Identifier ID_LIFE = Identifier.of(Yongye.MOD_ID, "artifact_life");
    private static final Identifier ID_SPEED = Identifier.of(Yongye.MOD_ID, "artifact_speed");

    private static final double[] LIFE_IDOL_HP = {20, 50, 100, 300, 1000, 5000};
    private static final double[] ESCAPIST_SPEED = {0.05, 0.10, 0.15, 0.20, 0.30, 0.40};
    private static final int[] IRON_CORE_AMP = {0, 0, 1, 1, 2, 2};
    private static final double[] GLUTTON_HEAL = {2, 5, 10, 20, 50, 100};
    private static final int[] EMBER_COOLDOWN_MIN = {24, 18, 12, 8, 5, 5};

    private static int tickCounter = 0;

    /** 扫描玩家物品栏,返回某神器的最高等级(无则 0)。 */
    public static int getActiveLevel(PlayerEntity player, ArtifactType type) {
        int best = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() instanceof ArtifactItem ai && ai.getType() == type) {
                best = Math.max(best, ArtifactItem.getLevel(s));
            }
        }
        // 饰品栏中的神器同样生效
        for (ItemStack s : AccessoryStorage.stacks(player)) {
            if (s.getItem() instanceof ArtifactItem ai && ai.getType() == type) {
                best = Math.max(best, ArtifactItem.getLevel(s));
            }
        }
        return best;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!YongyeConfig.get().enableArtifacts) return;
            if (++tickCounter < 10) return;
            tickCounter = 0;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                applyAll(p);
            }
        });

        // 饕餮心脏:击杀回血
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) return;
            if (killer == entity) return;
            int lvl = getActiveLevel(killer, ArtifactType.GLUTTON_HEART);
            if (lvl <= 0) return;
            if (isHealBlocked(killer)) return;
            double heal = GLUTTON_HEAL[lvl - 1];
            boolean elite = entity.getAttachedOrElse(ModAttachments.IS_ELITE, false)
                    || entity.getAttachedOrElse(ModAttachments.IS_BOSS, false);
            if (lvl >= 6 && elite) heal *= 4;
            killer.heal((float) heal);
        });

        // 不灭余烬:受致命伤保命
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            int lvl = getActiveLevel(player, ArtifactType.UNDYING_EMBER);
            if (lvl <= 0) return true;
            long now = player.getWorld().getTime();
            long readyAt = player.getAttachedOrElse(ModAttachments.EMBER_READY_AT, 0L);
            if (now < readyAt) return true; // 冷却中,正常死亡

            player.setHealth(player.getMaxHealth() * 0.5f);
            player.clearStatusEffects();
            long cd = (long) EMBER_COOLDOWN_MIN[lvl - 1] * 60L * 20L;
            player.setAttached(ModAttachments.EMBER_READY_AT, now + cd);

            if (player.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
                        60, 0.4, 0.6, 0.4, 0.2);
                sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
            if (lvl >= 6) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1));
            }
            player.sendMessage(Text.literal("【不灭余烬】你从死亡边缘归来").formatted(Formatting.GOLD), true);
            return false; // 取消死亡
        });

        Yongye.LOGGER.info("[永夜] 背包神器系统已挂载");
    }

    private static void applyAll(ServerPlayerEntity p) {
        // 生命神像
        applyAttribute(p, EntityAttributes.GENERIC_MAX_HEALTH, ID_LIFE,
                level(p, ArtifactType.LIFE_IDOL), LIFE_IDOL_HP, EntityAttributeModifier.Operation.ADD_VALUE);
        // 逃亡者之靴
        applyAttribute(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, ID_SPEED,
                level(p, ArtifactType.ESCAPIST_BOOTS), ESCAPIST_SPEED, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        // 铁壁核心(近似:抗性)
        int iron = level(p, ArtifactType.IRON_CORE);
        if (iron > 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40,
                    IRON_CORE_AMP[iron - 1], true, false, false));
        }

        // 永夜之眼:永夜期间夜视
        int eye = level(p, ArtifactType.NIGHTFALL_EYE);
        if (eye > 0 && NightfallManager.getLevel() >= 1) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, true, false, false));
        }

        // 巫毒净瓶:清除/削弱负面
        int voodoo = level(p, ArtifactType.VOODOO_BOTTLE);
        if (voodoo > 0) purgeNegatives(p, voodoo);

        // 掘墓人罗盘:指向最近高价值目标
        int compass = level(p, ArtifactType.GRAVEDIGGER_COMPASS);
        if (compass > 0) pointToTarget(p);
    }

    private static int level(ServerPlayerEntity p, ArtifactType t) {
        return getActiveLevel(p, t);
    }

    private static void applyAttribute(ServerPlayerEntity p, RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                                       Identifier id, int lvl, double[] table, EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (lvl > 0) {
            inst.addTemporaryModifier(new EntityAttributeModifier(id, table[lvl - 1], op));
        }
    }

    private static void purgeNegatives(ServerPlayerEntity p, int lvl) {
        List<RegistryEntry<net.minecraft.entity.effect.StatusEffect>> harmful = new ArrayList<>();
        for (StatusEffectInstance inst : p.getStatusEffects()) {
            if (inst.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL) {
                harmful.add(inst.getEffectType());
            }
        }
        if (harmful.isEmpty()) return;

        if (lvl >= 6) {
            for (var eff : harmful) p.removeStatusEffect(eff);
            return;
        }
        double removeChance = lvl == 5 ? 0.4 : lvl == 4 ? 0.2 : 0.0;
        double reduceFactor = new double[]{0.9, 0.8, 0.65, 0.65, 0.65, 0.65}[lvl - 1];
        for (var eff : harmful) {
            if (removeChance > 0 && p.getRandom().nextDouble() < removeChance) {
                p.removeStatusEffect(eff);
                continue;
            }
            StatusEffectInstance inst = p.getStatusEffect(eff);
            if (inst != null && inst.getDuration() > 20) {
                int nd = (int) (inst.getDuration() * reduceFactor);
                p.removeStatusEffect(eff);
                p.addStatusEffect(new StatusEffectInstance(eff, nd, inst.getAmplifier()));
            }
        }
    }

    private static void pointToTarget(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld world)) return;

        // 优先指向最近的灾厄核心(文档 14:掘墓人罗盘指向灾厄核心)
        net.minecraft.util.math.BlockPos core = CatastropheCoreManager.getNearest(p.getBlockPos());
        if (core != null) {
            double cdx = core.getX() + 0.5 - p.getX();
            double cdz = core.getZ() + 0.5 - p.getZ();
            int cdist = (int) Math.sqrt(cdx * cdx + cdz * cdz);
            p.sendMessage(Text.literal("【掘墓罗盘】灾厄核心:" + compassDir(cdx, cdz) + " " + cdist + " 格")
                    .formatted(Formatting.DARK_RED), true);
            return;
        }

        Box box = p.getBoundingBox().expand(64);
        List<MobEntity> targets = world.getEntitiesByClass(MobEntity.class, box,
                m -> m.isAlive() && (m.getAttachedOrElse(ModAttachments.IS_ELITE, false)
                        || m.getAttachedOrElse(ModAttachments.IS_BOSS, false)));
        if (targets.isEmpty()) return;
        MobEntity nearest = targets.get(0);
        double best = nearest.squaredDistanceTo(p);
        for (MobEntity m : targets) {
            double d = m.squaredDistanceTo(p);
            if (d < best) { best = d; nearest = m; }
        }
        double dx = nearest.getX() - p.getX();
        double dz = nearest.getZ() - p.getZ();
        String dir = compassDir(dx, dz);
        int dist = (int) Math.sqrt(best);
        p.sendMessage(Text.literal("【掘墓罗盘】高价值目标:" + dir + " " + dist + " 格")
                .formatted(Formatting.LIGHT_PURPLE), true);
    }

    private static String compassDir(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(-dx, dz)); // 0=南? 统一成 8 方向
        if (ang < 0) ang += 360;
        String[] dirs = {"南", "西南", "西", "西北", "北", "东北", "东", "东南"};
        return dirs[(int) Math.round(ang / 45.0) % 8];
    }

    /** 禁疗判定:供饕餮心脏等回血效果检查。 */
    public static boolean isHealBlocked(ServerPlayerEntity player) {
        long until = player.getAttachedOrElse(ModAttachments.NO_HEAL_UNTIL, 0L);
        return player.getWorld().getTime() < until;
    }
}
