package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 存活排行:周期性记录每名在线玩家「曾达到的最高永夜层数 / 最高游戏天数」(终身最佳,死亡保留)。
 * 用 /yongye top 查看在线玩家排行。
 */
public final class SurvivalRankHandler {
    private SurvivalRankHandler() {}

    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!YongyeConfig.get().enableSurvivalRank) return;
            if (++tick < 100) return; // 每 5 秒更新一次,开销极低
            tick = 0;
            int nf = NightfallManager.getLevel();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                int day = (int) ProgressionManager.gameDay(p.getWorld());
                if (nf > p.getAttachedOrElse(ModAttachments.BEST_NIGHTFALL, 0)) {
                    p.setAttached(ModAttachments.BEST_NIGHTFALL, nf);
                }
                if (day > p.getAttachedOrElse(ModAttachments.BEST_DAY, 0)) {
                    p.setAttached(ModAttachments.BEST_DAY, day);
                }
            }
        });
        Yongye.LOGGER.info("[永夜] 存活排行系统已挂载");
    }
}
