package com.yongye.system;

import com.google.gson.Gson;
import com.yongye.Yongye;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 世界崩塌(m155):一个整局的全局开关,持久化到存档 yongye_doom.json(与世界难度/永夜等级同套路)。
 *  - 触发条件由 {@link CreativeWatchHandler} 判定:非豁免玩家在创造模式持有「禁忌之物」(攻击强化技能书 / 任一稀有材料)。
 *  - 触发后:全服播报「谁拿了什么导致世界崩塌」,并让**所有怪物**(已加载的 + 之后生成的)血量与攻击各 ×{@code doomMobMultiplier}(默认 100)。
 *  - 设计为**永久**:触发后不再自动关闭、玩家无法关闭(应需求)。如需人工恢复,停服后删除/改写存档根目录的 yongye_doom.json 即可。
 */
public final class WorldDoomManager {
    private WorldDoomManager() {}

    private static final Gson GSON = new Gson();
    private static boolean doom = false;
    private static Path savePath;

    private static class State { boolean doom = false; }

    public static boolean isDoom() { return doom; }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            savePath = server.getSavePath(WorldSavePath.ROOT).resolve("yongye_doom.json");
            load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());

        // 世界已崩塌时,之后新生成/加载的怪也立即 ×100(独立于怪物增强系统的开关与早返,确保「全面」)。
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!doom) return;
            if (entity instanceof MobEntity mob && entity instanceof Monster) {
                MobEnhancementHandler.applyDoom(mob);
            }
        });

        Yongye.LOGGER.info("[永夜] 世界崩塌系统已挂载");
    }

    /** 触发世界崩塌(幂等:已崩塌则直接返回)。playerName/itemName 仅用于播报。 */
    public static void trigger(MinecraftServer server, String playerName, String itemName) {
        if (doom) return;
        doom = true;
        save();

        server.getPlayerManager().broadcast(Text.literal(
                "【世界崩塌】" + playerName + " 触碰了禁忌之物【" + itemName + "】——永夜彻底失控,怪物全面强化 ×"
                        + trim(com.yongye.YongyeConfig.get().doomMobMultiplier) + "!")
                .formatted(Formatting.DARK_RED, Formatting.BOLD), false);

        // 对当前所有世界里已加载的怪立即施加 ×100(新怪由上面的 ENTITY_LOAD 负责)。
        for (ServerWorld world : server.getWorlds()) {
            for (net.minecraft.entity.Entity e : world.iterateEntities()) {
                if (e instanceof MobEntity mob && e instanceof Monster && mob.isAlive()) {
                    MobEnhancementHandler.applyDoom(mob);
                }
            }
        }
    }

    private static String trim(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static void load() {
        doom = false;   // 切换世界先归位(静态字段跨世界存活),避免上一个世界的崩塌状态带到没有该文件的新世界
        try {
            if (savePath != null && Files.exists(savePath)) {
                State s = GSON.fromJson(Files.readString(savePath), State.class);
                if (s != null) doom = s.doom;
            }
        } catch (IOException | RuntimeException e) {
            Yongye.LOGGER.error("[永夜] 读取世界崩塌状态失败", e);
        }
    }

    private static void save() {
        try {
            if (savePath == null) return;
            State s = new State();
            s.doom = doom;
            Files.writeString(savePath, GSON.toJson(s));
        } catch (IOException e) {
            Yongye.LOGGER.error("[永夜] 写入世界崩塌状态失败", e);
        }
    }
}
