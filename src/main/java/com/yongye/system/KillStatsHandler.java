package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.network.HudInfoPayload;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 战况看板(m288,作者:「加一个杀怪统计、天数显示、阶段预告」):
 *  - 杀怪统计:口径与保护卷完全一致(Monster + 玩家击杀),累计进 TOTAL_KILLS 附件(跨登录/死亡保留);
 *  - 每 20 tick 向每名玩家下发 HudInfoPayload(累计击杀 + 下一阶段名 + 久留升层剩余秒);
 *  - 天数不下发:昼夜时钟原版同步,客户端直接 ProgressionManager.gameDay 计算(m252 收口,睡觉跳夜也算天);
 *  - 显示位置在客户端左上角(YongyeClient),开关 enableHudInfoPanel。
 */
public final class KillStatsHandler {
    private KillStatsHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Monster)) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) return;
            killer.setAttached(ModAttachments.TOTAL_KILLS,
                    killer.getAttachedOrElse(ModAttachments.TOTAL_KILLS, 0L) + 1L);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            if (!YongyeConfig.get().enableHudInfoPanel) return;
            String nextName = NightfallManager.getNextLevelName();
            int nextSec = NightfallManager.getEscalateRemainingSeconds();
            String forecast = server.getOverworld() == null ? ""
                    : buildDayForecast(ProgressionManager.gameDay(server.getOverworld()));
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, new HudInfoPayload(
                        p.getAttachedOrElse(ModAttachments.TOTAL_KILLS, 0L), nextName, nextSec, forecast));
            }
        });

        Yongye.LOGGER.info("[夜蚀] 战况看板已挂载(击杀统计/天数/阶段预告/按天事件预告)");
    }

    /**
     * m289 按天事件预告(作者:「第几天会出现什么」):全库 gameDay 门槛收口成一张表,按**实时配置值**取,
     * 改配置预告自动跟着变。找出最近一个还没到的事件日,同日多件并列(最多 3 件+「等」);
     * 怪物进化是周期事件(每 evolutionEveryDays 天),取下一个整倍数日一并参与比较。
     * 全部事件已过=只剩周期进化;连它也没有(配置≤0)=返回 "" 整行不显示。
     * 注意口径:各门槛判的是 gameDay >= minDay(0 起算),对玩家展示 = minDay+1(第 1 天起算)。
     */
    private static String buildDayForecast(long today) {
        YongyeConfig c = YongyeConfig.get();
        String[] names = {
                "佩恩降临", "怪物学会挖掘", "精英穿上装备", "袭击队长现身", "怪物BOSS出没",
                "野生黑龙盘旋", "阿努比斯降临", "大地侵蚀显现", "红蜘蛛出没", "死亡法师出没", "浴火凤凰出没" };
        long[] mins = {
                c.painSpawnMinDay, c.mobDigStartDay, c.eliteEquipStartDay, c.bossRaidCaptainMinDay, c.mobBossStartDay,
                c.wildDragonMinDay, c.anubisMinDay, c.blightStartDay, c.redSpiderMinDay, c.deathMageMinDay, c.phoenixMinDay };
        long best = Long.MAX_VALUE;
        for (long m : mins) if (m > today && m < best) best = m;
        // 周期事件:怪物进化(每 N 天),下一个整倍数日
        long evoNext = Long.MAX_VALUE;
        if (c.evolutionEveryDays > 0) evoNext = (today / c.evolutionEveryDays + 1) * (long) c.evolutionEveryDays;
        if (evoNext < best) best = evoNext;
        if (best == Long.MAX_VALUE) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int i = 0; i < mins.length; i++) {
            if (mins[i] != best) continue;
            if (n == 3) { sb.append(" 等"); break; }
            if (n > 0) sb.append(" · ");
            sb.append(names[i]);
            n++;
        }
        if (evoNext == best && n < 3) {
            if (n > 0) sb.append(" · ");
            sb.append("怪物进化");
        }
        long remain = best - today;
        return "第 " + (best + 1) + " 天:" + sb + (remain <= 1 ? "(明天!)" : "(还有 " + remain + " 天)");
    }
}
