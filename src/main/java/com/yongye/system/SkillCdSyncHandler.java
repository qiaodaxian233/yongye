package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.network.SkillCdPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 技能 CD 常显 HUD 数据源(m346,作者:「R/G/V、X、C 的剩余冷却常显,不要只在按键失败时提示」)。
 *  - 每 10 tick 把三套冷却表(武器技能 R/G/V + 大招 X + 小技能 C,m321 已统一 server.getTicks() 时基)
 *    读成剩余 tick,经 SkillCdPayload(5 varint)发给玩家本人;
 *  - 全就绪期间静默不发(省流量);「在转 → 转完」边沿补发一包全 0 让 HUD 归位,之后继续静默;
 *  - 客户端收到后本地每 tick 递减保平滑,渲染在血条面板左沿外(YongyeClient);
 *  - 开关 enableSkillCdHud(服务端不发 → 客户端 HUD 自然不显示;专用服由服务端配置统一管)。
 */
public final class SkillCdSyncHandler {
    private SkillCdSyncHandler() {}

    /** 上一包是否含非零冷却(用于「在转→转完」边沿补零;false=已静默)。 */
    private static final Map<UUID, Boolean> WAS_ACTIVE = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 != 0) return;
            if (!YongyeConfig.get().enableSkillCdHud) return;
            long now = server.getTicks();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                UUID id = p.getUuid();
                int r = WeaponSkillManager.remaining(id, 0, now);
                int g = WeaponSkillManager.remaining(id, 1, now);
                int v = WeaponSkillManager.remaining(id, 2, now);
                int x = ClassUltimateManager.remaining(id, now);
                int c = ClassMinorSkillManager.remaining(id, now);
                boolean active = (r | g | v | x | c) != 0;
                boolean was = WAS_ACTIVE.getOrDefault(id, false);
                if (!active && !was) continue;               // 全就绪且已归位:静默
                ServerPlayNetworking.send(p, new SkillCdPayload(r, g, v, x, c));
                WAS_ACTIVE.put(id, active);                  // 转完那一包=全 0 归位,之后静默
            }
        });

        // 玩家退出清缓存,避免内存堆积(与三管理器冷却表同口径)
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                WAS_ACTIVE.remove(handler.player.getUuid()));

        Yongye.LOGGER.info("[夜蚀] 技能冷却常显同步已挂载(R/G/V + 大招 + 小技能,每10t)");
    }
}
