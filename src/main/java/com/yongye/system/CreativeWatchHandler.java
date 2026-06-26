package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.SkillType;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 创造模式监听(m155)—— 反作弊陷阱 + 世界崩塌触发。
 *
 * 规则(应需求):
 *  - 豁免名单 {@code creativeExemptIds}(默认含管理员)里的玩家:不受任何限制,可自由进创造测试,也不会触发崩塌陷阱。
 *  - 非豁免玩家:
 *      ① 在创造模式中,主手持有「禁忌之物」(攻击强化技能书 / 任一稀有材料)→ 触发 {@link WorldDoomManager} 世界崩塌(全怪 ×100,永久)。
 *      ② 第 {@code 2} 次进入创造模式 → 立即强制改回生存(第 1 次容忍,正是上面陷阱的窗口)。计数持久化(死亡保留),累计跨登录。
 *
 * 实现:每若干 tick 轮询各玩家的游戏模式,与上一次记录(transient,重启重置不影响计数本身)对比识别「刚进创造」。
 */
public final class CreativeWatchHandler {
    private CreativeWatchHandler() {}

    private static int tick = 0;
    private static final Map<UUID, GameMode> LAST_MODE = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableCreativeWatch) return;
            if (++tick < 10) return;   // 每 10 tick(0.5s)查一次,够灵敏又不费性能
            tick = 0;

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                UUID uuid = p.getUuid();
                GameMode cur = p.interactionManager.getGameMode();

                // 豁免玩家:完全跳过(但仍更新记录,免得日后移出名单时把历史误判成「刚进创造」)
                if (isExempt(p, cfg)) {
                    LAST_MODE.put(uuid, cur);
                    continue;
                }

                GameMode prev = LAST_MODE.get(uuid);
                if (prev == null) {            // 首次见到该玩家:只记基线,不计数
                    LAST_MODE.put(uuid, cur);
                    continue;
                }

                if (cur == GameMode.CREATIVE) {
                    // ① 世界崩塌陷阱:在创造里持有禁忌之物(每次轮询都查,不限于刚进入的那一刻)
                    if (!WorldDoomManager.isDoom()) {
                        ItemStack held = p.getMainHandStack();
                        if (!held.isEmpty() && isForbidden(held.getItem())) {
                            WorldDoomManager.trigger(server, p.getGameProfile().getName(),
                                    held.getName().getString());
                        }
                    }
                    // ② 第 2 次进入创造 → 强制生存
                    if (prev != GameMode.CREATIVE) {   // 本次是「刚进入创造」
                        int entries = p.getAttachedOrElse(ModAttachments.CREATIVE_ENTRIES, 0) + 1;
                        p.setAttached(ModAttachments.CREATIVE_ENTRIES, entries);
                        if (cfg.creativeForceSurvivalOnSecond && entries >= 2) {
                            p.changeGameMode(GameMode.SURVIVAL);
                            p.sendMessage(Text.literal("永夜不容作弊:你已被强制改回生存模式。")
                                    .formatted(Formatting.RED), false);
                            cur = GameMode.SURVIVAL;   // 记录改后的模式
                        }
                    }
                }

                LAST_MODE.put(uuid, cur);
            }
        });
        Yongye.LOGGER.info("[永夜] 创造模式监听已挂载");
    }

    /** 玩家名是否在豁免名单(creativeExemptIds,逗号/空格分隔,大小写不敏感)。 */
    private static boolean isExempt(ServerPlayerEntity p, YongyeConfig cfg) {
        String list = cfg.creativeExemptIds;
        if (list == null || list.isBlank()) return false;
        String name = p.getGameProfile().getName();
        for (String token : list.split("[,\\s]+")) {
            if (!token.isBlank() && token.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** 禁忌之物:攻击强化技能书,或任一稀有材料(8 种)。 */
    private static boolean isForbidden(Item item) {
        if (item == ModItems.getSkillBook(SkillType.ATTACK)) return true;
        return item == ModItems.LIFE_SHARD
                || item == ModItems.LIFE_CRYSTAL
                || item == ModItems.LIFE_CORE
                || item == ModItems.CATASTROPHE_BLOOD_CORE
                || item == ModItems.ENDLESS_NIGHT_DUST
                || item == ModItems.RIFT_FRAGMENT
                || item == ModItems.ABYSS_SOUL_CRYSTAL
                || item == ModItems.ENDING_ESSENCE;
    }
}
