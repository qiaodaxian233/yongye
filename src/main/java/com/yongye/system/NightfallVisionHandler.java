package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

/**
 * 永夜剥视:永夜 ≥ N 层时,持续给玩家施加「黑暗」(Darkness,监守者同款)——
 * 屏幕外圈黑暗向内吞噬,有效视野骤缩,营造"永夜里几乎看不清远处"的沉浸恐怖感。
 *
 * 每 2 秒续一次 100t 的黑暗(始终有富余,不会触发到期淡出 → 稳定持续而非周期闪烁)。
 * 纯服务端施加效果,客户端自动渲染黑暗叠层,无需客户端 mixin。
 */
public final class NightfallVisionHandler {
    private NightfallVisionHandler() {}

    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableNightfallDarkness) return;
            if (NightfallManager.getLevel() < cfg.nightfallDarknessMinLevel) return;
            if (++tick < 40) return; // 每 2 秒续一次
            tick = 0;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                GameMode gm = p.interactionManager.getGameMode();
                if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) continue;
                // duration 100t > 续期间隔 40t,保证一直有效、无到期淡出闪烁;不显示图标/粒子,保持沉浸
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, true, false, false));
            }
        });
        Yongye.LOGGER.info("[永夜] 永夜剥视系统已挂载");
    }
}
