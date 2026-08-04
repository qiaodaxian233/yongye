package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * m364 每日悬赏(玩家反馈「不多吧又没啥内容」方案B,作者拍板「开始做」):
 * 每个游戏日(ProgressionManager.gameDay,第 2 天起)给每名玩家随机 3 张悬赏,完成即自动发奖,
 * 三张全清攒「连击」、连击给次日奖励加成——后期每天上线有事干的循环。
 *
 * 悬赏类型(从 4 池随机抽 3 种不重复):
 *   0 讨伐=击杀 N 只怪物(目标随天数爬);
 *   1 猎首=击杀 N 只精英(IS_ELITE 口径);
 *   2 锻造=强化提升 N 级(目标按当天强化石基准档 ×10^(档-1) 跟随石头经济,
 *     计数挂 EquipmentEnhancer.attempt 成功级数 + enhanceWith 强化石直加,四入口全覆盖;
 *     工作台配方直加无玩家管线不计入——有意取舍);
 *   3 坚守=当日累计存活 N 分钟(死亡当日进度清零,真·存活挑战)。
 *
 * 状态存玩家附件 BOUNTY_STATE(String,死亡保留):
 *   「day;streak;type,target,prog,done;×3」——换日在 tick 里检定:昨日三张全清 → streak+1(封顶),
 *   否则归零;每日目标/奖励都按连击加成 (100+streak×bountyStreakBonusPercent)%。
 * 奖励=强化石(基准档+1,精英档)×N + 终焉精华 ×N,offerOrDrop 直接进包(配合 m357 自动入库落仓库)。
 * 同步零新包:HudInfoPayload 尾加 bounty 字段(m361/m363 同款追加口径),任务书第 5 页签「悬赏」展示。
 * 击杀归属走 SummonKillCredit.creditedKiller(m300 口径,召唤物击杀记主人)。
 */
public final class DailyBountyHandler {
    private DailyBountyHandler() {}

    // ===== 悬赏类型 =====
    public static final int T_KILL = 0, T_ELITE = 1, T_ENHANCE = 2, T_SURVIVE = 3;

    public static void register() {
        // —— 击杀线:怪物死亡记讨伐/猎首;玩家死亡清「坚守」当日进度 ——
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!YongyeConfig.get().enableDailyBounty) return;
            if (entity instanceof ServerPlayerEntity dead) {
                resetSurvive(dead);   // 坚守=死亡清零
                return;
            }
            if (!(entity instanceof Monster)) return;
            ServerPlayerEntity killer = SummonKillCredit.creditedKiller(source);
            if (killer == null) return;
            addProgress(killer, T_KILL, 1);
            if (entity.getAttachedOrElse(ModAttachments.IS_ELITE, false)) addProgress(killer, T_ELITE, 1);
        });

        // —— 每 20t:换日检定 + 坚守进度 +1 秒 ——
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            if (!YongyeConfig.get().enableDailyBounty) return;
            ServerWorld ow = server.getOverworld();
            if (ow == null) return;
            long today = ProgressionManager.gameDay(ow);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                State st = load(p);
                if (st == null || st.day != today) {
                    st = rollover(p, st, today, ow);
                    if (st == null) continue;   // 第 1 天(day 0)不发悬赏,让位新手引导
                }
                if (p.isAlive()) addProgressState(p, st, T_SURVIVE, 1);   // 坚守:活着每秒 +1
            }
        });

        Yongye.LOGGER.info("[夜蚀] 每日悬赏已挂载(每日3张/连击加成/完成自动发奖)");
    }

    /** m364:强化提升等级计数(EquipmentEnhancer.attempt 成功级数 + enhanceWith 石头直加两处挂)。 */
    public static void onEnhance(ServerPlayerEntity p, long gained) {
        if (gained <= 0 || !YongyeConfig.get().enableDailyBounty) return;
        addProgress(p, T_ENHANCE, gained);
    }

    /** HudInfoPayload 同步串:「streak;type,target,prog,done;×3」;未生成/关闭="" 客户端不显示。 */
    public static String syncString(ServerPlayerEntity p) {
        if (!YongyeConfig.get().enableDailyBounty) return "";
        State st = load(p);
        if (st == null) return "";
        StringBuilder sb = new StringBuilder().append(st.streak);
        for (int i = 0; i < 3; i++) sb.append(';').append(st.type[i]).append(',').append(st.target[i])
                .append(',').append(Math.min(st.prog[i], st.target[i])).append(',').append(st.done[i] ? 1 : 0);
        return sb.toString();
    }

    // ===== 状态结构与存取 =====

    private static final class State {
        long day; int streak;
        int[] type = new int[3];
        long[] target = new long[3], prog = new long[3];
        boolean[] done = new boolean[3];
    }

    private static State load(ServerPlayerEntity p) {
        String raw = p.getAttachedOrElse(ModAttachments.BOUNTY_STATE, "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            String[] seg = raw.split(";");
            if (seg.length != 5) return null;
            State st = new State();
            st.day = Long.parseLong(seg[0]);
            st.streak = Integer.parseInt(seg[1]);
            for (int i = 0; i < 3; i++) {
                String[] f = seg[2 + i].split(",");
                st.type[i] = Integer.parseInt(f[0]);
                st.target[i] = Long.parseLong(f[1]);
                st.prog[i] = Long.parseLong(f[2]);
                st.done[i] = "1".equals(f[3]);
            }
            return st;
        } catch (Exception e) {
            return null;   // 坏档自愈:下次 tick 重新生成
        }
    }

    private static void save(ServerPlayerEntity p, State st) {
        StringBuilder sb = new StringBuilder().append(st.day).append(';').append(st.streak);
        for (int i = 0; i < 3; i++) sb.append(';').append(st.type[i]).append(',').append(st.target[i])
                .append(',').append(st.prog[i]).append(',').append(st.done[i] ? 1 : 0);
        p.setAttached(ModAttachments.BOUNTY_STATE, sb.toString());
    }

    // ===== 换日:结算昨日连击 + 生成今日三张 =====

    private static State rollover(ServerPlayerEntity p, State old, long today, ServerWorld ow) {
        if (today < 1) return null;   // 第 1 天(day 0)让位新手引导,第 2 天起刷悬赏
        YongyeConfig c = YongyeConfig.get();
        int streak = 0;
        if (old != null && old.done[0] && old.done[1] && old.done[2]) {
            streak = Math.min(Math.max(0, c.bountyStreakCap), old.streak + 1);
        }
        State st = new State();
        st.day = today; st.streak = streak;
        // 从 4 池随机抽 3 种不重复(Fisher–Yates 前 3)
        int[] pool = {T_KILL, T_ELITE, T_ENHANCE, T_SURVIVE};
        net.minecraft.util.math.random.Random r = p.getRandom();
        for (int i = pool.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = pool[i]; pool[i] = pool[j]; pool[j] = t;
        }
        for (int i = 0; i < 3; i++) {
            st.type[i] = pool[i];
            st.target[i] = targetFor(pool[i], today, ow, c);
            st.prog[i] = 0; st.done[i] = false;
        }
        save(p, st);
        p.sendMessage(Text.literal("◆ 今日悬赏已刷新(3 张)" + (streak > 0 ? " · 连击 ×" + streak
                + "(奖励 +" + streak * Math.max(0, c.bountyStreakBonusPercent) + "%)" : "")
                + " · 任务书「悬赏」页查看").formatted(Formatting.GOLD), false);
        return st;
    }

    private static long targetFor(int type, long day, ServerWorld ow, YongyeConfig c) {
        return switch (type) {
            case T_KILL -> Math.min(200, Math.max(5, c.bountyKillBase + day * 2));
            case T_ELITE -> Math.min(12, Math.max(1, c.bountyEliteBase + day / 4));
            case T_ENHANCE -> {
                int tier = Math.max(1, Math.min(10, EnhanceStoneDrops.baseTier(ow)));
                long face = 1; for (int i = 1; i < tier; i++) face *= 10;   // 10^(档-1)
                yield Math.max(1, c.bountyEnhanceBase) * face;              // 跟随强化石经济
            }
            case T_SURVIVE -> Math.max(1, c.bountySurviveMinutes) * 60L;    // 秒
            default -> 1;
        };
    }

    // ===== 进度推进 + 完成结算 =====

    private static void addProgress(ServerPlayerEntity p, int type, long amount) {
        State st = load(p);
        if (st == null) return;
        addProgressState(p, st, type, amount);
    }

    private static void addProgressState(ServerPlayerEntity p, State st, int type, long amount) {
        for (int i = 0; i < 3; i++) {
            if (st.type[i] != type || st.done[i]) continue;
            long np = st.prog[i] + amount;
            st.prog[i] = np < st.prog[i] ? Long.MAX_VALUE : np;   // 饱和防溢出(m293 口径)
            if (st.prog[i] >= st.target[i]) {
                st.done[i] = true;
                grantReward(p, st, i);
            }
            save(p, st);
            return;
        }
    }

    /** 坚守=死亡当日进度清零(未完成的才清,已达成不回收)。 */
    private static void resetSurvive(ServerPlayerEntity p) {
        State st = load(p);
        if (st == null) return;
        for (int i = 0; i < 3; i++) {
            if (st.type[i] == T_SURVIVE && !st.done[i] && st.prog[i] > 0) {
                st.prog[i] = 0;
                save(p, st);
                p.sendMessage(Text.literal("坚守悬赏:死亡,今日存活进度清零").formatted(Formatting.GRAY), false);
                return;
            }
        }
    }

    /** 完成发奖:强化石(基准档+1,精英档)×N + 终焉精华 ×N,连击按百分比加成,offerOrDrop 进包。 */
    private static void grantReward(ServerPlayerEntity p, State st, int idx) {
        YongyeConfig c = YongyeConfig.get();
        long mult = 100 + (long) st.streak * Math.max(0, c.bountyStreakBonusPercent);
        int tier = Math.max(1, Math.min(10, EnhanceStoneDrops.baseTier(p.getServerWorld()) + 1));
        int stones = (int) Math.max(1, Math.max(0, c.bountyRewardStones) * mult / 100);
        int essence = (int) Math.max(1, Math.max(0, c.bountyRewardEssence) * mult / 100);
        p.getInventory().offerOrDrop(new ItemStack(ModItems.enhanceStone(tier), stones));
        p.getInventory().offerOrDrop(new ItemStack(ModItems.ENDING_ESSENCE, essence));
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.2f);
        boolean allDone = st.done[0] && st.done[1] && st.done[2];
        p.sendMessage(Text.literal("◆ 悬赏达成:" + titleOf(st.type[idx]) + "!奖励已发放(强化石·" + tierName(tier)
                + " ×" + stones + " · 终焉精华 ×" + essence + ")"
                + (allDone ? " —— 今日三张全清,明日连击 ×" + Math.min(Math.max(0, c.bountyStreakCap), st.streak + 1) : ""))
                .formatted(Formatting.GOLD), false);
    }

    private static String titleOf(int type) {
        return switch (type) {
            case T_KILL -> "讨伐"; case T_ELITE -> "猎首"; case T_ENHANCE -> "锻造"; case T_SURVIVE -> "坚守";
            default -> "悬赏";
        };
    }

    private static String tierName(int tier) {
        String[] cn = {"壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖", "拾"};
        return cn[Math.max(1, Math.min(10, tier)) - 1];
    }
}
