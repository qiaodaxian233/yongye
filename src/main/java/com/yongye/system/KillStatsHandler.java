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
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, new HudInfoPayload(
                        p.getAttachedOrElse(ModAttachments.TOTAL_KILLS, 0L), nextName, nextSec));
            }
        });

        Yongye.LOGGER.info("[夜蚀] 战况看板已挂载(击杀统计/天数/阶段预告,左上角)");
    }
}
