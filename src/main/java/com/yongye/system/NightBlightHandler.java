package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;

/**
 * 夜蚀群系(m212):被永夜彻底吞噬的土地。三件事:
 * <p>① <b>侵蚀转化</b>:把已加载区块的群系数据改写成 {@code yongye:nightblight}
 * (群系本体是数据驱动 JSON,模组 data 目录即数据包,动态注册表自动加载)。
 * 改写不自己碰 chunk 内部结构,而是<b>逐区块、分 Y 段执行原版 {@code /fillbiome} 命令</b>:
 * 原版命令自带「写入 chunk + 标脏存盘 + ChunkBiomeData 包同步客户端」全套,
 * 分 4 段(每段 16×16×96=24576 方块)是为了压进 commandModificationBlockLimit
 * 默认上限 32768,<b>不动 gamerule</b>。改写对已生成地形立即生效(作者老档可直接测),天空/雾/水色随之变暗紫。
 * <p>② <b>全生物敌化</b>:玩家身处夜蚀群系时,周围一切生物都会攻击他——
 * 敌对/中立怪直接 setTarget(自带攻击 AI 接管);牛羊鸡猪村民这类被动生物<b>没有攻击 AI
 * 也没有攻击力属性</b>(挂 MeleeAttackGoal 会因缺 GENERIC_ATTACK_DAMAGE 崩溃),
 * 所以走本处理器自实现的「追击+贴脸啃咬」:导航追人,贴身 2 格内按扫描节奏(1 次/秒)咬一口。
 * 已驯服的宠物不背叛主人(TameableEntity.isTamed 豁免)。
 * <p>③ <b>侵蚀掉落</b>:在夜蚀群系内死亡的生物额外掉本模组材料——全员概率掉永夜之尘,
 * 被动生物额外概率掉生命碎片(血肉被夜蚀浸染),怪物额外概率掉裂隙碎片,极小概率深渊魂晶。
 * <p>④ <b>自然侵蚀</b>:第 blightStartDay 天起周期检定,小概率在随机玩家附近悄然出现一片侵蚀区
 * (模板=AnubisSpawnHandler 的天数门槛+概率检定+播报)。测试命令 /yongye blight [半径]。
 * <p>遗留 Stage2:侵蚀区随时间向外蔓延(需持久化圆心;当前群系数据本身随 chunk 存盘,但圆心不存)。
 */
public final class NightBlightHandler {
    private NightBlightHandler() {}

    public static final Identifier BIOME_ID = Identifier.of(Yongye.MOD_ID, "nightblight");
    public static final RegistryKey<Biome> BIOME_KEY = RegistryKey.of(RegistryKeys.BIOME, BIOME_ID);

    private static int aggroTick = 0;
    private static int seedTick = 0;
    private static int oreTick = 0;   // m264:蚀矿生长节拍

    public static void register() {
        // —— ② 全生物敌化(每 20 tick 扫一轮;扫描节奏本身就是被动生物的攻击冷却) ——
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableBlight) return;
            if (++aggroTick >= 20) {
                aggroTick = 0;
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    GameMode gm = player.interactionManager.getGameMode();
                    if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) continue;
                    if (!(player.getWorld() instanceof ServerWorld world)) continue;
                    if (!world.getBiome(player.getBlockPos()).matchesKey(BIOME_KEY)) continue;

                    double r = Math.max(4, cfg.blightAggroRange);
                    for (MobEntity mob : world.getEntitiesByClass(MobEntity.class,
                            player.getBoundingBox().expand(r), m -> m.isAlive() && !m.isRemoved())) {
                        // 已驯服的宠物不背叛主人
                        if (mob instanceof TameableEntity t && t.isTamed()) continue;

                        if (mob instanceof PassiveEntity) {
                            // 被动生物(动物/村民):无攻击 AI/属性,自实现追击+啃咬
                            mob.getNavigation().startMovingTo(player, cfg.blightPassiveSpeed);
                            mob.getLookControl().lookAt(player, 30.0f, 30.0f);
                            if (mob.squaredDistanceTo(player) < 4.0) {
                                player.damage(world.getDamageSources().mobAttack(mob),
                                        (float) cfg.blightPassiveDamage);
                            }
                        } else if (mob.getTarget() == null) {
                            // 敌对/中立怪:锁定目标即由它们自带的攻击 AI 接管
                            mob.setTarget(player);
                        }
                    }
                }
            }

            // —— m264:蚀矿在侵蚀区内缓慢生长(老侵蚀区/已播种区都能继续长新矿) ——
            if (++oreTick >= Math.max(200, cfg.blightOreGrowIntervalTicks)) {
                oreTick = 0;
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    if (!(player.getWorld() instanceof ServerWorld world)) continue;
                    if (!world.getBiome(player.getBlockPos()).matchesKey(BIOME_KEY)) continue;
                    if (world.getRandom().nextDouble() >= cfg.blightOreGrowChance) continue;
                    for (int attempt = 0; attempt < 10; attempt++) {
                        BlockPos pos = player.getBlockPos().add(
                                world.getRandom().nextInt(25) - 12,
                                world.getRandom().nextInt(17) - 12,
                                world.getRandom().nextInt(25) - 12);
                        if (convertToOre(world, pos)) break;   // 每人每轮最多长 1 块
                    }
                }
            }

            // —— ④ 自然侵蚀检定 ——
            if (++seedTick < Math.max(200, cfg.blightSeedCheckIntervalTicks)) return;
            seedTick = 0;
            List<ServerPlayerEntity> eligible = new ArrayList<>();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                GameMode gm = player.interactionManager.getGameMode();
                if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) continue;
                if (!(player.getWorld() instanceof ServerWorld world)) continue;
                if (ProgressionManager.gameDay(world) < cfg.blightStartDay) continue;
                eligible.add(player);
            }
            if (eligible.isEmpty()) return;
            Random rnd = server.getOverworld().getRandom();
            if (rnd.nextDouble() >= cfg.blightSeedChance) return;

            ServerPlayerEntity victim = eligible.get(rnd.nextInt(eligible.size()));
            ServerWorld world = (ServerWorld) victim.getWorld();
            // 落点:玩家附近 48~96 格随机方位(离得太近立刻被围殴,太远侵蚀区区块没加载填不进)
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double dist = 48 + rnd.nextDouble() * 48;
            BlockPos center = victim.getBlockPos().add(
                    (int) (Math.cos(ang) * dist), 0, (int) (Math.sin(ang) * dist));
            int chunks = blightArea(world, center, cfg.blightZoneRadius);
            if (chunks > 0) {
                server.getPlayerManager().broadcast(Text.literal(
                                "【夜蚀】黑暗渗入了大地……一片土地被夜蚀吞噬了(" +
                                        center.getX() + ", " + center.getZ() + " 附近)")
                        .formatted(Formatting.DARK_PURPLE), false);
            }
        });

        // —— ③ 侵蚀掉落:在夜蚀群系内死亡的生物掉本模组材料 ——
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableBlight) return;
            if (!(entity instanceof MobEntity mob)) return;
            if (!(entity.getWorld() instanceof ServerWorld world)) return;
            if (!world.getBiome(entity.getBlockPos()).matchesKey(BIOME_KEY)) return;
            if (cfg.blightDropRequirePlayerKill && SummonKillCredit.creditedKiller(source) == null) return; // m300

            Random r = mob.getRandom();
            if (r.nextDouble() < cfg.blightDustChance) {
                mob.dropStack(new ItemStack(ModItems.ENDLESS_NIGHT_DUST,
                        1 + r.nextInt(Math.max(1, cfg.blightDustMax))));
            }
            if (mob instanceof PassiveEntity && r.nextDouble() < cfg.blightShardChance) {
                mob.dropStack(new ItemStack(ModItems.LIFE_SHARD, 1));
            }
            if (!(mob instanceof PassiveEntity) && r.nextDouble() < cfg.blightRiftChance) {
                mob.dropStack(new ItemStack(ModItems.RIFT_FRAGMENT, 1));
            }
            if (r.nextDouble() < cfg.blightCrystalChance) {
                mob.dropStack(new ItemStack(ModItems.ABYSS_SOUL_CRYSTAL, 1));
            }
        });

        Yongye.LOGGER.info("[夜蚀] 夜蚀群系已注册(侵蚀转化 + 全生物敌化 + 侵蚀掉落 + 自然侵蚀)");
    }

    /**
     * 把 center 为中心、半径 radius 的方形范围内<b>已加载</b>区块整柱转为夜蚀群系。
     * 逐区块、分 4 个 Y 段执行原版 /fillbiome(每段 16×16×96=24576 &lt; 上限 32768,不碰 gamerule);
     * 原版命令自带存盘 + 客户端同步。返回成功处理的区块数。
     */
    public static int blightArea(ServerWorld world, BlockPos center, int radius) {
        MinecraftServer server = world.getServer();
        int bottom = world.getBottomY();
        int top = world.getTopY() - 1; // getTopY 是开区间上界
        int cx0 = (center.getX() - radius) >> 4, cx1 = (center.getX() + radius) >> 4;
        int cz0 = (center.getZ() - radius) >> 4, cz1 = (center.getZ() + radius) >> 4;
        int converted = 0;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue; // 未加载的跳过,fillbiome 会报错
                int x0 = cx << 4, z0 = cz << 4;
                for (int y = bottom; y <= top; y += 96) {
                    int y1 = Math.min(y + 95, top);
                    String cmd = String.format("fillbiome %d %d %d %d %d %d %s",
                            x0, y, z0, x0 + 15, y1, z0 + 15, BIOME_ID);
                    server.getCommandManager().executeWithPrefix(
                            server.getCommandSource().withWorld(world).withSilent(), cmd);
                }
                converted++;
                seedOre(world, cx, cz);   // m264:新侵蚀区播种蚀矿脉
            }
        }
        return converted;
    }

    /** m264:在刚被侵蚀的区块里播种蚀矿脉——地下随机取点,把石头族原地转化成蚀矿(空气/矿洞位置静默跳过重试)。 */
    private static void seedOre(ServerWorld world, int chunkX, int chunkZ) {
        YongyeConfig cfg = YongyeConfig.get();
        if (cfg.blightOreVeinsPerChunk <= 0) return;
        Random rnd = world.getRandom();
        int bottom = world.getBottomY();
        for (int v = 0; v < cfg.blightOreVeinsPerChunk; v++) {
            for (int attempt = 0; attempt < 8; attempt++) {
                int x = (chunkX << 4) + rnd.nextInt(16);
                int z = (chunkZ << 4) + rnd.nextInt(16);
                int surface = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
                int span = Math.max(1, surface - 6 - (bottom + 8));
                int y = bottom + 8 + rnd.nextInt(span);
                BlockPos pos = new BlockPos(x, y, z);
                if (!convertToOre(world, pos)) continue;   // 落到空气/水/矿物上就换个点重试
                int size = Math.max(1, cfg.blightOreVeinSize);
                for (int i = 1; i < size; i++) {
                    convertToOre(world, pos.add(rnd.nextInt(3) - 1, rnd.nextInt(3) - 1, rnd.nextInt(3) - 1));
                }
                break;
            }
        }
    }

    /** 石头族(石/深板岩/花岗闪长安山/凝灰岩)→ 蚀矿;其余方块一律不动,返回是否转化成功。 */
    private static boolean convertToOre(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.isOf(Blocks.STONE) || state.isOf(Blocks.DEEPSLATE) || state.isOf(Blocks.GRANITE)
                || state.isOf(Blocks.DIORITE) || state.isOf(Blocks.ANDESITE) || state.isOf(Blocks.TUFF))) return false;
        return world.setBlockState(pos, com.yongye.registry.ModBlocks.BLIGHT_ORE.getDefaultState());
    }
}
