package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.ArtifactItem;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 新手首次提示(m420,3A 打磨路线图第 28 项):三种「第一次」各发一句机制说明,
 * 只发一次、死亡保留、跨登录不重复(持久位掩码附件 FIRST_HINTS)。
 * <ul>
 *   <li><b>首次永夜升级</b>:检测到世界永夜等级 ≥1(含中途进服的玩家)→ 解释永夜会加深与赎夜出路;</li>
 *   <li><b>首次捡神器</b>:背包首次出现 ArtifactItem(捡的/任务给的/兑换的全覆盖)→ 解释放包即生效;</li>
 *   <li><b>首次遭遇精英</b>:24 格内首次出现 IS_ELITE 怪 → 解释危险性与掉落价值。</li>
 * </ul>
 * 与 m348 新手引导互补:那边是前 3 天的「路线轮播」,这边是不限天数的「事件触发一次性说明」。
 * 每秒一检,三位全满直接早退零开销;金字聊天(可回翻)+ 轻提示音;开关 enableFirstTimeHints。
 */
public final class FirstTimeHintHandler {
    private FirstTimeHintHandler() {}

    private static final int BIT_NIGHTFALL = 1;   // 首次永夜
    private static final int BIT_ARTIFACT  = 2;   // 首次神器
    private static final int BIT_ELITE     = 4;   // 首次精英
    private static final int ALL = BIT_NIGHTFALL | BIT_ARTIFACT | BIT_ELITE;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            if (!YongyeConfig.get().enableFirstTimeHints) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isSpectator()) continue;
                int seen = p.getAttachedOrElse(ModAttachments.FIRST_HINTS, 0);
                if ((seen & ALL) == ALL) continue;   // 三条都发过:零开销早退

                // ① 首次永夜:世界已入永夜(含中途进服),解释机制与出路
                if ((seen & BIT_NIGHTFALL) == 0 && NightfallManager.getLevel() >= 1) {
                    seen = send(p, seen, BIT_NIGHTFALL,
                            "永夜降临:入夜后怪物全面变强,且会随时间推移与任务失败继续加深;"
                                    + "摧毁灾厄核心可「赎夜」压回一级——跟着屏幕上方红色箭头走。");
                }
                // ② 首次神器:背包扫到任意 ArtifactItem
                if ((seen & BIT_ARTIFACT) == 0 && hasArtifact(p)) {
                    seen = send(p, seen, BIT_ARTIFACT,
                            "获得神器:神器放在背包里即自动生效,无需手持;悬停查看它的能力,"
                                    + "背包「饰品」栏可集中收纳。");
                }
                // ③ 首次精英:24 格内出现精英怪
                if ((seen & BIT_ELITE) == 0 && eliteNearby(p)) {
                    send(p, seen, BIT_ELITE,
                            "遭遇精英怪:周身带词缀光环,远比普通怪危险,但常掉强化石与稀有材料——"
                                    + "前期打不过就跑,别硬碰。");
                }
            }
        });
        Yongye.LOGGER.info("[夜蚀] 新手首次提示已挂载(首次永夜/神器/精英各一句机制说明)");
    }

    /** 发提示+落位掩码,返回更新后的掩码。 */
    private static int send(ServerPlayerEntity p, int seen, int bit, String msg) {
        int updated = seen | bit;
        p.setAttached(ModAttachments.FIRST_HINTS, updated);
        p.sendMessage(Text.literal("【夜蚀·机制】" + msg).formatted(Formatting.GOLD), false);
        if (p.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
        }
        return updated;
    }

    private static boolean hasArtifact(ServerPlayerEntity p) {
        for (int i = 0; i < p.getInventory().size(); i++) {
            if (p.getInventory().getStack(i).getItem() instanceof ArtifactItem) return true;
        }
        return false;
    }

    private static boolean eliteNearby(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return false;
        return !sw.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(24),
                m -> m.isAlive() && m.getAttachedOrElse(ModAttachments.IS_ELITE, false)).isEmpty();
    }
}
