package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.item.SkillType;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Function;

/**
 * m328:主线任务书(FTB Quests 风格内建版,作者:「添加任务书那个 FTB Quests,做一系列任务,最终击杀末影龙」)。
 * 不引外部依赖(FTB Quests 是独立模组还要玩家另装):内建 16 阶段线性主线,从「活过第一夜」一路到
 * 「终焉:讨伐末影龙」,右键任务书/背包「任务」钮打开进度界面,达成后领奖自动进下一阶段。
 * 架构:阶段表静态数据(标题/描述/判定/奖励);进度=玩家附件(persistent+copyOnDeath,死亡不清);
 * 击杀类计数走 AFTER_DEATH 钩子(击杀归属复用 SummonerHandler.creditedKiller,召唤物击杀也算主人——
 * 与掉落/看板同口径);领取=服务端权威复核 check 再发奖。与随机限时任务(QuestManager)完全独立并行。
 */
public final class MainQuestLine {
    private MainQuestLine() {}

    /** 一个主线阶段:标题/目标描述/达成判定/奖励描述/发奖。 */
    public record Stage(String title, String goal, Function<ServerPlayerEntity, Boolean> check,
                        String rewardDesc, java.util.function.Consumer<ServerPlayerEntity> reward) {}

    public static final Stage[] STAGES = new Stage[]{
            new Stage("破晓", "活过第一夜(游戏进入第 2 天)",
                    p -> ProgressionManager.gameDay(p.getWorld()) >= 2,
                    "生命碎片 ×10", p -> give(p, ModItems.LIFE_SHARD, 10)),
            new Stage("开卷", "技能总等级达到 V5(学任意技能书)",
                    p -> totalSkill(p) >= 5,
                    "血量技能书 V5", p -> p.getInventory().offerOrDrop(com.yongye.item.HealthSkillBookItem.create(5))),
            new Stage("锋芒", "把任意装备强化到 +10",
                    p -> maxEnhance(p) >= 10,
                    "强化石·贰 ×10", p -> give(p, ModItems.enhanceStone(2), 10)),
            new Stage("见血", "累计击杀 20 只怪物",
                    p -> kills(p) >= 20,
                    "生命结晶 ×5", p -> give(p, ModItems.LIFE_CRYSTAL, 5)),
            new Stage("立命", "选定本命职业",
                    p -> !ClassManager.learnedList(p).isEmpty(),
                    "强化保护卷 ×1", p -> give(p, ModItems.ENHANCE_PROTECT_SCROLL, 1)),
            new Stage("猎手", "击杀 3 只精英怪",
                    p -> p.getAttachedOrElse(ModAttachments.MAIN_ELITE_KILLS, 0) >= 3,
                    "生命核心 ×3", p -> give(p, ModItems.LIFE_CORE, 3)),
            new Stage("淬炼", "把任意装备强化到 +100",
                    p -> maxEnhance(p) >= 100,
                    "强化石·伍 ×5", p -> give(p, ModItems.enhanceStone(5), 5)),
            new Stage("百人斩", "累计击杀 100 只怪物",
                    p -> kills(p) >= 100,
                    "生命结晶 ×20", p -> give(p, ModItems.LIFE_CRYSTAL, 20)),
            new Stage("夜行者", "永夜等级达到 3 层",
                    p -> NightfallManager.getLevel() >= 3,
                    "深渊魂晶 ×3 + 永夜尘 ×10", p -> { give(p, ModItems.ABYSS_SOUL_CRYSTAL, 3); give(p, ModItems.ENDLESS_NIGHT_DUST, 10); }),
            new Stage("屠魔", "击杀任意一只 BOSS",
                    p -> p.getAttachedOrElse(ModAttachments.MAIN_BOSS_KILLS, 0) >= 1,
                    "灾变血核 ×2", p -> give(p, ModItems.CATASTROPHE_BLOOD_CORE, 2)),
            new Stage("千锤", "把任意装备强化到 +1000",
                    p -> maxEnhance(p) >= 1000,
                    "强化石·柒 ×3", p -> give(p, ModItems.enhanceStone(7), 3)),
            new Stage("学海", "技能总等级达到 V500",
                    p -> totalSkill(p) >= 500,
                    "终焉精华 ×5", p -> give(p, ModItems.ENDING_ESSENCE, 5)),
            new Stage("千人斩", "累计击杀 1000 只怪物",
                    p -> kills(p) >= 1000,
                    "灾变血核 ×5", p -> give(p, ModItems.CATASTROPHE_BLOOD_CORE, 5)),
            new Stage("弑神", "击败佩恩",
                    p -> p.getAttachedOrElse(ModAttachments.MAIN_PAIN_SLAIN, false),
                    "终焉精华 ×10 + 灾变血核 ×5", p -> { give(p, ModItems.ENDING_ESSENCE, 10); give(p, ModItems.CATASTROPHE_BLOOD_CORE, 5); }),
            new Stage("远征", "备战末地:持有末影珍珠 ×8",
                    p -> countItem(p, Items.ENDER_PEARL) >= 8,
                    "终焉精华 ×5 + 强化石·捌 ×2", p -> { give(p, ModItems.ENDING_ESSENCE, 5); give(p, ModItems.enhanceStone(8), 2); }),
            new Stage("终焉", "讨伐末影龙",
                    p -> p.getAttachedOrElse(ModAttachments.MAIN_DRAGON_SLAIN, false),
                    "灾变血核 ×10 + 终焉精华 ×20 + 强化石·拾 ×1",
                    p -> { give(p, ModItems.CATASTROPHE_BLOOD_CORE, 10); give(p, ModItems.ENDING_ESSENCE, 20); give(p, ModItems.enhanceStone(10), 1); }),
    };

    /** 击杀计数钩子:归属复用 creditedKiller(召唤物击杀记主人);精英按名判(与叠皮/红眼同口径)。 */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!YongyeConfig.get().enableMainQuest) return;
            if (entity instanceof ServerPlayerEntity) return;
            ServerPlayerEntity killer = SummonerHandler.creditedKiller(source);
            if (killer == null) return;

            killer.setAttached(ModAttachments.MAIN_KILLS, kills(killer) + 1);
            if (entity.hasCustomName() && entity.getCustomName() != null
                    && entity.getCustomName().getString().contains("精英")) {
                killer.setAttached(ModAttachments.MAIN_ELITE_KILLS,
                        killer.getAttachedOrElse(ModAttachments.MAIN_ELITE_KILLS, 0) + 1);
            }
            if (isBoss(entity)) {
                killer.setAttached(ModAttachments.MAIN_BOSS_KILLS,
                        killer.getAttachedOrElse(ModAttachments.MAIN_BOSS_KILLS, 0) + 1);
            }
            if (PainBossHandler.isPain(entity)) {
                killer.setAttached(ModAttachments.MAIN_PAIN_SLAIN, true);
            }
            if (entity instanceof EnderDragonEntity) {
                killer.setAttached(ModAttachments.MAIN_DRAGON_SLAIN, true);
                killer.getServer().getPlayerManager().broadcast(
                        Text.literal("☀ " + killer.getName().getString() + " 讨伐了末影龙!永夜的终焉到来了!")
                                .formatted(Formatting.GOLD, Formatting.BOLD), false);
            }
            // 达成即时提示(不领不推进,提示去任务书领奖)
            int st = stage(killer);
            if (st < STAGES.length && STAGES[st].check().apply(killer)) {
                killer.sendMessage(Text.literal("◆ 主线【" + STAGES[st].title() + "】已达成!打开任务书领取奖励")
                        .formatted(Formatting.GOLD), true);
            }
        });
    }

    /** 领奖(服务端权威):复核当前阶段达成 → 发奖 → 阶段+1 → 回发最新快照。 */
    public static void claim(ServerPlayerEntity p) {
        if (!YongyeConfig.get().enableMainQuest) return;
        int st = stage(p);
        if (st >= STAGES.length) { msg(p, "主线已全部完成,恭喜通关!", Formatting.GOLD); sync(p); return; }
        Stage s = STAGES[st];
        if (!s.check().apply(p)) { msg(p, "【" + s.title() + "】尚未达成:" + s.goal(), Formatting.RED); sync(p); return; }
        s.reward().accept(p);
        p.setAttached(ModAttachments.MAIN_QUEST_STAGE, st + 1);
        msg(p, "◆ 主线【" + s.title() + "】完成!奖励:" + s.rewardDesc()
                + (st + 1 < STAGES.length ? " → 下一阶段【" + STAGES[st + 1].title() + "】" : " —— 全线通关!"), Formatting.GOLD);
        sync(p);
    }

    /** 回发进度快照(界面打开/领奖后)。 */
    public static void sync(ServerPlayerEntity p) {
        int st = stage(p);
        boolean complete = st < STAGES.length && STAGES[st].check().apply(p);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                new com.yongye.network.MainQuestSyncPayload(st, complete, kills(p),
                        p.getAttachedOrElse(ModAttachments.MAIN_ELITE_KILLS, 0),
                        p.getAttachedOrElse(ModAttachments.MAIN_BOSS_KILLS, 0),
                        p.getAttachedOrElse(ModAttachments.MAIN_PAIN_SLAIN, false),
                        p.getAttachedOrElse(ModAttachments.MAIN_DRAGON_SLAIN, false)));
    }

    // ---------- 小工具 ----------
    public static int stage(ServerPlayerEntity p) { return p.getAttachedOrElse(ModAttachments.MAIN_QUEST_STAGE, 0); }
    private static long kills(ServerPlayerEntity p) { return p.getAttachedOrElse(ModAttachments.MAIN_KILLS, 0L); }

    private static boolean isBoss(net.minecraft.entity.LivingEntity e) {
        return e instanceof com.yongye.entity.AnubisEntity
                || e instanceof com.yongye.entity.FirePhoenixEntity
                || e instanceof com.yongye.entity.GiantCrabEntity
                || e instanceof com.yongye.entity.DeathMageEntity
                || e instanceof com.yongye.entity.ToroEnderDragonEntity;
    }

    private static int totalSkill(ServerPlayerEntity p) {
        long sum = p.getAttachedOrElse(ModAttachments.LEARNED_HEALTH, 0);
        for (SkillType t : SkillType.values()) sum += SkillEffectManager.getLearnedLevel(p, t);
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    private static int maxEnhance(ServerPlayerEntity p) {
        int best = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty()) best = Math.max(best, EquipmentEnhancer.getLevel(s));
        }
        return best;
    }

    private static int countItem(ServerPlayerEntity p, Item item) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getItem() == item) n += s.getCount();
        }
        return n;
    }

    private static void give(ServerPlayerEntity p, Item item, int count) {
        p.getInventory().offerOrDrop(new ItemStack(item, count));
    }

    private static void msg(ServerPlayerEntity p, String t, Formatting f) {
        p.sendMessage(Text.literal(t).formatted(f), false);
    }
}
