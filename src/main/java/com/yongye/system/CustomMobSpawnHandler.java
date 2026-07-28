package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.entity.DeathMageEntity;
import com.yongye.entity.FirePhoenixEntity;
import com.yongye.entity.GiantCrabEntity;
import com.yongye.entity.RedSpiderEntity;
import com.yongye.entity.VenomSpiderEntity;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * 五只新怪自然刷怪(m176,收 m167/m169/m170 的「刷怪接入」遗留):
 * <ul>
 *   <li><b>BOSS 线</b>(稀有全服事件,照 m175 阿努比斯模板:天数门槛 + 全服存活上限 + 概率检定 + 播报):
 *       红蜘蛛(地表)/ 死亡法师(地表)/ 浴火凤凰(高空,飞行 BOSS)。</li>
 *   <li><b>精英线</b>(逐玩家就近压力刷怪,带附近同类上限 + m153 全局敌对预算闸):
 *       毒液蜘蛛 / 巨型螃蟹;出生打 IS_ELITE 标记 → 吃 LootHandler 精英掉落档 +
 *       BonusXpHandler 精英经验档(EliteHandler 的光环/技能/词缀因 m175 命名空间豁免不会挂上)。</li>
 * </ul>
 * 所有天数/概率/上限均可配;阿努比斯仍走独立的 AnubisSpawnHandler(m175),不在此处。
 */
public final class CustomMobSpawnHandler {
    private CustomMobSpawnHandler() {}

    private static int tick = 0;

    /** 实体工厂(沙箱规避 EntityType.create 跨版本签名差异,直接走各实体 proven 构造器)。 */
    private interface Factory {
        MobEntity create(ServerWorld world);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableCustomMobSpawns) return;
            if (++tick < Math.max(1, cfg.customMobCheckIntervalTicks)) return;
            tick = 0;

            // ===== BOSS 线:三只稀有 BOSS,各自独立检定 =====
            rollBoss(server, cfg.redSpiderMinDay, cfg.redSpiderSpawnChance, cfg.redSpiderMaxAlive,
                    RedSpiderEntity.class, false,
                    "巢穴震颤——猩红巨蛛破土而出!", Formatting.DARK_RED,
                    w -> new RedSpiderEntity(ModEntities.RED_SPIDER, w));
            rollBoss(server, cfg.deathMageMinDay, cfg.deathMageSpawnChance, cfg.deathMageMaxAlive,
                    DeathMageEntity.class, false,
                    "亡者低语回荡——死亡法师踏入人间!", Formatting.DARK_PURPLE,
                    w -> new DeathMageEntity(ModEntities.DEATH_MAGE, w));
            rollBoss(server, cfg.phoenixMinDay, cfg.phoenixSpawnChance, cfg.phoenixMaxAlive,
                    FirePhoenixEntity.class, true,
                    "烈焰撕裂长空——浴火凤凰降临!", Formatting.GOLD,
                    w -> new FirePhoenixEntity(ModEntities.FIRE_PHOENIX, w));

            // ===== 精英线:两只精英,逐玩家就近检定 =====
            rollElite(server, cfg, cfg.venomSpiderMinDay, cfg.venomSpiderSpawnChance, cfg.venomSpiderMaxNearby,
                    VenomSpiderEntity.class, w -> new VenomSpiderEntity(ModEntities.VENOM_SPIDER, w));
            rollElite(server, cfg, cfg.giantCrabMinDay, cfg.giantCrabSpawnChance, cfg.giantCrabMaxNearby,
                    GiantCrabEntity.class, w -> new GiantCrabEntity(ModEntities.GIANT_CRAB, w));
        });
        Yongye.LOGGER.info("[夜蚀] 新怪自然刷怪系统已挂载(红蛛/法师/凤凰=BOSS事件,毒蛛/螃蟹=精英压力)");
    }

    /**
     * 稀有 BOSS 事件(照 m175 AnubisSpawnHandler):全服存活 ≥maxAlive 跳过;
     * 收集已达天数门槛的生存/冒险玩家,全服一次概率检定;
     * 中则在随机玩家附近 12~24 格刷出(sky=true 刷玩家上方高空,供飞行 BOSS)+ 出生锁定 + 播报。
     */
    private static void rollBoss(MinecraftServer server, int minDay, double chance, int maxAlive,
                                 Class<? extends MobEntity> clazz, boolean sky,
                                 String message, Formatting color, Factory factory) {
        // 全服存活计数(复用 m155 proven 的 getWorlds()/iterateEntities())
        int alive = 0;
        for (ServerWorld w : server.getWorlds()) {
            for (Entity e : w.iterateEntities()) {
                if (clazz.isInstance(e) && e instanceof MobEntity m && m.isAlive()) alive++;
            }
        }
        if (alive >= Math.max(1, maxAlive)) return;

        List<ServerPlayerEntity> eligible = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            GameMode gm = player.interactionManager.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) continue;
            if (!(player.getWorld() instanceof ServerWorld world)) continue;
            if (ProgressionManager.gameDay(world) < minDay) continue;
            eligible.add(player);
        }
        if (eligible.isEmpty()) return;
        if (server.getOverworld().getRandom().nextDouble() >= chance) return;

        ServerPlayerEntity target = eligible.get(
                server.getOverworld().getRandom().nextInt(eligible.size()));
        if (!(target.getWorld() instanceof ServerWorld world)) return;

        var r = world.getRandom();
        double dist = 12.0 + r.nextDouble() * 12.0;
        double angle = r.nextDouble() * Math.PI * 2.0;
        int sx = (int) Math.round(target.getX() + Math.cos(angle) * dist);
        int sz = (int) Math.round(target.getZ() + Math.sin(angle) * dist);
        double sy = sky
                ? Math.min(target.getY() + 24.0, world.getTopY() - 4)          // 飞行 BOSS:玩家上方高空
                : world.getTopY(Heightmap.Type.WORLD_SURFACE, sx, sz);         // 地面 BOSS:地表落点

        MobEntity mob = factory.create(world);
        mob.refreshPositionAndAngles(sx + 0.5, sy, sz + 0.5, r.nextFloat() * 360f, 0f);
        world.spawnEntity(mob);
        mob.setTarget(target); // 出生即锁定该玩家

        world.getServer().getPlayerManager().broadcast(
                Text.literal(message).formatted(color, Formatting.BOLD), false);
    }

    /**
     * 精英压力刷怪:逐玩家独立概率检定;附近 48 格同类 ≥maxNearby 跳过;
     * 再过 m153 全局敌对预算闸(globalMaxHostilesNearby/globalHostileRadius,防实体爆炸);
     * 落点=玩家附近 14~28 格地表;出生打 IS_ELITE(只吃掉落/经验档,光环词缀已被命名空间豁免挡住)。
     */
    private static void rollElite(MinecraftServer server, YongyeConfig cfg,
                                  int minDay, double chance, int maxNearby,
                                  Class<? extends MobEntity> clazz, Factory factory) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            GameMode gm = player.interactionManager.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) continue;
            if (!(player.getWorld() instanceof ServerWorld world)) continue;
            if (ProgressionManager.gameDay(world) < minDay) continue;
            if (world.getRandom().nextDouble() >= chance) continue;

            // 附近同类上限(48 格),防同种精英扎堆
            Box near = player.getBoundingBox().expand(48.0);
            int nearby = world.getEntitiesByClass(MobEntity.class, near,
                    m -> m.isAlive() && clazz.isInstance(m)).size();
            if (nearby >= Math.max(1, maxNearby)) continue;

            // 全局敌对预算(m153 尸潮同款硬闸)
            Box gbox = player.getBoundingBox().expand(cfg.globalHostileRadius);
            int globalHostiles = world.getEntitiesByClass(MobEntity.class, gbox,
                    m -> m.isAlive() && m instanceof Monster).size();
            if (globalHostiles >= cfg.globalMaxHostilesNearby) continue;
            if (LagGuard.spawnFactor(world.getServer()) <= 0.0) continue;   // m335 卡顿硬闸:先让服务器喘气

            var r = world.getRandom();
            double dist = 14.0 + r.nextDouble() * 14.0;
            double angle = r.nextDouble() * Math.PI * 2.0;
            int sx = (int) Math.round(player.getX() + Math.cos(angle) * dist);
            int sz = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
            int sy = world.getTopY(Heightmap.Type.WORLD_SURFACE, sx, sz);

            MobEntity mob = factory.create(world);
            mob.refreshPositionAndAngles(sx + 0.5, sy, sz + 0.5, r.nextFloat() * 360f, 0f);
            // 精英标记:LootHandler 精英掉落档 + BonusXpHandler 精英经验档。
            // EliteHandler 的 ELITES 追踪(光环/技能/词缀)因 m175 命名空间豁免在 IS_ELITE 恢复分支之前,不会挂上。
            mob.setAttached(ModAttachments.IS_ELITE, true);
            world.spawnEntity(mob);
        }
    }
}
