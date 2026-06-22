package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Boss 专属机制(文档第 7 章的"技能频率/召唤/抗性/时间压力")。
 * 不修改 vanilla AI,而是用 tick 给已标记 IS_BOSS 的实体叠加全新能力:
 *  - 持续减伤(抗性)
 *  - 锁定最近玩家
 *  - 低血量狂暴(力量+速度)
 *  - 定期召唤援军
 *  - 定期冲击波(范围伤害+击退)
 */
public final class BossAbilityHandler {
    private BossAbilityHandler() {}

    private static final EntityType<?>[] MINIONS = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, EntityType.SPIDER
    };

    private static class BossState {
        int nextSummon;
        int nextShock;
        boolean enraged;
        BossState(int now) { this.nextSummon = now + 100; this.nextShock = now + 100; }
    }

    private static final Map<UUID, BossState> STATES = new HashMap<>();
    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableBoss || !cfg.enableBossAbilities) return;
            if (++tick < 10) return; // 每 0.5 秒
            tick = 0;
            int now = server.getTicks();
            Set<MobEntity> seen = new HashSet<>();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!(p.getWorld() instanceof ServerWorld world)) continue;
                Box box = p.getBoundingBox().expand(cfg.bossAggroRange);
                for (MobEntity m : world.getEntitiesByClass(MobEntity.class, box,
                        e -> e.isAlive() && e.getAttachedOrElse(ModAttachments.IS_BOSS, false))) {
                    if (seen.add(m)) applyAbilities(world, m, now, cfg);
                }
            }
        });

        // Boss 死亡清理状态
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getAttachedOrElse(ModAttachments.IS_BOSS, false)) {
                STATES.remove(entity.getUuid());
            }
        });

        Yongye.LOGGER.info("[永夜] Boss 专属机制已挂载");
    }

    private static void applyAbilities(ServerWorld world, MobEntity boss, int now, YongyeConfig cfg) {
        BossState st = STATES.computeIfAbsent(boss.getUuid(), k -> new BossState(now));

        // 持续减伤
        boss.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 0, true, false, false));

        // 锁定最近玩家
        PlayerEntity tgt = world.getClosestPlayer(boss.getX(), boss.getY(), boss.getZ(), cfg.bossAggroRange, false);
        if (tgt instanceof LivingEntity living) boss.setTarget(living);

        // 低血量狂暴
        if (!st.enraged && boss.getHealth() < boss.getMaxHealth() * cfg.bossEnrageThreshold) {
            st.enraged = true;
            boss.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 999999, 1, true, true, true));
            boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 999999, 1, true, true, true));
            world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, boss.getX(), boss.getY() + 1.5, boss.getZ(),
                    30, 1.0, 1.0, 1.0, 0.1);
            broadcast(world, "【Boss】" + boss.getName().getString() + " 进入狂暴!");
        }

        // 定期召唤援军
        if (now >= st.nextSummon) {
            st.nextSummon = now + cfg.bossSummonIntervalTicks;
            summon(world, boss, cfg);
        }

        // 定期冲击波
        if (now >= st.nextShock) {
            st.nextShock = now + cfg.bossShockIntervalTicks;
            shockwave(world, boss, cfg);
        }
    }

    private static void summon(ServerWorld world, MobEntity boss, YongyeConfig cfg) {
        int nearby = world.getEntitiesByClass(HostileEntity.class,
                new Box(boss.getBlockPos()).expand(20), e -> true).size();
        if (nearby >= cfg.bossSummonMaxNearby) return;
        var r = world.getRandom();
        for (int i = 0; i < cfg.bossSummonCount; i++) {
            int x = boss.getBlockX() + r.nextInt(7) - 3;
            int z = boss.getBlockZ() + r.nextInt(7) - 3;
            int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            MINIONS[r.nextInt(MINIONS.length)].spawn(world, new net.minecraft.util.math.BlockPos(x, y, z), SpawnReason.SPAWNER);
        }
        world.spawnParticles(ParticleTypes.SOUL, boss.getX(), boss.getY() + 1.0, boss.getZ(), 20, 0.5, 0.5, 0.5, 0.05);
    }

    private static void shockwave(ServerWorld world, MobEntity boss, YongyeConfig cfg) {
        world.spawnParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 0.5, boss.getZ(),
                1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, boss.getX(), boss.getY() + 0.5, boss.getZ(),
                12, cfg.bossShockRadius / 2.0, 0.3, cfg.bossShockRadius / 2.0, 0.0);
        Box area = new Box(boss.getBlockPos()).expand(cfg.bossShockRadius);
        for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, area, e -> e.isAlive())) {
            p.damage(world.getDamageSources().mobAttack(boss), (float) cfg.bossShockDamage);
            double dx = p.getX() - boss.getX();
            double dz = p.getZ() - boss.getZ();
            p.takeKnockback(1.2, -dx, -dz);
            p.velocityModified = true;
        }
    }

    private static void broadcast(ServerWorld world, String msg) {
        if (world.getServer() != null) {
            world.getServer().getPlayerManager().broadcast(Text.literal(msg).formatted(Formatting.DARK_RED), false);
        }
    }
}
