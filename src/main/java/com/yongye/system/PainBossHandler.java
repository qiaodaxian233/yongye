package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.ArtifactItem;
import com.yongye.item.ArtifactType;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.item.SkillBookItem;
import com.yongye.item.SkillType;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 长门(佩恩)Boss。以 Husk 为载体(不怕日晒),靠自定义名「佩恩·天道」让客户端套长门皮肤,
 * 全部技能由本处理器以 tick 驱动(零 mixin):
 *   神罗天征(范围排斥)/ 万象牵引(范围拉拽)/ 地爆天星(延迟爆心)/ 轮回天生(残血复活一次)。
 */
public final class PainBossHandler {
    private PainBossHandler() {}

    private static final String NAME = "佩恩·天道";

    private static class PainState {
        int nextAbility;
        boolean rebirthUsed;
        long devastationAt;
        Vec3d devastationCenter;
        PainState(int now) { this.nextAbility = now + 80; }
    }

    private static final Map<UUID, PainState> STATES = new HashMap<>();
    private static int tick = 0;

    /** 当前存活的长门(全局唯一);null 表示不存在。 */
    private static UUID activePain = null;
    private static int naturalCheckTick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 终局自然降临检定(独立计时,不受下面的 10 tick 节流影响)
            maybeNaturalSpawn(server);

            if (++tick < 10) return;
            tick = 0;
            int now = server.getTicks();
            Set<MobEntity> seen = new HashSet<>();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!(p.getWorld() instanceof ServerWorld world)) continue;
                Box box = p.getBoundingBox().expand(48);
                for (MobEntity m : world.getEntitiesByClass(MobEntity.class, box,
                        e -> e.isAlive() && e.getAttachedOrElse(ModAttachments.IS_PAIN, false))) {
                    if (seen.add(m)) tickPain(world, m, now);
                }
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!entity.getAttachedOrElse(ModAttachments.IS_PAIN, false)) return;
            STATES.remove(entity.getUuid());
            if (entity.getUuid().equals(activePain)) activePain = null;
            if (entity.getWorld() instanceof ServerWorld world) {
                dropRewards(world, entity);
                // 击败长门 → 大幅赎夜,作为对抗永夜升级的泄压阀
                YongyeConfig cfg = YongyeConfig.get();
                if (cfg.painDeathRedeemLevels > 0 && world.getServer() != null) {
                    int cur = NightfallManager.getLevel();
                    if (cur > 0) {
                        NightfallManager.setLevel(world.getServer(),
                                cur - cfg.painDeathRedeemLevels);
                        world.getServer().getPlayerManager().broadcast(
                                Text.literal("【六道终焉】佩恩被击败,黑暗暂退!").formatted(Formatting.AQUA), false);
                    }
                }
            }
        });

        Yongye.LOGGER.info("[亡途荒夜] 长门(佩恩)Boss 已挂载");
    }

    /** 永夜达到阈值后,按概率让长门作为「六道之痛」自然降临(全局唯一)。 */
    private static void maybeNaturalSpawn(MinecraftServer server) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.painNaturalSpawn) return;
        if (++naturalCheckTick < cfg.painNaturalCheckIntervalTicks) return;
        naturalCheckTick = 0;

        if (activePain != null) return;                              // 已存在,不重复
        if (NightfallManager.getLevel() < cfg.painSpawnMinNightfall) return;
        if (server.getPlayerManager().getPlayerList().isEmpty()) return;
        if (server.getOverworld().getRandom().nextDouble() > cfg.painNaturalSpawnChance) return;

        // 随机挑一名玩家,在其附近降临
        var players = server.getPlayerManager().getPlayerList();
        ServerPlayerEntity target = players.get(server.getOverworld().getRandom().nextInt(players.size()));
        MobEntity pain = spawnPainBossNear(target);
        if (pain != null) activePain = pain.getUuid();
    }

    private static void tickPain(ServerWorld world, MobEntity pain, int now) {
        YongyeConfig cfg = YongyeConfig.get();
        PainState st = STATES.computeIfAbsent(pain.getUuid(), k -> new PainState(now));

        PlayerEntity tgt = world.getClosestPlayer(pain.getX(), pain.getY(), pain.getZ(), 48, false);
        if (tgt instanceof LivingEntity living) pain.setTarget(living);

        // 轮回天生:残血一次性满血复活
        if (!st.rebirthUsed && pain.getHealth() < pain.getMaxHealth() * cfg.painRebirthThreshold) {
            st.rebirthUsed = true;
            pain.setHealth(pain.getMaxHealth());
            pain.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 2, true, true, true));
            world.spawnParticles(ParticleTypes.END_ROD, pain.getX(), pain.getY() + 1.0, pain.getZ(), 60, 0.6, 1.0, 0.6, 0.2);
            broadcast(world, "【佩恩】轮回天生!");
        }

        // 地爆天星延迟爆发
        if (st.devastationAt > 0 && now >= st.devastationAt) {
            detonate(world, st.devastationCenter, cfg);
            st.devastationAt = 0;
            st.devastationCenter = null;
        }

        // 施放技能
        if (st.devastationAt == 0 && now >= st.nextAbility && tgt != null) {
            st.nextAbility = now + cfg.painAbilityIntervalTicks;
            switch (world.getRandom().nextInt(3)) {
                case 0 -> almightyPush(world, pain, cfg);
                case 1 -> universalPull(world, pain, cfg);
                default -> planetaryDevastation(world, pain, tgt, st, now, cfg);
            }
        }
    }

    /** 神罗天征:范围强力排斥 + 伤害。 */
    private static void almightyPush(ServerWorld world, MobEntity pain, YongyeConfig cfg) {
        broadcast(world, "【佩恩】神罗天征!");
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pain.getX(), pain.getY() + 0.5, pain.getZ(), 1, 0, 0, 0, 0);
        Box area = new Box(pain.getBlockPos()).expand(cfg.painPushRadius);
        for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, area, e -> e.isAlive())) {
            p.damage(world.getDamageSources().magic(), (float) cfg.painPushDamage);
            double dx = p.getX() - pain.getX();
            double dz = p.getZ() - pain.getZ();
            p.takeKnockback(2.6, -dx, -dz);
            p.addVelocity(0, 0.5, 0);
            p.velocityModified = true;
        }
    }

    /** 万象牵引:把范围内玩家拉向佩恩。 */
    private static void universalPull(ServerWorld world, MobEntity pain, YongyeConfig cfg) {
        broadcast(world, "【佩恩】万象牵引!");
        world.spawnParticles(ParticleTypes.PORTAL, pain.getX(), pain.getY() + 1.0, pain.getZ(), 40, 0.4, 0.8, 0.4, 0.6);
        Box area = new Box(pain.getBlockPos()).expand(cfg.painPullRadius);
        for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, area, e -> e.isAlive())) {
            Vec3d dir = new Vec3d(pain.getX() - p.getX(), 0.2, pain.getZ() - p.getZ()).normalize();
            p.setVelocity(dir.x * 1.5, 0.25, dir.z * 1.5);
            p.velocityModified = true;
            p.damage(world.getDamageSources().magic(), 3.0f);
        }
    }

    /** 地爆天星:在目标上方聚成爆心,延迟后坍缩造成大范围伤害。 */
    private static void planetaryDevastation(ServerWorld world, MobEntity pain, PlayerEntity tgt,
                                             PainState st, int now, YongyeConfig cfg) {
        broadcast(world, "【佩恩】地爆天星!");
        Vec3d center = new Vec3d(tgt.getX(), tgt.getY() + 10, tgt.getZ());
        st.devastationCenter = center;
        st.devastationAt = now + 70;
        // 周围实体被拉升(漂浮)
        Box area = new Box(tgt.getBlockPos()).expand(cfg.painDevastationRadius + 2);
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e.isAlive() && !e.getAttachedOrElse(ModAttachments.IS_PAIN, false))) {
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 70, 2, false, false, false));
        }
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 80, 1.0, 1.0, 1.0, 0.1);
    }

    private static void detonate(ServerWorld world, Vec3d center, YongyeConfig cfg) {
        if (center == null) return;
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 3, 1, 1, 1, 0);
        Box area = Box.of(center, cfg.painDevastationRadius * 2, cfg.painDevastationRadius * 2, cfg.painDevastationRadius * 2);
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e.isAlive() && !e.getAttachedOrElse(ModAttachments.IS_PAIN, false))) {
            le.damage(world.getDamageSources().magic(), (float) cfg.painDevastationDamage);
            le.addVelocity(0, -1.0, 0);
            le.velocityModified = true;
        }
    }

    /** 在玩家附近生成长门 Boss,返回实体。 */
    public static MobEntity spawnPainBossNear(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return null;
        var r = world.getRandom();
        int x = player.getBlockX() + (r.nextInt(11) - 5);
        int z = player.getBlockZ() + (r.nextInt(11) - 5);
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);

        HuskEntity pain = new HuskEntity(EntityType.HUSK, world);
        pain.refreshPositionAndAngles(x + 0.5, y, z + 0.5, r.nextFloat() * 360, 0);
        pain.setCustomName(Text.literal(NAME).formatted(Formatting.DARK_RED));
        pain.setCustomNameVisible(true);
        pain.setPersistent();
        pain.setAttached(ModAttachments.IS_PAIN, true);

        YongyeConfig cfg = YongyeConfig.get();
        setBase(pain, EntityAttributes.GENERIC_MAX_HEALTH, cfg.painBossMaxHealth);
        setBase(pain, EntityAttributes.GENERIC_ATTACK_DAMAGE, cfg.painBossAttack);
        setBase(pain, EntityAttributes.GENERIC_ARMOR, cfg.painBossArmor);
        setBase(pain, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
        setBase(pain, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3);
        setBase(pain, EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
        pain.setHealth(pain.getMaxHealth());

        world.spawnEntity(pain);
        if (world.getServer() != null) {
            world.getServer().getPlayerManager().broadcast(
                    Text.literal("【佩恩】六道之痛降临……").formatted(Formatting.DARK_RED), false);
        }
        return pain;
    }

    private static void setBase(MobEntity m, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr, double v) {
        EntityAttributeInstance inst = m.getAttributeInstance(attr);
        if (inst != null) inst.setBaseValue(v);
    }

    private static void dropRewards(ServerWorld world, LivingEntity pain) {
        var r = world.getRandom();
        drop(world, pain, new ItemStack(ModItems.ENDING_ESSENCE, 1 + r.nextInt(2)));
        drop(world, pain, new ItemStack(ModItems.CATASTROPHE_BLOOD_CORE, 1 + r.nextInt(2)));
        drop(world, pain, new ItemStack(ModItems.LIFE_CORE, 2));
        drop(world, pain, HealthSkillBookItem.create(15 + r.nextInt(16)));   // V15~V30
        for (int i = 0; i < 3; i++) {
            SkillType st = SkillType.values()[r.nextInt(SkillType.values().length)];
            drop(world, pain, SkillBookItem.create(st, 5 + r.nextInt(11))); // V5~V15
        }
        // 必掉一件高级神器
        ArtifactType at = ArtifactType.values()[r.nextInt(ArtifactType.values().length)];
        drop(world, pain, ArtifactItem.create(at, 4 + r.nextInt(3)));        // 4~6 级
        drop(world, pain, new ItemStack(Items.NETHERITE_INGOT, 2 + r.nextInt(3)));
        drop(world, pain, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 3));
        if (r.nextDouble() < 0.15) {
            drop(world, pain, new ItemStack(ModItems.CHAOS_BLADE));
        }
    }

    private static void drop(ServerWorld world, LivingEntity src, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity ie = new ItemEntity(world, src.getX(), src.getY() + 0.5, src.getZ(), stack);
        ie.setToDefaultPickupDelay();
        world.spawnEntity(ie);
    }

    private static void broadcast(ServerWorld world, String msg) {
        if (world.getServer() != null) {
            world.getServer().getPlayerManager().broadcast(Text.literal(msg).formatted(Formatting.LIGHT_PURPLE), false);
        }
    }
}
