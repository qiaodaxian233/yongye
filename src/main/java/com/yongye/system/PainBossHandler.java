package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.ArtifactItem;
import com.yongye.item.ArtifactType;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.item.SkillBookItem;
import com.yongye.item.SkillType;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModSounds;
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
        double devastationDamage;
        int phase = 1;
        PainState(int now) { this.nextAbility = now + 80; }
    }

    private static final Map<UUID, PainState> STATES = new HashMap<>();
    private static int tick = 0;

    /** 当前存活的长门(全局唯一);null 表示不存在。 */
    private static UUID activePain = null;
    private static int naturalCheckTick = 0;
    private static long painLastTargetTick = 0; // 佩恩最近一次拥有目标的游戏刻;长时间无目标则传送

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 终局自然降临检定(独立计时,不受下面的 10 tick 节流影响)
            maybeNaturalSpawn(server);
            // 长时间找不到玩家 → 传送到随机玩家身边追杀
            maybeRelocatePain(server, server.getTicks());

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
                stopBgmNear(world, entity);
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

        Yongye.LOGGER.info("[永夜] 长门(佩恩)Boss 已挂载");
    }

    /** 永夜达到阈值后,按概率让长门作为「六道之痛」自然降临(全局唯一)。 */
    private static void maybeNaturalSpawn(MinecraftServer server) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.painNaturalSpawn) return;
        if (++naturalCheckTick < cfg.painNaturalCheckIntervalTicks) return;
        naturalCheckTick = 0;

        if (activePain != null) return;                              // 已存在,不重复
        if (NightfallManager.getLevel() < cfg.painSpawnMinNightfall) return;
        if (ProgressionManager.gameDay(server.getOverworld()) < cfg.painSpawnMinDay) return; // 早期不降临
        if (server.getPlayerManager().getPlayerList().isEmpty()) return;

        // 重启兜底:若已有持久长门在某玩家附近加载着,认领它,避免再刷一个
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!(p.getWorld() instanceof ServerWorld w)) continue;
            var found = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(128),
                    e -> e.isAlive() && e.getAttachedOrElse(ModAttachments.IS_PAIN, false));
            if (!found.isEmpty()) { activePain = found.get(0).getUuid(); return; }
        }

        if (server.getOverworld().getRandom().nextDouble() > cfg.painNaturalSpawnChance) return;

        // 随机挑一名玩家,在其附近降临
        var players = server.getPlayerManager().getPlayerList();
        ServerPlayerEntity target = players.get(server.getOverworld().getRandom().nextInt(players.size()));
        MobEntity pain = spawnPainBossNear(target);
        if (pain != null) activePain = pain.getUuid();
    }

    /** 佩恩长时间(painLostTeleportTicks)没有目标 → 传送到同世界一名随机玩家身边继续追杀。 */
    private static void maybeRelocatePain(MinecraftServer server, int now) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.painLostTeleport || activePain == null) return;
        if (now - painLastTargetTick < cfg.painLostTeleportTicks) return;
        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;
        for (ServerPlayerEntity p : players) {
            if (!(p.getWorld() instanceof ServerWorld w)) continue;
            var found = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(160),
                    e -> e.isAlive() && e.getAttachedOrElse(ModAttachments.IS_PAIN, false));
            if (found.isEmpty()) continue;
            java.util.List<ServerPlayerEntity> same = new java.util.ArrayList<>();
            for (ServerPlayerEntity q : players) if (q.getWorld() == w) same.add(q);
            if (same.isEmpty()) return;
            relocatePain(w, found.get(0), same.get(w.getRandom().nextInt(same.size())));
            painLastTargetTick = now;
            return;
        }
    }

    private static void relocatePain(ServerWorld world, MobEntity pain, ServerPlayerEntity target) {
        var r = world.getRandom();
        int x = target.getBlockX() + (r.nextInt(11) - 5);
        int z = target.getBlockZ() + (r.nextInt(11) - 5);
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        pain.refreshPositionAndAngles(x + 0.5, y, z + 0.5, r.nextFloat() * 360, 0);
        pain.setTarget(target);
        playBgmNear(world, pain);
        if (world.getServer() != null) {
            world.getServer().getPlayerManager().broadcast(
                    Text.literal("【佩恩】六道之痛已锁定新的猎物……").formatted(Formatting.DARK_RED), false);
        }
    }

    private static void tickPain(ServerWorld world, MobEntity pain, int now) {
        YongyeConfig cfg = YongyeConfig.get();
        PainState st = STATES.computeIfAbsent(pain.getUuid(), k -> new PainState(now));

        PlayerEntity tgt = world.getClosestPlayer(pain.getX(), pain.getY(), pain.getZ(), 48, false);
        if (tgt instanceof LivingEntity living) pain.setTarget(living);
        if (pain.getTarget() != null && pain.getTarget().isAlive()) painLastTargetTick = now;

        // 阶段化(m73):按血量分 3 阶段,进阶时狂暴(力量/速度叠加)并立即施法
        if (cfg.enablePainPhases) {
            double pct = pain.getHealth() / pain.getMaxHealth();
            int newPhase = pct > 0.66 ? 1 : (pct > 0.33 ? 2 : 3);
            if (newPhase > st.phase) {
                st.phase = newPhase;
                int amp = newPhase - 1; // 阶段2→I,阶段3→II
                pain.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, StatusEffectInstance.INFINITE, amp, true, false, true));
                pain.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, StatusEffectInstance.INFINITE, amp, true, false, true));
                pain.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0, true, false, true));
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pain.getX(), pain.getY() + 1.0, pain.getZ(), 80, 0.8, 1.2, 0.8, 0.05);
                broadcast(world, "【佩恩】进入第 " + newPhase + " 阶段——痛苦加深!");
                st.nextAbility = now; // 进阶即刻施法
            }
        }

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
            detonate(world, st.devastationCenter, st.devastationDamage, cfg);
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
        playSkill(world, pain, ModSounds.PAIN_ALMIGHTY_PUSH);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pain.getX(), pain.getY() + 0.5, pain.getZ(), 1, 0, 0, 0, 0);
        Box area = new Box(pain.getBlockPos()).expand(cfg.painPushRadius);
        for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, area, e -> e.isAlive())) {
            p.damage(world.getDamageSources().magic(), (float) (pain.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * cfg.painPushAttackRatio));
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
        playSkill(world, pain, ModSounds.PAIN_UNIVERSAL_PULL);
        world.spawnParticles(ParticleTypes.PORTAL, pain.getX(), pain.getY() + 1.0, pain.getZ(), 40, 0.4, 0.8, 0.4, 0.6);
        Box area = new Box(pain.getBlockPos()).expand(cfg.painPullRadius);
        for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, area, e -> e.isAlive())) {
            Vec3d dir = new Vec3d(pain.getX() - p.getX(), 0.2, pain.getZ() - p.getZ()).normalize();
            p.setVelocity(dir.x * 1.5, 0.25, dir.z * 1.5);
            p.velocityModified = true;
            p.damage(world.getDamageSources().magic(), (float) (pain.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * cfg.painPullAttackRatio));
        }
    }

    /** 地爆天星:在目标上方聚成爆心,延迟后坍缩造成大范围伤害。 */
    private static void planetaryDevastation(ServerWorld world, MobEntity pain, PlayerEntity tgt,
                                             PainState st, int now, YongyeConfig cfg) {
        broadcast(world, "【佩恩】地爆天星!");
        playSkill(world, pain, ModSounds.PAIN_PLANETARY);
        Vec3d center = new Vec3d(tgt.getX(), tgt.getY() + 10, tgt.getZ());
        st.devastationCenter = center;
        st.devastationAt = now + 70;
        st.devastationDamage = pain.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * cfg.painDevastationAttackRatio;
        // 周围实体被拉升(漂浮)
        Box area = new Box(tgt.getBlockPos()).expand(cfg.painDevastationRadius + 2);
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e.isAlive() && !e.getAttachedOrElse(ModAttachments.IS_PAIN, false))) {
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 70, 2, false, false, false));
        }
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 80, 1.0, 1.0, 1.0, 0.1);
    }

    private static void detonate(ServerWorld world, Vec3d center, double damage, YongyeConfig cfg) {
        if (center == null) return;
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 3, 1, 1, 1, 0);
        Box area = Box.of(center, cfg.painDevastationRadius * 2, cfg.painDevastationRadius * 2, cfg.painDevastationRadius * 2);
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e.isAlive() && !e.getAttachedOrElse(ModAttachments.IS_PAIN, false))) {
            le.damage(world.getDamageSources().magic(), (float) damage);
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
        // 时间线增强:复用怪物缩放公式(永夜等级 + 游戏天数 + 附近玩家强度 + 进化阶段,封顶 mobScalingMaxMultiplier)。
        // 血量按整倍率,攻击按 mobScalingAttackRatio 比例(与普通怪一致,避免攻击膨胀过猛)。
        double prog = cfg.enableMobScaling ? MobEnhancementHandler.progressionMultiplier(pain, cfg) : 1.0;
        double atkProg = 1.0 + (prog - 1.0) * cfg.mobScalingAttackRatio;
        setBase(pain, EntityAttributes.GENERIC_MAX_HEALTH, cfg.painBossMaxHealth * prog);
        setBase(pain, EntityAttributes.GENERIC_ATTACK_DAMAGE, cfg.painBossAttack * atkProg);
        setBase(pain, EntityAttributes.GENERIC_ARMOR, cfg.painBossArmor);
        setBase(pain, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
        setBase(pain, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3);
        setBase(pain, EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
        pain.setHealth(pain.getMaxHealth());
        if (prog > 1.01) {
            Yongye.LOGGER.info(String.format("[永夜] 佩恩降临:进度倍率 ×%.2f → 血量 %.0f / 攻击 %.0f",
                    prog, cfg.painBossMaxHealth * prog, cfg.painBossAttack * atkProg));
        }

        world.spawnEntity(pain);
        if (world.getServer() != null) painLastTargetTick = world.getServer().getTicks();
        playBgmNear(world, pain);
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

    /** 在佩恩处播放技能音效。 */
    private static void playSkill(ServerWorld world, MobEntity pain, net.minecraft.sound.SoundEvent sound) {
        world.playSound(null, pain.getX(), pain.getY(), pain.getZ(), sound,
                net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 1.0f);
    }

    /** 降临 BGM:对附近玩家播放遭遇音乐。 */
    private static void playBgmNear(ServerWorld world, MobEntity pain) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(pain) <= 64 * 64) {
                p.playSoundToPlayer(ModSounds.PAIN_BGM, net.minecraft.sound.SoundCategory.MUSIC, 1.0f, 1.0f);
            }
        }
    }

    /** 佩恩死亡:让附近玩家停止 BGM。 */
    private static void stopBgmNear(ServerWorld world, LivingEntity pain) {
        var pkt = new net.minecraft.network.packet.s2c.play.StopSoundS2CPacket(
                net.minecraft.util.Identifier.of(com.yongye.Yongye.MOD_ID, "pain_bgm"),
                net.minecraft.sound.SoundCategory.MUSIC);
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(pain) <= 96 * 96) p.networkHandler.sendPacket(pkt);
        }
    }

    private static void broadcast(ServerWorld world, String msg) {
        if (world.getServer() != null) {
            world.getServer().getPlayerManager().broadcast(Text.literal(msg).formatted(Formatting.LIGHT_PURPLE), false);
        }
    }
}
