package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HIM 突脸惊吓:极低概率在玩家正前方「啪」地出现一个静止、无 AI、无敌、无伤害的人形,
 * 持续约 1~2 秒后凭空消失,伴随瘆人音效。纯吓人,不造成任何伤害。
 * 以 ZombieEntity 为载体 + 自定义名「HIM」走套皮渲染(贴图 textures/entity/him.png)。
 */
public final class HimJumpscareHandler {
    private HimJumpscareHandler() {}

    public static final String NAME = "HIM";

    private static final Map<UUID, Long> ACTIVE = new HashMap<>();
    private static final Map<java.util.UUID, Long> PENDING = new HashMap<>();
    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            long now = server.getTicks();

            // 待生成:失明 5 秒后再让 HIM 出现
            if (!PENDING.isEmpty()) {
                PENDING.entrySet().removeIf(e -> {
                    if (now < e.getValue()) return false;
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                    if (p != null && p.getWorld() instanceof ServerWorld w) spawnHim(w, p, cfg, now);
                    return true;
                });
            }

            if (!ACTIVE.isEmpty()) {
                ACTIVE.entrySet().removeIf(e -> {
                    if (now < e.getValue()) return false;
                    for (ServerWorld w : server.getWorlds()) {
                        var ent = w.getEntity(e.getKey());
                        if (ent != null) {
                            w.spawnParticles(ParticleTypes.SMOKE, ent.getX(), ent.getBodyY(0.5), ent.getZ(),
                                    20, 0.3, 0.6, 0.3, 0.02);
                            ent.discard();
                        }
                    }
                    return true;
                });
            }

            if (!cfg.enableHim) return;
            if (++tick < cfg.himCheckIntervalTicks) return;
            tick = 0;

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isCreative() || p.isSpectator()) continue;
                if (!(p.getWorld() instanceof ServerWorld world)) continue;
                if (cfg.himNightOrCaveOnly && !isNightOrDark(world, p)) continue;
                if (world.getRandom().nextDouble() >= cfg.himChance) continue;
                // 出现前短暂失明铺垫(时长可配,越短越突然)
                int blind = Math.max(1, cfg.himBlindnessTicks);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, blind, 0, false, false, true));
                PENDING.put(p.getUuid(), now + blind);
            }
        });
        Yongye.LOGGER.info("[永夜] HIM 惊吓系统已挂载");
    }

    private static boolean isNightOrDark(ServerWorld world, ServerPlayerEntity p) {
        long t = world.getTimeOfDay() % 24000L;
        boolean night = t >= 13000 && t <= 23000;
        boolean dark = world.getLightLevel(LightType.SKY, p.getBlockPos()) == 0;
        return night || dark;
    }

    private static void spawnHim(ServerWorld world, ServerPlayerEntity player, YongyeConfig cfg, long now) {
        float yaw = player.getYaw();
        double rad = Math.toRadians(yaw);
        double sx = player.getX() - Math.sin(rad) * cfg.himSpawnDistance;
        double sy = player.getY();
        double sz = player.getZ() + Math.cos(rad) * cfg.himSpawnDistance;

        BlockPos feet = BlockPos.ofFloored(sx, sy, sz);
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                || !world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()) {
            return;
        }

        ZombieEntity him = new ZombieEntity(EntityType.ZOMBIE, world);
        him.setAttached(ModAttachments.IS_HIM, true);
        float fy = faceYaw(sx, sz, player);
        him.refreshPositionAndAngles(sx, sy, sz, fy, 0);
        him.setCustomName(Text.literal(NAME));
        him.setCustomNameVisible(false);
        him.setAiDisabled(true);
        him.setSilent(true);
        him.setInvulnerable(true);
        him.setPersistent();
        him.setNoGravity(true);
        him.setHealth(him.getMaxHealth());

        world.spawnEntity(him);
        him.setYaw(fy);
        him.setHeadYaw(fy);
        him.setBodyYaw(fy);

        // 突脸音效(自定义 OGG)+ 传送闪现粒子(紫色末影门 + 烟),营造"啪地闪到面前"
        player.playSoundToPlayer(ModSounds.HIM_JUMPSCARE, SoundCategory.HOSTILE, 1.0f, 1.0f);
        if (cfg.himTeleportFlash) {
            world.spawnParticles(ParticleTypes.PORTAL, sx, sy + 1.0, sz, 50, 0.4, 0.9, 0.4, 0.4);
        }
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, sx, sy + 1.0, sz, 15, 0.2, 0.4, 0.2, 0.01);

        ACTIVE.put(him.getUuid(), now + cfg.himDurationTicks);
    }

    private static float faceYaw(double x, double z, ServerPlayerEntity player) {
        double ddx = player.getX() - x;
        double ddz = player.getZ() - z;
        return (float) (MathHelper.atan2(ddz, ddx) * (180.0 / Math.PI)) - 90.0f;
    }
}
