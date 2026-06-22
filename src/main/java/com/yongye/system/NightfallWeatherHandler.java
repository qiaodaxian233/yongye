package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

/**
 * 永夜天象:永夜 ≥1 时,周期性随机降下灾害,按永夜等级解锁:
 *   血月(怪群狂暴) / 酸雨(露天腐蚀) / 流星雨(天降火劫)。
 * 纯服务端实现——血月的"红天"、浓雾等属客户端渲染,本系统不做(用广播 + 玩法效果替代)。
 */
public final class NightfallWeatherHandler {
    private NightfallWeatherHandler() {}

    private enum Event { NONE, BLOOD_MOON, ACID_RAIN, METEOR }

    private static Event active = Event.NONE;
    private static long endTick = 0;
    private static int checkTick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableNightfallWeather) return;
            int nf = NightfallManager.getLevel();
            if (nf < 1) return;
            long now = server.getTicks();

            if (active != Event.NONE && now >= endTick) {
                endEvent(server);
                active = Event.NONE;
            }

            if (++checkTick >= Math.max(20, cfg.weatherCheckIntervalTicks)) {
                checkTick = 0;
                if (active == Event.NONE && server.getOverworld().getRandom().nextDouble() < cfg.weatherTriggerChance) {
                    startRandomEvent(server, nf, now, cfg);
                }
            }

            if (active != Event.NONE) applyEvent(server, now, cfg);
        });
        Yongye.LOGGER.info("[永夜] 永夜天象系统已挂载");
    }

    private static void startRandomEvent(MinecraftServer server, int nf, long now, YongyeConfig cfg) {
        java.util.List<Event> pool = new java.util.ArrayList<>();
        if (nf >= cfg.bloodMoonMinNightfall) pool.add(Event.BLOOD_MOON);
        if (nf >= cfg.acidRainMinNightfall) pool.add(Event.ACID_RAIN);
        if (nf >= cfg.meteorMinNightfall) pool.add(Event.METEOR);
        if (pool.isEmpty()) return;

        active = pool.get(server.getOverworld().getRandom().nextInt(pool.size()));
        endTick = now + cfg.weatherEventDurationTicks;

        String msg;
        switch (active) {
            case BLOOD_MOON -> msg = "血月当空——万物癫狂!";
            case ACID_RAIN -> {
                msg = "酸雨倾盆——露天即受腐蚀!";
                for (ServerWorld w : server.getWorlds()) w.setWeather(0, cfg.weatherEventDurationTicks, true, false);
            }
            case METEOR -> msg = "流星雨降临——天降火劫!";
            default -> { active = Event.NONE; return; }
        }
        server.getPlayerManager().broadcast(Text.literal("【永夜天象】" + msg).formatted(Formatting.DARK_RED), false);
    }

    private static void endEvent(MinecraftServer server) {
        if (active == Event.ACID_RAIN) {
            for (ServerWorld w : server.getWorlds()) w.setWeather(6000, 0, false, false);
        }
        server.getPlayerManager().broadcast(
                Text.literal("【永夜天象】天象平息……暂时。").formatted(Formatting.GRAY), false);
    }

    private static void applyEvent(MinecraftServer server, long now, YongyeConfig cfg) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!(p.getWorld() instanceof ServerWorld w)) continue;
            switch (active) {
                case BLOOD_MOON -> {
                    if (now % 40 == 0) {
                        for (MobEntity m : w.getEntitiesByClass(MobEntity.class,
                                p.getBoundingBox().expand(48), e -> e.isAlive() && e instanceof Monster)) {
                            m.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 1, true, false, false));
                            m.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false, false));
                        }
                    }
                }
                case ACID_RAIN -> {
                    if (now % 20 == 0 && w.isRaining() && w.isSkyVisible(p.getBlockPos().up())) {
                        p.damage(w.getDamageSources().magic(), (float) cfg.acidRainDamage);
                    }
                }
                case METEOR -> {
                    if (now % 15 == 0) {
                        var r = w.getRandom();
                        double ang = r.nextDouble() * Math.PI * 2;
                        double dist = r.nextDouble() * cfg.meteorRadius;
                        double mx = p.getX() + Math.cos(ang) * dist;
                        double mz = p.getZ() + Math.sin(ang) * dist;
                        int my = w.getTopY(Heightmap.Type.WORLD_SURFACE, (int) Math.floor(mx), (int) Math.floor(mz));
                        impact(w, mx, my, mz, cfg);
                    }
                }
                default -> { }
            }
        }
    }

    private static void impact(ServerWorld w, double x, double y, double z, YongyeConfig cfg) {
        w.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 4, 1, 1, 1, 0.0);
        w.spawnParticles(ParticleTypes.FLAME, x, y, z, 30, cfg.meteorImpactRadius, 1, cfg.meteorImpactRadius, 0.05);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.WEATHER, 2.0f, 1.0f);
        Box area = new Box(x - cfg.meteorImpactRadius, y - 2, z - cfg.meteorImpactRadius,
                x + cfg.meteorImpactRadius, y + 3, z + cfg.meteorImpactRadius);
        for (LivingEntity le : w.getEntitiesByClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            le.damage(w.getDamageSources().magic(), (float) cfg.meteorDamage);
        }
    }
}
