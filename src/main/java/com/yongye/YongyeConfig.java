package com.yongye;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 全局配置。文件位于 config/yongye.json,可在不改代码的前提下调平衡。
 */
public class YongyeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static YongyeConfig INSTANCE;

    // ============ 总开关 ============
    public boolean enableMobEnhancement = true;
    public boolean enableArmorHealthBonus = true;
    public boolean enableRandomLoot = true;

    // ============ 怪物基础增强(文档 5)============
    /** 最大生命倍率(在原版基础上 ADD_MULTIPLIED_BASE) */
    public double mobHealthMultiplier = 2.0;
    /** 攻击力倍率 */
    public double mobAttackMultiplier = 1.5;
    /** 移速倍率 */
    public double mobSpeedMultiplier = 1.15;
    /** 击退抗性附加值(0~1) */
    public double mobKnockbackResistanceAdd = 0.2;
    /** 感知/跟踪范围附加(格) */
    public double mobFollowRangeAdd = 16.0;
    /** 生成时随机携带正面药水效果的概率 */
    public double mobRandomPotionChance = 0.25;

    // ============ 套装血量加成(文档 12.1,单位:点最大生命)============
    public double setBonusLeather = 5;
    public double setBonusChain = 8;
    public double setBonusIron = 10;
    public double setBonusGold = 20;
    public double setBonusDiamond = 30;
    public double setBonusNetherite = 40;

    // ============ 技能书(文档 13)============
    public int skillBookMaxLevel = 65535;

    /** 属性技能书(攻击/护甲/恢复/闪避/反伤/抗性)掉落几率:普通怪 / 精英 / Boss。普通怪受永夜等级加成。 */
    public double skillBookDropChanceNormal = 0.02;
    public double skillBookDropChanceElite = 0.6;
    public double skillBookDropChanceBoss = 1.0;
    /** 合成到该结果等级时需要"生命结晶" */
    public int lifeCrystalThreshold = 10;
    /** 需要"生命核心" */
    public int lifeCoreThreshold = 100;
    /** 需要"灾变血核" */
    public int catastropheBloodCoreThreshold = 1000;

    // ============ 精英怪(文档第 6 章)============
    public boolean enableElite = true;
    /** 怪物精英化的基础概率 */
    public double eliteChance = 0.04;
    /** 精英在"基础增强之上"再追加的属性倍率/数值 */
    public double eliteHealthMultiplier = 3.0;
    public double eliteAttackMultiplier = 2.0;
    public double eliteSpeedMultiplier = 1.3;
    public double eliteKnockbackResistanceAdd = 0.6;
    public double eliteFollowRangeAdd = 24.0;
    /** 精英骷髅每秒箭数 / 精英女巫每秒投掷数 */
    public int eliteSkeletonArrowsPerSecond = 5;
    public int eliteWitchPotionsPerSecond = 5;
    /** 精英瞬移:与目标水平距离超过该值且冷却结束时触发(格);冷却(tick) */
    public double eliteTeleportTriggerDistance = 16.0;
    public int eliteTeleportCooldownTicks = 100;
    /** 精英主动感知玩家的半径(没目标时锁定此范围内最近玩家) */
    public double eliteSenseRadius = 48.0;
    /** 精英女巫支援:每隔多少 tick 治疗/增益一次附近怪物 */
    public int eliteWitchSupportIntervalTicks = 60;

    // ============ Boss 翻倍(文档第 7 章)============
    public boolean enableBoss = true;
    public double bossHealthMultiplier = 2.0;
    public double bossAttackMultiplier = 2.0;
    public double bossSpeedMultiplier = 1.2;
    public double bossKnockbackResistanceAdd = 0.4;
    /** 掉落奖励翻倍系数 */
    public double bossDropMultiplier = 2.0;

    // —— Boss 专属机制(文档 7.1)——
    public boolean enableBossAbilities = true;
    public double bossAggroRange = 48.0;       // 锁定/能力作用范围
    public double bossEnrageThreshold = 0.5;   // 血量低于此比例进入狂暴
    public int bossSummonIntervalTicks = 200;  // 召唤间隔
    public int bossSummonCount = 3;            // 每次召唤数量
    public int bossSummonMaxNearby = 12;       // 周围援军上限
    public int bossShockIntervalTicks = 120;   // 冲击波间隔
    public double bossShockRadius = 6.0;       // 冲击波半径
    public double bossShockDamage = 8.0;       // 冲击波伤害

    /** 摧毁灾厄核心是否赎夜(降低一级永夜)。 */
    public boolean coreDestroyRedeems = true;

    // —— 长门(佩恩)Boss ——
    public double painBossMaxHealth = 400.0;
    public double painBossAttack = 12.0;
    public double painBossArmor = 10.0;
    public int painAbilityIntervalTicks = 120;   // 技能间隔
    public double painPushRadius = 12.0;          // 神罗天征半径
    public double painPushDamage = 8.0;
    public double painPullRadius = 16.0;          // 万象牵引半径
    public double painDevastationRadius = 8.0;    // 地爆天星爆心半径
    public double painDevastationDamage = 16.0;
    public double painRebirthThreshold = 0.3;     // 轮回天生触发血量比例
    public boolean painNaturalSpawn = true;       // 是否允许长门作为终局事件自然降临
    public int painSpawnMinNightfall = 4;          // 触发自然降临的最低永夜等级(IV灾变)
    public double painNaturalSpawnChance = 0.25;  // 每次检定的降临概率
    public int painNaturalCheckIntervalTicks = 1200; // 检定间隔(默认60秒)
    public int painDeathRedeemLevels = 2;          // 击败长门降低的永夜级数

    // ============ 随机掉落(文档 11.2 普通怪概率)============
    public double lootChanceCommon = 0.60;
    public double lootChanceUseful = 0.25;
    public double lootChanceRare = 0.10;
    public double lootChanceEpic = 0.04;
    public double lootChanceGodly = 0.01;
    /** 普通怪掉落"生命碎片"的概率(文档 15.1) */
    public double lifeShardDropChance = 0.05;
    /** 仅当怪物被玩家击杀才触发随机掉落 */
    public boolean lootRequirePlayerKill = true;

    // ============ 永夜灾变(文档第 10 章)============
    public boolean enableNightfall = true;
    /** 永夜 I~V 对应的精英概率倍率(作用于 eliteChance) */
    public double[] nightfallEliteChanceMultiplier = {1.0, 1.5, 2.0, 3.0, 5.0, 8.0};
    /** 永夜 I~V 对应的怪物锁定半径(格) */
    public double[] nightfallLockRadius = {0.0, 16.0, 24.0, 32.0, 40.0, 48.0};

    // —— 灾厄核心(文档 9.3 / 第 14、15 章)——
    public boolean enableCatastropheCore = true;
    public int coreMaxActive = 3;                 // 同时存在的自然核心上限
    public int coreMinNightfall = 2;              // 永夜达到此级才会自然生成
    public double coreNaturalSpawnChance = 0.04;  // 每 2 秒判定一次的生成几率
    public int coreSpawnDistanceMin = 32;
    public int coreSpawnDistanceMax = 64;
    public int coreMobSpawnRadius = 28;           // 玩家进入此半径,核心开始刷怪
    public int coreMobMaxNearby = 6;              // 核心周围怪上限
    public double coreMobSpawnChance = 0.5;       // 每次判定的刷怪几率

    // ============ 追杀 AI(文档第 8 章)============
    public boolean enablePursuit = true;
    /** 追杀逻辑作用半径(只处理玩家附近的怪) */
    public double pursuitRadius = 32.0;
    /** 挖墙冷却(tick) */
    public int digCooldownTicks = 16;
    /** 各档可破坏方块的最大硬度:普通 / 精英 / Boss */
    public double digMaxHardnessNormal = 0.8;   // 泥土沙砾玻璃树叶
    public double digMaxHardnessElite = 3.2;    // 圆石石头木板木门
    public double digMaxHardnessBoss = 60.0;    // 含黑曜石
    /** 爬墙竖直速度 */
    public double climbSpeed = 0.22;

    // ============ 随机任务(文档第 9 章)============
    public boolean enableQuests = true;
    /** 自动派发任务的间隔(tick),默认 6 分钟 */
    public int questIntervalTicks = 7200;
    /** 任务限时(tick),默认 3 分钟 */
    public int questTimeLimitTicks = 3600;
    /** 开局宽限期(tick),此前不派发任务,默认 5 分钟 */
    public int questStartGraceTicks = 6000;
    /** 派发猎杀精英任务所需的最低永夜等级(此前只派可达成的逃离/存活) */
    public int questHuntEliteMinNightfall = 2;

    // ============ 背包神器(文档第 14 章)============
    public boolean enableArtifacts = true;
    public int artifactMaxLevel = 6;

    // ============ 高血量反制(文档第 17 章)============
    public boolean enableHighHpCounter = true;
    /** Boss 攻击附加的"最大生命百分比"伤害 */
    public double bossPercentDamage = 0.02;
    /** 精英攻击附加的最大生命百分比伤害 */
    public double elitePercentDamage = 0.01;
    /** Boss/精英攻击附加的真实(无视护甲)伤害 */
    public double bossTrueDamage = 6.0;
    public double eliteTrueDamage = 2.0;
    /** 命中时施加禁疗的概率与时长(tick) */
    public double healBlockChance = 0.25;
    public int healBlockDurationTicks = 100;

    public static YongyeConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("yongye.json");
        try {
            if (Files.exists(path)) {
                String json = Files.readString(path);
                INSTANCE = GSON.fromJson(json, YongyeConfig.class);
                if (INSTANCE == null) INSTANCE = new YongyeConfig();
            } else {
                INSTANCE = new YongyeConfig();
                save();
            }
        } catch (IOException | RuntimeException e) {
            Yongye.LOGGER.error("[亡途荒夜] 读取配置失败,使用默认值", e);
            INSTANCE = new YongyeConfig();
        }
    }

    public static void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("yongye.json");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(INSTANCE == null ? new YongyeConfig() : INSTANCE));
        } catch (IOException e) {
            Yongye.LOGGER.error("[亡途荒夜] 写入配置失败", e);
        }
    }
}
