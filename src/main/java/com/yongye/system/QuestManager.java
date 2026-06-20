package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 随机任务系统(文档第 9 章)。限时、目标明确、失败提升永夜。
 * Phase 3 实现三类:猎杀精英怪 / 守住据点(存活) / 限时逃离。带 Boss 血条计时。
 */
public final class QuestManager {
    private QuestManager() {}

    public enum Type { HUNT_ELITE, SURVIVE, FLEE }

    private static class Quest {
        Type type;
        int endTick;
        int totalTicks;
        Vec3d origin;
        boolean done;
        ServerBossBar bar;
    }

    private static final Map<UUID, Quest> ACTIVE = new HashMap<>();
    private static int assignCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(QuestManager::tick);

        // 猎杀精英:玩家击杀精英怪即完成
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!entity.getAttachedOrElse(ModAttachments.IS_ELITE, false)) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) return;
            Quest q = ACTIVE.get(killer.getUuid());
            if (q != null && q.type == Type.HUNT_ELITE && !q.done) {
                complete(killer);
            }
        });

        Yongye.LOGGER.info("[亡途荒夜] 随机任务系统已挂载");
    }

    private static void tick(MinecraftServer server) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableQuests) return;

        // 定期派发
        if (++assignCounter >= cfg.questIntervalTicks) {
            assignCounter = 0;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!ACTIVE.containsKey(p.getUuid())) {
                    assign(p, Type.values()[p.getRandom().nextInt(Type.values().length)]);
                }
            }
        }

        // 推进现有任务
        ACTIVE.entrySet().removeIf(e -> {
            UUID id = e.getKey();
            Quest q = e.getValue();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) { if (q.bar != null) q.bar.clearPlayers(); return true; }

            int left = q.endTick - server.getTicks();
            if (q.bar != null) q.bar.setPercent(Math.max(0f, Math.min(1f, (float) left / q.totalTicks)));

            if (q.done) { if (q.bar != null) q.bar.clearPlayers(); return true; }

            // 成功判定
            if (q.type == Type.FLEE && q.origin != null
                    && p.getPos().distanceTo(q.origin) >= 50.0) {
                complete(p);
                if (q.bar != null) q.bar.clearPlayers();
                return true;
            }
            if (q.type == Type.SURVIVE && left <= 0) {
                complete(p);
                if (q.bar != null) q.bar.clearPlayers();
                return true;
            }

            // 超时失败
            if (left <= 0 && !q.done) {
                fail(p);
                if (q.bar != null) q.bar.clearPlayers();
                return true;
            }
            return false;
        });
    }

    public static void assign(ServerPlayerEntity player, Type type) {
        YongyeConfig cfg = YongyeConfig.get();
        Quest q = new Quest();
        q.type = type;
        q.totalTicks = cfg.questTimeLimitTicks;
        q.endTick = player.getServer().getTicks() + cfg.questTimeLimitTicks;
        q.origin = player.getPos();

        Text title = switch (type) {
            case HUNT_ELITE -> Text.literal("任务·猎杀:击杀一只精英怪").formatted(Formatting.GOLD);
            case SURVIVE -> Text.literal("任务·守住据点:在限时内存活").formatted(Formatting.GREEN);
            case FLEE -> Text.literal("任务·限时逃离:远离此地 50 格").formatted(Formatting.AQUA);
        };
        BossBar.Color color = switch (type) {
            case HUNT_ELITE -> BossBar.Color.YELLOW;
            case SURVIVE -> BossBar.Color.GREEN;
            case FLEE -> BossBar.Color.BLUE;
        };
        q.bar = new ServerBossBar(title, color, BossBar.Style.PROGRESS);
        q.bar.addPlayer(player);
        q.bar.setPercent(1.0f);

        ACTIVE.put(player.getUuid(), q);
        player.sendMessage(Text.literal("【新任务】").formatted(Formatting.GOLD).append(title), false);
    }

    private static void complete(ServerPlayerEntity player) {
        Quest q = ACTIVE.get(player.getUuid());
        if (q != null) q.done = true;
        player.sendMessage(Text.literal("【任务完成】奖励已发放").formatted(Formatting.GREEN), false);
        reward(player);
    }

    private static void fail(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("【任务失败】黑暗加深……").formatted(Formatting.DARK_RED), false);
        if (player.getServer() != null) NightfallManager.escalate(player.getServer());
    }

    private static void reward(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        var r = player.getRandom();
        drop(world, player, HealthSkillBookItem.create(2 + r.nextInt(4)));   // V2~V5
        drop(world, player, new ItemStack(ModItems.LIFE_CRYSTAL, 1 + r.nextInt(2)));
        drop(world, player, new ItemStack(Items.GOLDEN_APPLE, 2));
        drop(world, player, new ItemStack(Items.DIAMOND, 1 + r.nextInt(3)));
        if (r.nextDouble() < 0.3) drop(world, player, new ItemStack(ModItems.LIFE_CORE));
    }

    private static void drop(ServerWorld world, PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity ie = new ItemEntity(world, player.getX(), player.getY() + 0.5, player.getZ(), stack);
        ie.setToDefaultPickupDelay();
        world.spawnEntity(ie);
    }
}
