package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;

/**
 * 硬核开局生存包(设计前 8 条「世界生存难度」):全部配置可开关。
 *  - 睡觉不能跳过夜晚:玩家睡眠百分比设为 101(永不跳夜)。
 *  - 食物紧张:持续额外饥饿消耗。
 *  - 火把不能保证安全:夜晚在玩家附近无视亮度强制刷怪。
 *  - 洞穴极度危险:地下额外刷怪 + 偶发失明/挖掘疲劳。
 *  - 木头/石头/矿物难获取:开采时概率施加挖掘疲劳 + 额外饥饿。
 */
public final class HardcoreSurvivalHandler {
    private HardcoreSurvivalHandler() {}

    private static final EntityType<?>[] AMBUSH_POOL = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER
    };

    private static int tick = 0;

    public static void register() {
        // 睡觉不能跳过夜晚:服务器启动时把 playersSleepingPercentage 设为 101
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (cfg.enableHardcoreSurvival && cfg.hcNoSleepSkip) {
                server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE).set(101, server);
                Yongye.LOGGER.info("[亡途荒夜] 硬核:睡觉无法跳过夜晚");
            }
        });

        // 周期性:食物消耗 + 洞穴危险
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableHardcoreSurvival) return;
            tick++;

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!(p.getWorld() instanceof ServerWorld world)) continue;
                if (p.isCreative() || p.isSpectator()) continue;

                if (tick % 20 == 0 && cfg.hcHungerDrainPerSecond > 0) {
                    p.getHungerManager().addExhaustion((float) cfg.hcHungerDrainPerSecond);
                }

                if (cfg.hcCaveDanger && tick % 100 == 0) {
                    BlockPos pos = p.getBlockPos();
                    boolean underground = p.getY() < cfg.hcCaveYThreshold
                            && world.getLightLevel(LightType.SKY, pos) == 0;
                    if (underground) {
                        if (world.getRandom().nextDouble() < cfg.hcCaveSpawnChance) {
                            ambushSpawn(world, p, cfg, false);
                        }
                        if (world.getRandom().nextDouble() < cfg.hcCaveDebuffChance) {
                            p.addStatusEffect(new StatusEffectInstance(
                                    world.getRandom().nextBoolean() ? StatusEffects.BLINDNESS : StatusEffects.MINING_FATIGUE,
                                    120, 0, false, true, true));
                        }
                    }
                }
            }

            // 夜晚伏击(火把不保证安全)
            if (cfg.hcNightAmbush && cfg.hcAmbushIntervalTicks > 0 && tick % cfg.hcAmbushIntervalTicks == 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if (!(p.getWorld() instanceof ServerWorld world)) continue;
                    if (p.isCreative() || p.isSpectator()) continue;
                    long timeOfDay = world.getTimeOfDay() % 24000L;
                    boolean night = timeOfDay >= 13000 && timeOfDay <= 23000;
                    boolean nightfall = NightfallManager.getLevel() >= 1;
                    if ((night || nightfall) && world.getRandom().nextDouble() < cfg.hcAmbushChance) {
                        // 玩家在地下时用其所在高度刷怪,否则用地表
                        boolean ug = p.getY() < cfg.hcCaveYThreshold
                                && world.getLightLevel(LightType.SKY, p.getBlockPos()) == 0;
                        ambushSpawn(world, p, cfg, !ug);
                    }
                }
            }
        });

        // 木头/石头/矿物难获取:开采时概率施加挖掘疲劳 + 额外饥饿
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableHardcoreSurvival || !cfg.hcResourceHarder) return;
            if (sp.isCreative()) return;
            boolean hard = state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.BASE_STONE_OVERWORLD)
                    || state.isIn(BlockTags.COAL_ORES) || state.isIn(BlockTags.IRON_ORES);
            if (!hard) return;
            // 挖掘减速由 MiningSpeedMixin 处理;这里仅按概率额外扣体力(挖矿很累)
            if (sp.getRandom().nextDouble() < cfg.hcResourceFatigueChance) {
                sp.getHungerManager().addExhaustion(2.0f);
            }
        });

        Yongye.LOGGER.info("[亡途荒夜] 硬核开局生存包已挂载");
    }

    /** 在玩家附近刷一只敌对怪(无视亮度);surface=true 用地表高度,否则用玩家所在高度。 */
    private static void ambushSpawn(ServerWorld world, ServerPlayerEntity player, YongyeConfig cfg, boolean surface) {
        long nearby = world.getEntitiesByClass(HostileEntity.class,
                player.getBoundingBox().expand(cfg.hcAmbushRadius + 4), e -> e.isAlive()).size();
        if (nearby >= cfg.hcAmbushMaxNearby) return;

        var r = world.getRandom();
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = 6 + r.nextDouble() * cfg.hcAmbushRadius;
        int x = player.getBlockX() + (int) (Math.cos(ang) * dist);
        int z = player.getBlockZ() + (int) (Math.sin(ang) * dist);
        int y = surface ? world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) : player.getBlockY();
        EntityType<?> type = AMBUSH_POOL[r.nextInt(AMBUSH_POOL.length)];
        type.spawn(world, new BlockPos(x, y, z), SpawnReason.EVENT);
    }
}
