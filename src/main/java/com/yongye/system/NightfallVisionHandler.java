package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

/**
 * 永夜剥视:永夜 ≥ N 层时压缩视野,营造"永夜里看不清远处"的沉浸感。
 *
 * 视野压缩现由**客户端恒定暗角(vignette)**实现(YongyeClient,亮度固定不闪),
 * 强度通过 NightfallSyncPayload 的 vision 字段下发(由 sendNightfall 按配置计算)。
 *
 * 本处仅在 `nightfallDarknessEffect=true`(默认 false)时,额外施加原版「黑暗」效果——
 * 但该效果自带呼吸式脉动会"一闪一闪",故默认关闭;保留仅为想要原版脉动观感的用户。
 * 纯服务端,无客户端 mixin。
 */
public final class NightfallVisionHandler {
    private NightfallVisionHandler() {}

    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            // 视野压缩本体已是客户端暗角;这里只在显式开启原版脉动黑暗时才施加效果
            if (!cfg.enableNightfallDarkness || !cfg.nightfallDarknessEffect) return;
            if (NightfallManager.getLevel() < cfg.nightfallDarknessMinLevel) return;
            if (++tick < 40) return; // 每 2 秒续一次
            tick = 0;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                GameMode gm = p.interactionManager.getGameMode();
                if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) continue;
                // duration 100t > 续期间隔 40t,保证一直有效;不显示图标/粒子
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, true, false, false));
            }
        });
        Yongye.LOGGER.info("[永夜] 永夜剥视系统已挂载");
    }
}
