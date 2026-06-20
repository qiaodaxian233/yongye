package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import com.yongye.registry.ModBlocks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 随机任务系统(文档第 9 章)。限时、目标明确、失败提升永夜。
 * Phase 3 实现三类:猎杀精英怪 / 守住据点(存活) / 限时逃离。带 Boss 血条计时。
 */
public final class QuestManager {
    private QuestManager() {}

    public enum Type { HUNT_ELITE, SURVIVE, FLEE, CLEAR_CORE, GATHER, SLAY }

    private static class Quest {
        Type type;
        int endTick;
        int totalTicks;
        Vec3d origin;
        BlockPos corePos;
        boolean done;
        ServerBossBar bar;
        Item targetItem;
        int targetCount;
        int killNeed;
        int kills;
        double fleeDistance;
    }

    private record Need(Item item, int count) {}
    private static final Need[] GATHER_POOL = {
            new Need(Items.IRON_INGOT, 16), new Need(Items.GOLD_INGOT, 12), new Need(Items.DIAMOND, 6),
            new Need(Items.COAL, 32), new Need(Items.BONE, 24), new Need(Items.ROTTEN_FLESH, 32),
            new Need(Items.GUNPOWDER, 16), new Need(Items.STRING, 24), new Need(Items.ENDER_PEARL, 4),
            new Need(Items.REDSTONE, 32), new Need(Items.SLIME_BALL, 8), new Need(Items.LEATHER, 12),
            new Need(ModItems.LIFE_SHARD, 6),
    };

    private static final Map<UUID, Quest> ACTIVE = new HashMap<>();
    private static int assignCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(QuestManager::tick);

        // 击杀计数:猎杀精英(数精英)/ 屠戮(数任意敌对怪)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            // 守住据点:玩家死亡即判败
            if (entity instanceof ServerPlayerEntity victim) {
                Quest vq = ACTIVE.get(victim.getUuid());
                if (vq != null && vq.type == Type.SURVIVE && !vq.done) {
                    vq.done = true;
                    if (vq.bar != null) vq.bar.clearPlayers();
                    ACTIVE.remove(victim.getUuid());
                    fail(victim);
                }
            }
            if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) return;
            Quest q = ACTIVE.get(killer.getUuid());
            if (q == null || q.done) return;
            boolean elite = entity.getAttachedOrElse(ModAttachments.IS_ELITE, false);
            if (q.type == Type.HUNT_ELITE && elite) {
                q.kills++;
                if (q.kills >= q.killNeed) complete(killer); else refreshKillBar(q);
            } else if (q.type == Type.SLAY && entity instanceof Monster) {
                q.kills++;
                if (q.kills >= q.killNeed) complete(killer); else refreshKillBar(q);
            }
        });

        Yongye.LOGGER.info("[亡途荒夜] 随机任务系统已挂载");
    }

    private static void tick(MinecraftServer server) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableQuests) return;

        // 定期派发(开局宽限期内不派)
        if (++assignCounter >= cfg.questIntervalTicks) {
            assignCounter = 0;
            if (server.getTicks() >= cfg.questStartGraceTicks) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if (ProgressionManager.isNewbie(p.getWorld())) continue; // 新手期不派任务
                    if (!ACTIVE.containsKey(p.getUuid())) {
                        assign(p, pickType(p));
                    }
                }
            }
        }

        // 推进现有任务(逐条 try/catch:单个任务出错也不拖垮服务端)
        ACTIVE.entrySet().removeIf(e -> {
            try {
                return tickQuest(server, e.getKey(), e.getValue());
            } catch (Exception ex) {
                Yongye.LOGGER.error("[亡途荒夜] 任务处理异常,已移除该任务", ex);
                Quest q = e.getValue();
                if (q != null && q.bar != null) q.bar.clearPlayers();
                return true;
            }
        });
    }

    /** 返回 true 表示该任务结束需移除。 */
    private static boolean tickQuest(MinecraftServer server, UUID id, Quest q) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p == null) { if (q.bar != null) q.bar.clearPlayers(); return true; }

        int left = q.endTick - server.getTicks();
        // 死亡重生后玩家是新实体,血条引用失效 → 每 tick 把当前玩家挂回血条(守住据点死亡不判败)
        if (q.bar != null && !q.bar.getPlayers().contains(p)) q.bar.addPlayer(p);
        if (q.bar != null) q.bar.setPercent(Math.max(0f, Math.min(1f, (float) left / q.totalTicks)));

        if (q.done) { if (q.bar != null) q.bar.clearPlayers(); return true; }

        // 成功判定
        if (q.type == Type.FLEE && q.origin != null && p.getPos().distanceTo(q.origin) >= q.fleeDistance) {
            complete(p);
            if (q.bar != null) q.bar.clearPlayers();
            return true;
        }
        if (q.type == Type.SURVIVE && left <= 0) {
            complete(p);
            if (q.bar != null) q.bar.clearPlayers();
            return true;
        }
        if (q.type == Type.CLEAR_CORE && q.corePos != null
                && p.getWorld() instanceof ServerWorld sw
                && !sw.getBlockState(q.corePos).isOf(ModBlocks.CATASTROPHE_CORE)) {
            complete(p);
            if (q.bar != null) q.bar.clearPlayers();
            return true;
        }
        if (q.type == Type.GATHER && q.targetItem != null
                && p.getInventory().count(q.targetItem) >= q.targetCount) {
            complete(p);
            if (q.bar != null) q.bar.clearPlayers();
            return true;
        }

        // 超时失败
        if (left <= 0) {
            fail(p);
            if (q.bar != null) q.bar.clearPlayers();
            return true;
        }
        return false;
    }

    /** 循序渐进:前期(永夜不足)只派可达成的逃离/存活;永夜到一定等级才派猎杀精英。 */
    private static Type pickType(ServerPlayerEntity p) {
        var rnd = p.getRandom();
        long day = ProgressionManager.gameDay(p.getWorld());
        boolean combatOk = NightfallManager.getLevel() >= YongyeConfig.get().questHuntEliteMinNightfall
                && day >= YongyeConfig.get().eliteStartDay; // 第3天前不派需要精英/据点的任务
        if (combatOk) {
            return Type.values()[rnd.nextInt(Type.values().length)];
        }
        Type[] early = { Type.FLEE, Type.SURVIVE, Type.GATHER };
        return early[rnd.nextInt(early.length)];
    }

    public static void assign(ServerPlayerEntity player, Type type) {
        YongyeConfig cfg = YongyeConfig.get();
        var rnd = player.getRandom();
        Quest q = new Quest();
        q.type = type;
        q.totalTicks = cfg.questTimeLimitTicks;
        q.endTick = player.getServer().getTicks() + cfg.questTimeLimitTicks;
        q.origin = player.getPos();
        if (type == Type.CLEAR_CORE) {
            q.corePos = CatastropheCoreManager.spawnCoreNear(player);
        }
        int nf = NightfallManager.getLevel();
        int players = Math.max(1, player.getServer().getPlayerManager().getPlayerList().size());
        double pScale = 1 + (players - 1) * cfg.questPlayerScaling; // 人越多越难
        if (type == Type.GATHER) {
            Need need = GATHER_POOL[rnd.nextInt(GATHER_POOL.length)];
            q.targetItem = need.item();
            q.targetCount = (int) Math.ceil(need.count() * (1 + nf * 0.4) * pScale);
        }
        if (type == Type.HUNT_ELITE) q.killNeed = (int) Math.ceil((cfg.questHuntEliteCount + nf / 2.0) * pScale);
        if (type == Type.SLAY) q.killNeed = (int) Math.ceil((cfg.questSlayCount + nf * 5) * pScale);
        q.fleeDistance = cfg.questFleeDistance * (1 + nf * 0.2);

        Text title = switch (type) {
            case HUNT_ELITE -> Text.literal("任务·猎杀精英 0/" + q.killNeed).formatted(Formatting.GOLD);
            case SURVIVE -> Text.literal("任务·守住据点:在限时内存活(死亡判败!)").formatted(Formatting.GREEN);
            case FLEE -> Text.literal("任务·限时逃离:远离此地 " + (int) q.fleeDistance + " 格").formatted(Formatting.AQUA);
            case CLEAR_CORE -> Text.literal("任务·清除灾厄核心:摧毁附近的灾厄核心").formatted(Formatting.DARK_RED);
            case GATHER -> Text.literal("任务·搜集物资:限时内集齐 " + q.targetCount + "× ").formatted(Formatting.LIGHT_PURPLE)
                    .append(q.targetItem.getName());
            case SLAY -> Text.literal("任务·屠戮怪物 0/" + q.killNeed).formatted(Formatting.RED);
        };
        BossBar.Color color = switch (type) {
            case HUNT_ELITE -> BossBar.Color.YELLOW;
            case SURVIVE -> BossBar.Color.GREEN;
            case FLEE -> BossBar.Color.BLUE;
            case CLEAR_CORE -> BossBar.Color.RED;
            case GATHER -> BossBar.Color.PURPLE;
            case SLAY -> BossBar.Color.RED;
        };
        q.bar = new ServerBossBar(title, color, BossBar.Style.PROGRESS);
        q.bar.addPlayer(player);
        q.bar.setPercent(1.0f);

        ACTIVE.put(player.getUuid(), q);
        player.sendMessage(Text.literal("【新任务】").formatted(Formatting.GOLD).append(title), false);
    }

    private static void complete(ServerPlayerEntity player) {
        Quest q = ACTIVE.get(player.getUuid());
        if (q != null) {
            if (q.done) return;
            q.done = true;
        }
        player.sendMessage(Text.literal("【任务完成】奖励已发放").formatted(Formatting.GREEN), false);
        reward(player);
    }

    private static void fail(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("【任务失败】黑暗加深……").formatted(Formatting.DARK_RED), false);
        if (player.getServer() != null) {
            Text msg = Text.literal("【任务失败】" + player.getName().getString()
                    + " 未在限时内完成任务,永夜加深!").formatted(Formatting.DARK_RED);
            for (ServerPlayerEntity pp : player.getServer().getPlayerManager().getPlayerList()) {
                if (pp != player) pp.sendMessage(msg, false);
            }
            NightfallManager.escalate(player.getServer());
        }
    }

    private static void refreshKillBar(Quest q) {
        if (q.bar == null) return;
        boolean hunt = q.type == Type.HUNT_ELITE;
        q.bar.setName(Text.literal((hunt ? "任务·猎杀精英 " : "任务·屠戮怪物 ") + q.kills + "/" + q.killNeed)
                .formatted(hunt ? Formatting.GOLD : Formatting.RED));
    }

    private static void reward(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        var r = player.getRandom();
        int nf = NightfallManager.getLevel();
        // 保底一本血量书,等级随永夜走高
        drop(world, player, HealthSkillBookItem.create(3 + nf * 2 + r.nextInt(3)));
        // 高价值按概率(永夜越高越易、越值),不再保底堆钻石/金苹果
        if (r.nextDouble() < 0.40 + nf * 0.10) drop(world, player, new ItemStack(ModItems.LIFE_CRYSTAL, 1 + r.nextInt(2)));
        if (r.nextDouble() < 0.20 + nf * 0.08) drop(world, player, new ItemStack(ModItems.LIFE_CORE));
        if (r.nextDouble() < 0.08 + nf * 0.05) {
            Item[] mats = { ModItems.ENDING_ESSENCE, ModItems.ABYSS_SOUL_CRYSTAL, ModItems.RIFT_FRAGMENT, ModItems.CATASTROPHE_BLOOD_CORE };
            drop(world, player, new ItemStack(mats[r.nextInt(mats.length)]));
        }
        if (r.nextDouble() < 0.15) drop(world, player, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
    }

    private static void drop(ServerWorld world, PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity ie = new ItemEntity(world, player.getX(), player.getY() + 0.5, player.getZ(), stack);
        ie.setToDefaultPickupDelay();
        world.spawnEntity(ie);
    }
}
