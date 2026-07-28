package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * m330:新周目·永夜+(作者拍板方案①)。讨伐末影龙后自动开启"二周目":怪物强度与掉落全线翻倍
 * (倍率可配),把无限强化的后期数值真正用起来。存档级持久化照 NightfallManager 口径
 * (世界根目录 yongye_ngplus.json),跨重启保持;`yongye config set enableNgPlus false` 可整体关闭生效。
 * 挂点:MobEnhancementHandler.progressionMultiplier 末端乘(在封顶之后乘——二周目就该破上限)、
 * LootHandler lm/gm 双倍率、图鉴页显示状态。
 */
public final class NewGamePlusManager {
    private NewGamePlusManager() {}

    private static boolean active = false;
    private static Path savePath;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            savePath = server.getSavePath(WorldSavePath.ROOT).resolve("yongye_ngplus.json");
            active = Files.exists(savePath);   // 文件存在即已开启(内容不重要,存在性即状态)
            if (active) Yongye.LOGGER.info("[夜蚀] 永夜+(二周目)已开启");
        });
    }

    /** 讨伐末影龙时调用:开启二周目并全服广播(幂等)。 */
    public static void activate(MinecraftServer server) {
        if (active) return;
        active = true;
        try { Files.writeString(savePath, "{\"active\":true}"); } catch (Exception e) {
            Yongye.LOGGER.warn("[夜蚀] 永夜+状态写盘失败", e);
        }
        server.getPlayerManager().broadcast(Text.literal(
                        "☽ 永夜+已开启!二周目降临:怪物强度与掉落全线翻倍,无限强化的真正战场开始了")
                .formatted(Formatting.DARK_PURPLE, Formatting.BOLD), false);
    }

    public static boolean isActive() {
        return active && YongyeConfig.get().enableNgPlus;
    }

    /** 怪物强度倍率(未开启=1)。 */
    public static double mobMult(YongyeConfig cfg) { return isActive() ? Math.max(1.0, cfg.ngPlusMobMult) : 1.0; }

    /** 掉落倍率(未开启=1)。 */
    public static double lootMult(YongyeConfig cfg) { return isActive() ? Math.max(1.0, cfg.ngPlusLootMult) : 1.0; }
}
