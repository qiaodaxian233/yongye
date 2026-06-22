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

    /** 在途流星(下落动画用):每 tick 推进位置 + 喷火尾,落地触发 impact。 */
    private static final java.util.List<Meteor> meteors = new java.util.ArrayList<>();

    /** 一颗下落中的流星:从高空斜线坠向落点,带火焰拖尾。 */
    private static final class Meteor {
        final ServerWorld world;
        double x, y, z;              // 当前头部位置
        final double vx, vy, vz;     // 每 tick 位移
        final double tx, ty, tz;     // 落点(到达即爆炸)
        int ticksLeft;
        Meteor(ServerWorld w, double sx, double sy, double sz,
               double tx, double ty, double tz, int ticks) {
            this.world = w; this.x = sx; this.y = sy; this.z = sz;
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.ticksLeft = ticks;
            this.vx = (tx - sx) / ticks; this.vy = (ty - sy) / ticks; this.vz = (tz - sz) / ticks;
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickMeteors(server); // 始终推进在途流星(保证落地/清理,不受事件开关或永夜等级变化影响)
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
                        spawnMeteor(w, mx, my, mz, cfg); // 在落点上方高空生成流星,斜线坠落后砸地
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

    /** 每 tick 推进所有在途流星:移动 + 火尾;落地则爆炸并移除。始终调用,保证清理不残留。 */
    private static void tickMeteors(MinecraftServer server) {
        if (meteors.isEmpty()) return;
        YongyeConfig cfg = YongyeConfig.get();
        java.util.Iterator<Meteor> it = meteors.iterator();
        while (it.hasNext()) {
            Meteor m = it.next();
            m.x += m.vx; m.y += m.vy; m.z += m.vz;
            m.ticksLeft--;
            // 火尾:头部喷火焰 + 少量熔岩火星 + 烟,形成一道下坠的燃烧轨迹
            m.world.spawnParticles(ParticleTypes.FLAME, m.x, m.y, m.z, 10, 0.25, 0.25, 0.25, 0.01);
            m.world.spawnParticles(ParticleTypes.LAVA, m.x, m.y, m.z, 1, 0.1, 0.1, 0.1, 0.0);
            m.world.spawnParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 2, 0.15, 0.15, 0.15, 0.0);
            if (m.ticksLeft <= 0) {
                impact(m.world, m.tx, m.ty, m.tz, cfg); // 落地:沿用现有爆炸 + 范围伤害
                it.remove();
            }
        }
    }

    /** 在落点上方高空生成一颗流星(斜线坠落),约 1~1.6s 后砸地。 */
    private static void spawnMeteor(ServerWorld w, double tx, double ty, double tz, YongyeConfig cfg) {
        if (meteors.size() > 64) return; // 上限护栏,防极端情况堆积
        var r = w.getRandom();
        double height = 45 + r.nextDouble() * 15;  // 起点高度 45~60 格
        double offX = (r.nextDouble() - 0.5) * 24; // 水平偏移 → 斜着落下
        double offZ = (r.nextDouble() - 0.5) * 24;
        int ticks = 20 + r.nextInt(12);            // 1~1.6s 下落
        meteors.add(new Meteor(w, tx + offX, ty + height, tz + offZ, tx, ty, tz, ticks));
    }
}
