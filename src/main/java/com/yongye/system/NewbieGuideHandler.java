package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 新手前 3 天引导(m348,作者:「小目标提示『先选职→做书→强化→找核心』,降低玩家进服懵住的概率」)。
 *  - 引导期(游戏天数 < newbieGuideDays)内,每 newbieGuideIntervalSeconds 秒在 actionbar 提示
 *    **第一件还没做的事**:①没职业→选职 ②没学过任何技能书→学书 ③全身无强化→强化;
 *    三件都做了 → 轮播通用提示(核心箭头/任务书/第 5 天 BOSS 预警);
 *  - 每次登录(仍在引导期)另发一条聊天版路线总纲,玩家翻聊天记录随时能看;
 *  - 天数口径走 ProgressionManager.gameDay(m252 收口,睡觉跳夜也算天);过了引导期整套静默零开销;
 *  - 每秒一检、判定全用在树只读接口(learnedList/getLearnedHealth/LEARNED_SKILLS/getLevel),不写任何状态;
 *  - 开关 enableNewbieGuide;计时/轮播缓存挂 DISCONNECT 清理。
 */
public final class NewbieGuideHandler {
    private NewbieGuideHandler() {}

    /** 每玩家引导计时(秒);到 newbieGuideIntervalSeconds 清零并提示一次。 */
    private static final Map<UUID, Integer> SECONDS = new HashMap<>();
    /** 每玩家通用提示轮播游标(三件事都做完后循环用)。 */
    private static final Map<UUID, Integer> ROTATION = new HashMap<>();

    /** 三件事都做完后的通用轮播提示(核心箭头 / 任务书 / 第 5 天 BOSS 预警)。 */
    private static final String[] GENERIC_TIPS = {
            "跟随屏幕上方红色箭头,寻找并摧毁灾厄核心",
            "背包点「任务」打开任务书,按主线阶段一步步推进",
            "第 5 天起 BOSS 陆续现身,抓紧强化装备与技能"
    };

    public static void register() {
        // 登录总纲:引导期内每次登录发一条聊天版路线图(false=进聊天记录,可回翻)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableNewbieGuide) return;
            ServerPlayerEntity p = handler.player;
            if (ProgressionManager.gameDay(p.getWorld()) >= cfg.newbieGuideDays) return;
            p.sendMessage(Text.literal("【夜蚀·新手指引】前期路线:①右键「职业选择书」选职 → ②背包「学书」学技能书 → "
                    + "③背包「强化」提升武器 → ④跟随红色箭头找灾厄核心。第 5 天起 BOSS 陆续现身,抓紧变强!")
                    .formatted(Formatting.GOLD), false);
        });

        // 每秒计数,到间隔就取「第一件没做的事」actionbar 提示
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableNewbieGuide) return;
            int interval = Math.max(10, cfg.newbieGuideIntervalSeconds);   // 下限 10s 防配置手滑刷屏
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isSpectator()) continue;
                if (ProgressionManager.gameDay(p.getWorld()) >= cfg.newbieGuideDays) continue;
                UUID id = p.getUuid();
                int sec = SECONDS.merge(id, 1, Integer::sum);
                if (sec < interval) continue;
                SECONDS.put(id, 0);
                p.sendMessage(Text.literal("【引导】" + nextGoal(p)).formatted(Formatting.GOLD), true);
            }
        });

        // 玩家退出清计时/轮播缓存,避免内存堆积
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SECONDS.remove(handler.player.getUuid());
            ROTATION.remove(handler.player.getUuid());
        });

        Yongye.LOGGER.info("[夜蚀] 新手前3天引导已挂载(选职→学书→强化→找核心)");
    }

    /** 按作者口径「选职→学书→强化→找核心」取第一件没做的事;都做了轮播通用提示。 */
    private static String nextGoal(ServerPlayerEntity p) {
        if (ClassManager.learnedList(p).isEmpty())
            return "先选职业——右键使用「职业选择书」(开局已发,选完领开局武器)";
        if (!hasLearnedAnyBook(p))
            return "收集技能书,背包点「学书」一键学习(血量书每级 +10 最大生命)";
        if (!hasAnyEnhanced(p))
            return "用强化石强化武器——背包「强化」按钮,越强越能活";
        int r = ROTATION.merge(p.getUuid(), 1, Integer::sum);
        return GENERIC_TIPS[Math.floorMod(r, GENERIC_TIPS.length)];
    }

    /** 学过任何技能书 = 血量书累计等级 > 0 或职业技能书(LEARNED_SKILLS)任一 > 0。 */
    private static boolean hasLearnedAnyBook(ServerPlayerEntity p) {
        if (PlayerSkillManager.getLearnedHealth(p) > 0) return true;
        for (int v : p.getAttachedOrElse(ModAttachments.LEARNED_SKILLS, Map.<String, Integer>of()).values())
            if (v > 0) return true;
        return false;
    }

    /** 全背包(含穿着/副手,inv.size() 覆盖)任一物品强化等级 > 0 即算已强化。 */
    private static boolean hasAnyEnhanced(ServerPlayerEntity p) {
        net.minecraft.entity.player.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && EquipmentEnhancer.getLevel(s) > 0) return true;
        }
        return false;
    }
}
