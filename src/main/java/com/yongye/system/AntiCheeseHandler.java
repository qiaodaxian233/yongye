package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 反苟机制(m114)——破解三种龟缩流:
 *  ① 泡水苟:玩家在水里累计超 antiCheeseWaterSeconds → 召唤守护者(Guardian)追杀 + 持续扣血。
 *  ② 虚空/搭方块苟:玩家长时间悬空(脚下方圆无地面,靠自搭方块龟缩)超 antiCheeseAirborneSeconds → 召幻翼(Phantom)空袭。
 *  ③ 远程龟缩通用:进入上述苟态超宽限期 → 持续扣血(固定点 + 按最大生命比例,应对高血量苟),逼玩家离开苟点正面作战。
 *
 * 全部用已确认 API:isTouchingWater / isOnGround + 脚下方块扫描 / spawnEntity / damage / setHealth。
 * 每 20 tick(1秒)检测一次,低频不卡。
 */
public final class AntiCheeseHandler {
    private AntiCheeseHandler() {}

    /** 各玩家泡水持续秒数 */
    private static final Map<UUID, Integer> waterSec = new HashMap<>();
    /** 各玩家悬空持续秒数 */
    private static final Map<UUID, Integer> airSec = new HashMap<>();
    /** 已对该玩家召过守护者(避免每秒刷) */
    private static final Map<UUID, Long> lastGuardian = new HashMap<>();
    private static final Map<UUID, Long> lastPhantom = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableAntiCheese) return;
            if (server.getTicks() % 20 != 0) return; // 每秒一次

            long now = server.getTicks();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isCreative() || p.isSpectator()) continue;
                if (!(p.getWorld() instanceof ServerWorld sw)) continue;
                UUID id = p.getUuid();

                boolean inWater = p.isTouchingWater() || p.isSubmergedInWater();
                boolean airborne = yongye$isAirborneCheese(p, sw);

                // ① 泡水苟
                if (inWater) {
                    int sec = waterSec.merge(id, 1, Integer::sum);
                    if (sec >= cfg.antiCheeseWaterSeconds) {
                        if (now - lastGuardian.getOrDefault(id, -99999L) > 200) { // 10s 一只
                            yongye$summonGuardian(sw, p);
                            lastGuardian.put(id, now);
                            p.sendMessage(Text.literal("深渊不容苟且——守护者已盯上你!")
                                    .formatted(Formatting.AQUA), true);
                        }
                        yongye$drain(p, cfg, sec - cfg.antiCheeseWaterSeconds, cfg.antiCheeseGraceSeconds);
                    }
                } else {
                    waterSec.remove(id);
                }

                // ② 虚空/搭方块苟
                if (airborne) {
                    int sec = airSec.merge(id, 1, Integer::sum);
                    if (sec >= cfg.antiCheeseAirborneSeconds) {
                        if (now - lastPhantom.getOrDefault(id, -99999L) > 200) {
                            yongye$summonPhantoms(sw, p);
                            lastPhantom.put(id, now);
                            p.sendMessage(Text.literal("高处亦无藏身之地——空袭降临!")
                                    .formatted(Formatting.DARK_PURPLE), true);
                        }
                        yongye$drain(p, cfg, sec - cfg.antiCheeseAirborneSeconds, cfg.antiCheeseGraceSeconds);
                    }
                } else {
                    airSec.remove(id);
                }
            }
        });
        Yongye.LOGGER.info("[永夜] 反苟机制已挂载(泡水/悬空/龟缩)");
    }

    /** 判定"搭方块/虚空龟缩":站在方块上(非地面连续地形),且脚下一圈大多悬空。 */
    private static boolean yongye$isAirborneCheese(ServerPlayerEntity p, ServerWorld sw) {
        if (!p.isOnGround()) return false;            // 自由下落不算(正在移动)
        if (p.isTouchingWater()) return false;
        BlockPos foot = p.getBlockPos().down();
        // 脚下必须有支撑方块(站着),但周围 3x3 下方多数悬空 = 孤立柱/平台龟缩
        if (sw.getBlockState(foot).isAir()) return false;
        int airAround = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                // 检测该列下方 4 格是否都空(悬空柱特征)
                boolean allAir = true;
                for (int dy = 0; dy < 4; dy++) {
                    if (!sw.getBlockState(foot.add(dx, -dy, dz)).isAir()) { allAir = false; break; }
                }
                if (allAir) airAround++;
            }
        }
        return airAround >= 6; // 周围 8 格里 ≥6 格下方悬空 = 孤立平台
    }

    /** 龟缩持续扣血:超过宽限期后,固定点+最大生命比例(应对高血量)。 */
    private static void yongye$drain(ServerPlayerEntity p, YongyeConfig cfg, int secOver, int grace) {
        if (secOver < grace) return;
        float flat = (float) cfg.antiCheeseDrainPerSecond;
        float byMax = (float) (p.getMaxHealth() * cfg.antiCheeseDrainMaxHpFraction);
        float dmg = flat + byMax;
        // 直接削血(绕过护甲,真伤逼出),但不致死下限留 1
        float nh = Math.max(1.0f, p.getHealth() - dmg);
        p.setHealth(nh);
    }

    private static void yongye$summonGuardian(ServerWorld sw, ServerPlayerEntity p) {
        for (int i = 0; i < 2; i++) {
            GuardianEntity g = new GuardianEntity(EntityType.GUARDIAN, sw);
            Vec3d pos = p.getPos().add((sw.random.nextDouble() - 0.5) * 6, 1, (sw.random.nextDouble() - 0.5) * 6);
            g.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
            g.setTarget(p);
            sw.spawnEntity(g);
        }
        sw.spawnParticles(ParticleTypes.BUBBLE_POP, p.getX(), p.getY() + 1, p.getZ(), 30, 1, 1, 1, 0.1);
        sw.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 1f, 0.6f);
    }

    private static void yongye$summonPhantoms(ServerWorld sw, ServerPlayerEntity p) {
        for (int i = 0; i < 3; i++) {
            PhantomEntity ph = new PhantomEntity(EntityType.PHANTOM, sw);
            Vec3d pos = p.getPos().add((sw.random.nextDouble() - 0.5) * 8, 6, (sw.random.nextDouble() - 0.5) * 8);
            ph.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
            ph.setTarget(p);
            sw.spawnEntity(ph);
        }
        sw.spawnParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 2, p.getZ(), 30, 1, 1, 1, 0.05);
        sw.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PHANTOM_SWOOP, SoundCategory.HOSTILE, 1f, 0.7f);
    }
}
