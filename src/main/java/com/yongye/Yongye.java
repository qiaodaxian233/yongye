package com.yongye;

import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModBlocks;
import com.yongye.registry.ModComponents;
import com.yongye.registry.ModItemGroups;
import com.yongye.registry.ModItems;
import com.yongye.registry.ModSounds;
import com.yongye.registry.ModRecipes;
import com.yongye.system.ArmorHealthHandler;
import com.yongye.system.ArtifactManager;
import com.yongye.system.BonusXpHandler;
import com.yongye.system.BossHandler;
import com.yongye.system.EliteCombatAI;
import com.yongye.system.BossAbilityHandler;
import com.yongye.system.CatastropheCoreManager;
import com.yongye.system.EliteHandler;
import com.yongye.system.EndDragonHandler;
import com.yongye.system.HardcoreSurvivalHandler;
import com.yongye.system.HimJumpscareHandler;
import com.yongye.system.HighHpCounterHandler;
import com.yongye.system.LootHandler;
import com.yongye.system.MobBossHandler;
import com.yongye.system.MobEnhancementHandler;
import com.yongye.system.ModCommands;
import com.yongye.system.NightfallManager;
import com.yongye.system.PainBossHandler;
import com.yongye.system.PlayerSkillManager;
import com.yongye.system.PursuitHandler;
import com.yongye.system.QuestManager;
import com.yongye.system.SkillEffectManager;
import com.yongye.system.WeaponCombatHandler;
import com.yongye.system.WeaponGuardHandler;
import com.yongye.system.WeaponSkillFx;
import com.yongye.system.WeaponSkillManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 《夜蚀》主入口（m203 由《永夜》改名；内部 id/包名/命令仍为 yongye，存档兼容，勿改）。
 * Phase 0: 工程骨架 + 配置 + 注册框架。
 * Phase 1: 怪物基础增强 / 套装血量 / 技能书(V1~V10亿,可配 skillBookMaxLevel,含同级合成) / 随机掉落 / 超稀有材料。
 * Phase 2: 精英怪系统 / Boss 翻倍。
 * Phase 3: 永夜灾变 / 随机任务 / 追杀(锁定·挖墙·爬墙)。
 * Phase 4: 背包神器 / 高血量反制 / 指令。
 */
public class Yongye implements ModInitializer {
    public static final String MOD_ID = "yongye";
    public static final Logger LOGGER = LoggerFactory.getLogger("夜蚀");

    @Override
    public void onInitialize() {
        LOGGER.info("[夜蚀] 世界已经坏掉了，正在初始化……");

        // 配置
        YongyeConfig.load();

        // 解除原版属性 1024 硬上限(否则血量书/装备强化堆到一千多就失效)
        raiseAttributeCaps();

        // 注册层
        ModComponents.init();
        com.yongye.registry.ModScreens.init();
        ModAttachments.init();
        ModBlocks.init();
        com.yongye.registry.ModArmorMaterials.init();   // m265 夜蚀盔甲材质(须在 ModItems 之前)
        ModItems.init();
        ModItemGroups.init();
        ModRecipes.init();
        com.yongye.registry.ModEntities.init();   // 自定义实体(末影龙 BOSS,GeckoLib 渲染)

        // 玩法系统(Phase 1~2)
        // 注意 ENTITY_LOAD 监听器注册顺序:基础增强 → Boss 标记 → 精英化
        com.yongye.system.ProgressionManager.register();
        com.yongye.system.EquipmentEnhancer.registerDurabilityGuard(); // m395 负耐久自愈(崩报回修,单点注册)
        com.yongye.system.ClassManager.register();
        com.yongye.system.MonkSystem.register();
        com.yongye.system.ItemCleanupHandler.register();
        // m144 停掉磁吸挂载(Sophisticated Backpacks 自带磁吸,本 mod LootMagnetHandler 重复):
        // 直接不挂载,彻底失效,不受老存档 yongye.json 里 enableLootMagnet 残留 true 的影响。要恢复就取消注释。
        // com.yongye.system.LootMagnetHandler.register();
        com.yongye.system.TalentManager.register();
        com.yongye.system.ClassSkillHandler.register();
        MobEnhancementHandler.register();
        BossHandler.register();
        BossAbilityHandler.register();
        MobBossHandler.register();
        BonusXpHandler.register();
        com.yongye.system.StartingKitHandler.register();
        com.yongye.system.WelcomeBookHandler.register();
        com.yongye.system.NightfallHordeHandler.register();
        com.yongye.system.WildDragonSpawnHandler.register();   // m165 野生末影龙:第N天起高空几率刷出
        com.yongye.system.AnubisSpawnHandler.register();       // m175 阿努比斯:第N天起地表几率降临
        com.yongye.system.CustomMobSpawnHandler.register();    // m176 五只新怪自然刷怪(BOSS事件+精英压力)
        com.yongye.system.NightBlightHandler.register();       // m212 夜蚀群系:侵蚀转化+全生物敌化+侵蚀掉落
        com.yongye.system.SummonFriendlyFireHandler.register(); // m299 召唤物免友伤(m453 起仅暗影分身)
        com.yongye.system.SummonAssistHandler.register();       // m320 召唤物协同集火(主人打谁它打谁+护主)
        com.yongye.system.MainQuestLine.register();             // m328 主线任务书(击杀计数/龙讨伐广播)
        com.yongye.system.NewGamePlusManager.register();        // m330 永夜+(二周目,杀龙开启)
        com.yongye.system.AntiCheeseHandler.register();         // m334 反卡BUG(悬空卡怪/泡水躲怪)
        com.yongye.system.BossGuardHandler.register();          // m304 皮肤BOSS格挡条+攻击平衡
        com.yongye.system.CandleDimension.register();           // m305 烛之维度:烛块门/传送
        com.yongye.system.CandleSpawnHandler.register();        // m305 烛之维度:百倍刷怪+实体闸
        com.yongye.system.SurvivalRankHandler.register();
        com.yongye.system.NightfallWeatherHandler.register();
        com.yongye.system.NightfallSkyWeatherHandler.register();
        com.yongye.system.WarlockBoltHandler.register();           // m450 术士魔法弹弹道(飞行法术球)   // m435 原版雨雪雷随永夜加深(与天象分工:那边造事件,这边调原版节律)
        com.yongye.system.NightfallVisionHandler.register();
        EliteHandler.register();
        EliteCombatAI.register(); // m309 精英战斗AI:跳劈/走位/自爆翻倍/逃跑回归/跳搭
        ArmorHealthHandler.register();
        LootHandler.register();
        com.yongye.system.ProtectScrollHandler.register();    // m159 强化保护卷:掉落 + 杀怪兑换

        // 玩法系统(Phase 3:永夜 / 追杀 / 任务)
        NightfallManager.register();
        com.yongye.system.DifficultyManager.register();
        com.yongye.system.WorldDoomManager.register();        // m155 世界崩塌(全局持久 ×100)
        com.yongye.system.CreativeWatchHandler.register();     // m155 创造模式监听(反作弊 + 触发崩塌)
        com.yongye.system.PlayerUpkeepHandler.register();
        PursuitHandler.register();
        QuestManager.register();
        CatastropheCoreManager.register();
        PainBossHandler.register();

        // 玩法系统(Phase 4:神器 / 高血量反制 / 指令)
        ArtifactManager.register();
        HighHpCounterHandler.register();
        SkillEffectManager.register();
        WeaponCombatHandler.register();
        com.yongye.system.ForeignDamageFilterHandler.register(); // m189 怪物伤害来源检测(只认原版+本模组武器)
        WeaponGuardHandler.register(); // m259 武器右键格挡(在打击感之前注册:挡下的伤害不出打击感;在伤害过滤/职业受击之后:坦克折减先行)
        com.yongye.system.ExecuteHandler.register(); // m270 处决斩杀(取消原伤害+嵌套补致死刀,须在打击感之前)
        com.yongye.system.BlightSetHandler.register(); // m272 夜蚀共鸣(套装百分比加成)
        com.yongye.system.CombatFxHandler.register(); // m239 沉浸式战斗手感(必须排在伤害过滤之后:被取消的伤害不出打击感)
        com.yongye.system.ComboHandler.register(); // m273 连击计数器(同口径:排在过滤/格挡之后,被取消的伤害不涨连击)
        com.yongye.system.KillStatsHandler.register(); // m288 战况看板(击杀统计/阶段预告下发)
        com.yongye.system.SkillCdSyncHandler.register(); // m346 技能CD常显HUD(冷却剩余每10t下发)
        com.yongye.system.NewbieGuideHandler.register(); // m348 新手前3天引导(选职→学书→强化→找核心)
        com.yongye.system.FirstTimeHintHandler.register(); // m420 首次永夜/神器/精英各一句机制说明
        com.yongye.system.DailyBountyHandler.register(); // m364 每日悬赏(每日3张/连击加成/完成自动发奖)
        com.yongye.system.HuntMedalHandler.register(); // m366 猎杀勋章(击杀里程碑三选一,独立永久成长线)
        com.yongye.system.VaultManager.register(); // m357 材料仓库自动入库(每5s扫背包区,热栏豁免)
        com.yongye.system.BossRageHandler.register(); // m274 BOSS 半血狂暴(阶段转换演出+变招)
        com.yongye.system.AutoScrollHandler.register(); // m276 自动卷轴执行器(自动强化/自动吃书)
        WeaponSkillFx.register(); // m255 武器技能特效编排器(混沌斩/吞噬/终焉的多帧大演出)
        WeaponSkillManager.init();
        com.yongye.system.LootCrateHandler.init(); // 战利品宝箱掉落(m245)
        HardcoreSurvivalHandler.register();
        EndDragonHandler.register();  // m183 末地末影龙强化(10亿血/三命/脱战回血)
        ModSounds.init();
        HimJumpscareHandler.register();
        com.yongye.network.YongyeNet.register();
        ModCommands.register();

        // 玩家加入 / 重生时,根据持久化的累计等级重新应用血量强化
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PlayerSkillManager.applyHealthModifier(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            PlayerSkillManager.applyHealthModifier(newPlayer);
            com.yongye.system.ClassManager.applyClasses(newPlayer); // 重生先刷职业/武僧的生命上限
            newPlayer.setHealth(newPlayer.getMaxHealth());          // 即时尽量回满
            // 兜底:神器/强化护甲/携带武器等生命上限可能晚一两 tick 才应用,开 2 秒满血窗口持续补满
            com.yongye.system.PlayerUpkeepHandler.scheduleRespawnHeal(newPlayer);
            // m265:归还死亡时截走的灵魂绑定物品(附件 copyOnDeath 已随死亡复制到新玩家)
            java.util.List<net.minecraft.item.ItemStack> stash =
                    newPlayer.getAttachedOrElse(com.yongye.registry.ModAttachments.SOULBOUND_STASH, java.util.List.of());
            if (!stash.isEmpty()) {
                for (net.minecraft.item.ItemStack s : stash) {
                    if (!s.isEmpty()) newPlayer.getInventory().offerOrDrop(s.copy());
                }
                newPlayer.setAttached(com.yongye.registry.ModAttachments.SOULBOUND_STASH, new java.util.ArrayList<>());
                newPlayer.sendMessage(net.minecraft.text.Text.literal("【夜蚀】灵魂契约生效——你的夜蚀装备回到了身边")
                        .formatted(net.minecraft.util.Formatting.LIGHT_PURPLE), false);
            }
        });

        LOGGER.info("[夜蚀] 初始化完成。活到天亮就是胜利。");
    }

    /** 把某个属性的硬上限抬到 max(原版默认夹在 1024,会让高血量/高强化失效)。 */
    private static void raiseCap(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> entry, double max) {
        if (entry.value() instanceof net.minecraft.entity.attribute.ClampedEntityAttribute c) {
            ((com.yongye.mixin.ClampedEntityAttributeAccessor) (Object) c).yongye$setMaxValue(max);
        }
    }

    /** 解除核心属性的 1024 硬上限。攻速 1024 已够用,不动。 */
    private static void raiseAttributeCaps() {
        // m220:上限=无符号 64 位整数最大值(作者点名)。18446744073709551615 在 double 下即 2^64
        // ≈1.8446744e19;属性是 double、血量是 float,大数下有精度粒度但功能正常,显示走 K/M/B/T/Qa/Qi。
        double cap = 1.8446744073709552E19;
        raiseCap(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH, cap);
        raiseCap(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE, cap);
        raiseCap(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR, cap);
        raiseCap(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS, cap);
        LOGGER.info("[夜蚀] 已抬高属性上限(原版默认夹在 1024)");
    }
}
