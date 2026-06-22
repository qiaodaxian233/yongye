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
    public double mobHealthMultiplier = 3.0;
    /** 攻击力倍率 */
    public double mobAttackMultiplier = 2.2;
    /** 移速倍率 */
    public double mobSpeedMultiplier = 1.2;
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
    public double skillStealChancePerLevel = 0.005; // 抢夺技能每级 +0.5% 夺取概率
    public double skillStealMaxChance = 0.9;         // 抢夺概率上限

    /** 属性技能书(攻击/护甲/恢复/闪避/反伤/抗性)掉落几率:普通怪 / 精英 / Boss。普通怪受永夜等级加成。 */
    public double skillBookDropChanceNormal = 0.008;
    public double skillBookDropChanceElite = 0.3;
    public double skillBookDropChanceBoss = 1.0;
    public int skillBookEarlyGameDays = 6;        // 前几个游戏日视为前期
    public double skillBookEarlyGameChance = 0.1; // 前期技能书爆率压制系数
    /** 合成到该结果等级时需要"生命结晶" */
    public int lifeCrystalThreshold = 10;
    /** 需要"生命核心" */
    public int lifeCoreThreshold = 100;
    /** 需要"灾变血核" */
    public int catastropheBloodCoreThreshold = 1000;

    // ============ 精英怪(文档第 6 章)============
    public boolean enableElite = true;
    /** 精英怪持续发光(部分渲染mod如AcceleratedRendering处理实体描边有bug,默认关防崩) */
    public boolean eliteGlowing = false;
    /** 精英怪光环特效:周身喷幽蓝魂火粒子(纯服务端 spawnParticles,不走发光描边,无渲染mod崩溃风险) */
    public boolean eliteAuraEffect = true;
    /** 光环特效的发射间隔(tick),越小越密集越费;默认 4(约每秒 5 次) */
    public int eliteAuraIntervalTicks = 4;
    /** 怪物精英化的基础概率 */
    public double eliteChance = 0.04;
    /** 精英在"基础增强之上"再追加的属性倍率/数值 */
    public boolean eliteCanDisarm = true;          // 精英命中玩家时概率夺走主手武器
    public double eliteDisarmChance = 0.12;        // 缴械概率(每次精英命中)
    public int eliteDisarmCooldownTicks = 200;     // 同一玩家两次被缴械的冷却(tick)
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

    // ============ 普通怪 BOSS 版(m60:第 N 天起,普通怪低概率"BOSS化")============
    public boolean enableMobBoss = true;
    public int mobBossStartDay = 10;             // 第几天起开始刷怪物BOSS(早于此天不刷)
    public double mobBossChance = 0.008;         // 每只普通怪生成时BOSS化的概率(低,作偶发精英BOSS)
    public double mobBossHealthMultiplier = 12.0;
    public double mobBossAttackMultiplier = 4.0;
    public double mobBossSpeedMultiplier = 1.25;
    public double mobBossKnockbackResistanceAdd = 0.9;
    public double mobBossScaleMultiplier = 1.6;  // 体型放大(更像Boss);1.0=不放大。靠 GENERIC_SCALE 属性
    public double mobBossBarRadius = 48.0;        // 多远内的玩家能看到这只BOSS的血条

    // ============ 精英+ 额外经验(m62:加快升级)============
    public boolean enableBonusXp = true;
    public int xpBonusElite = 25;                 // 精英怪死亡额外经验
    public int xpBonusMobBoss = 150;              // 怪物BOSS版死亡额外经验
    public int xpBonusVanillaBoss = 200;          // 原版Boss(凋灵/监守者等)死亡额外经验(原版自带经验之外再加)
    public int xpBonusPain = 500;                 // 长门·佩恩死亡额外经验

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
    public double painBossMaxHealth = 20000.0;
    public double painBossAttack = 2000.0;
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
    public int painSpawnMinDay = 5;                // 自然降临的最低游戏天数(早期不刷)
    public double painNaturalSpawnChance = 0.25;  // 每次检定的降临概率
    public int painNaturalCheckIntervalTicks = 1200; // 检定间隔(默认60秒)
    public int painDeathRedeemLevels = 2;          // 击败长门降低的永夜级数

    // ===== 装备无限强化 / 品质 =====
    public boolean enableEquipmentEnhance = true;
    public double enhanceDamagePerLevel = 0.5;     // 武器每级 +攻击力
    public double enhanceArmorPerLevel = 0.3;      // 盔甲每级 +护甲
    public double enhanceToughnessPerLevel = 0.1;  // 盔甲每级 +护甲韧性
    public double enhanceHealthPerLevel = 1.0;     // 盔甲每级 +最大生命
    public int enhanceDurabilityPerLevel = 8;      // 每级 +最大耐久
    public double enhanceCritBonusMultiplier = 0.75; // 暴击额外伤害 = 强化攻击加成 × 此值
    public double enhanceHybridDamageFraction = 0.5; // 攻防双修武器(如镇魂)的攻击成长 = 武器攻击/级 × 此值(攻击加得少些)
    public int enhanceShardValue = 1;              // 生命碎片 = +1 级
    public int enhanceCrystalValue = 10;           // 生命结晶 = +10 级
    public int enhanceCoreValue = 100;             // 生命核心 = +100 级
    public int enhanceBloodCoreValue = 1000;       // 灾变血核 = +1000 级

    // ===== 主动武器技能(按品质解锁,按键触发) =====
    public boolean enableWeaponSkills = true;
    public int skillSlashCooldown = 160;       // 混沌斩 冷却(tick,8s)
    public double skillSlashDamage = 8.0;
    public double skillSlashDamagePerLevel = 0.4;
    public double skillSlashRange = 6.0;
    public int skillDevourCooldown = 300;      // 深渊吞噬 冷却(15s)
    public double skillDevourDamage = 6.0;
    public double skillDevourDamagePerLevel = 0.3;
    public double skillDevourRadius = 7.0;
    public double skillDevourHealRatio = 0.4;  // 伤害转治疗比例
    public double skillDevourHealMaxPct = 0.25; // 单次吸血治疗上限(最大生命百分比)
    public int skillFinalityCooldown = 900;    // 终焉降临 冷却(45s)
    public double skillFinalityDamage = 20.0;
    public double skillFinalityDamagePerLevel = 0.6;
    public double skillFinalityRadius = 9.0;

    // ===== 怪物随进度递增(血量/攻击跟着玩家变强) =====
    public boolean enableMobScaling = true;
    public double mobScalingPerNightfall = 0.8;       // 每级永夜 +60% 血
    public double mobScalingPerDay = 0.06;            // 每个游戏日 +4% 血
    public int mobScalingMaxDays = 100;               // 计入天数上限
    public double mobScalingPlayerHealthFactor = 0.5; // 按附近玩家「超出20的最大生命」比例的 50% 给怪加血
    public double mobScalingAttackRatio = 0.4;        // 攻击按血量缩放的 30% 同步提升
    public double mobScalingMaxMultiplier = 60.0;     // 缩放倍率上限

    // ===== 防卡死:全局怪量预算 + 传送限流 =====
    public int globalMaxHostilesNearby = 60;          // 玩家附近敌对生物总数上限,超了本 mod 不再额外刷怪
    public double globalHostileRadius = 28.0;         // 统计半径
    public int pursuitMaxTeleportsPerTick = 3;        // 追杀/嵌墙传送每 tick 上限

    // ===== HIM 突脸惊吓(无伤害) =====
    public boolean enableHim = true;
    public int himCheckIntervalTicks = 600;
    public double himChance = 0.04;
    public int himDurationTicks = 35;
    public boolean himNightOrCaveOnly = true;
    public double himSpawnDistance = 2.2;
    public int himBlindnessTicks = 20;        // 出现前的短暂失明铺垫(tick);越短越"突然"。原 100(5秒),20≈1秒
    public boolean himTeleportFlash = true;   // 登场时喷紫色末影门粒子(传送闪现感)

    // ===== 硬核开局生存包 =====
    public boolean enableHardcoreSurvival = true;
    public boolean forceSurvival = true;          // 创造模式自动切回生存(反作弊)
    public boolean forceSurvivalExemptOp = true;  // OP 豁免(便于管理/搭建)
    public boolean hcNoSleepSkip = true;
    public double hcHungerDrainPerSecond = 0.35;
    public boolean hcNightAmbush = true;
    public double hcAmbushChance = 0.35;
    public int hcAmbushIntervalTicks = 200;
    public double hcAmbushRadius = 12.0;
    public int hcAmbushMaxNearby = 8;
    public boolean hcCaveDanger = true;
    public int hcCaveYThreshold = 50;
    public double hcCaveSpawnChance = 0.5;
    public double hcCaveDebuffChance = 0.15;
    public boolean hcResourceHarder = true;
    public double hcResourceFatigueChance = 0.5;
    public boolean hcMiningSlowdown = false;       // 已关闭:不再限制砍树/挖矿速度
    public double hcMiningSpeedMultiplier = 0.3;   // 挖掘速度系数(0.3≈耗时3.3倍;越小越慢)
    public boolean hcMiningSlowAll = false;        // true=所有方块减速;false=只木头/石头/矿物

    // ============ 随机掉落(文档 11.2 普通怪概率)============
    public double lootChanceCommon = 0.35;
    public double lootChanceUseful = 0.15;
    public double lootChanceRare = 0.06;
    public double lootChanceEpic = 0.02;
    public double lootChanceGodly = 0.008;
    /** 普通怪掉落"生命碎片"的概率(文档 15.1) */
    public double lifeShardDropChance = 1.0;     // 生命碎片:普通怪必掉
    public double lifeCrystalDropChance = 0.20;  // 生命结晶:普通怪 20%(精英翻倍)
    public double lifeCoreDropChance = 0.50;     // 生命核心:仅精英(普通怪绝不掉)
    public double bloodCoreDropChanceElite = 0.10; // 灾厄血核:仅精英,小概率
    public double classWeaponDropChanceElite = 0.04; // 精英掉落职业专属武器(随机职业)的几率
    /** 仅当怪物被玩家击杀才触发随机掉落 */
    public boolean lootRequirePlayerKill = true;

    // ============ 永夜灾变(文档第 10 章)============
    public boolean enableNightfall = true;
    /** 永夜 I~V 对应的精英概率倍率(作用于 eliteChance) */
    public double[] nightfallEliteChanceMultiplier = {1.0, 1.5, 2.0, 3.0, 5.0, 8.0};
    /** 永夜 I~V 对应的怪物锁定半径(格) */
    public double[] nightfallLockRadius = {0.0, 16.0, 24.0, 32.0, 40.0, 48.0};
    /** 永夜等级上限(V5 不再是终点;失败可继续升,默认 99 近似"无尽") */
    public int nightfallMaxLevel = 99;
    /** 永夜超过 V5 后,每多一级给怪物叠加的最大生命倍率(线性、无额外封顶):V6=+50%、V7=+100%… */
    public double nightfallBeyondHpPerLevel = 0.5;

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

    // —— 搭方块爬塔(m60:反制玩家用单格高塔躲在怪够不着的高处)——
    public boolean mobPillarUp = true;            // 怪在玩家正上方够不着时,原地搭方块柱子往上爬
    public double pillarMinHeightDiff = 3.0;      // 玩家高出怪多少格才触发搭塔
    public double pillarMaxHorizontal = 2.5;      // 怪与玩家水平距离小于此值才搭(即玩家近乎正上方)
    public int pillarCooldownTicks = 8;           // 每搭一格的间隔(tick),越小爬得越快
    public String pillarBlock = "minecraft:cobblestone"; // 搭塔用的方块 id(默认圆石,玩家可挖掉)
    /** 卡住兜底:船卡/水/岩浆/挖不动的墙后,传送到玩家身边 */
    public boolean pursuitTeleportStuck = true;
    public int pursuitStuckTicks = 60;          // 持续无进展多久判定卡住(tick,3s)
    public double pursuitTeleportRadius = 6.0;  // 传送落点距玩家半径
    public double pursuitTeleportMinDist = 3.0; // 距玩家小于此距离不传送(已贴脸)

    // ============ 随机任务(文档第 9 章)============
    // ===== 职业系统 =====
    public int classLevel1 = 50;             // 第一职业所需等级
    public int classLevel2 = 100;            // 第二职业所需等级
    public double classBookDropChance = 0.15; // 精英掉落职业书概率

    // ===== 天赋树系统 =====
    public boolean enableTalents = true;
    public int talentStartLevel = 50;        // 从该等级起,每升 1 级发放天赋点
    public int talentPointsPerLevel = 1;     // 每级发放的天赋点数

    // ===== 职业专属技能(m41,触发型)=====
    public boolean enableClassSkills = true;
    // 战士
    public double warriorLifestealFraction = 0.15;   // 近战命中按攻击力比例回血
    public double warriorExecuteThreshold = 0.20;     // 目标生命比例低于此触发斩杀
    public double warriorExecuteBonusFraction = 0.5;  // 斩杀追加伤害 = 目标最大生命 × 此值
    // 坦克
    public double tankTauntRadius = 12.0;
    public int tankTauntIntervalTicks = 40;
    public int tankShieldAmplifier = 1;               // 吸收等级(0=吸收I=4心)
    public double tankShieldReflect = 4.0;            // 持磐盾格挡时的反震伤害
    public double tankTrueDamageReduction = 0.15;     // 坦克真·百分比减伤(0~0.9;0=关。经 LivingEntity mixin 削减最终伤害)
    // 刺客
    public double assassinBackstabBonus = 4.0;
    public double assassinDodgeChance = 0.20;
    public int assassinUncombatTicks = 100;           // 脱战多少 tick 后获得加速
    public int assassinSprintAmplifier = 1;
    public double assassinCritChance = 0.20;          // 刺客职业暴击概率(持影刺再+15%)
    public double assassinCritBonusFraction = 0.5;    // 暴击追加伤害 = 攻击力 × 此值
    // 术士
    public double warlockAoeHpCost = 6.0;             // 潜行攻击消耗的生命
    public double warlockAoeRadius = 4.0;
    public double warlockAoeDamage = 8.0;
    // 武僧
    public int monkComboWindowTicks = 40;
    public double monkComboBonusPerHit = 1.0;
    public int monkComboMaxStacks = 5;
    public double monkDisarmChance = 0.15;
    public boolean monkWeaponDurabilityPenalty = true; // 武僧持武器攻击额外耗耐久(等效×2)
    // 剑客
    public double swordsmanWaveRange = 4.0;
    public double swordsmanWaveDamage = 5.0;
    public double swordsmanParryReflect = 6.0;

    // ===== 开局选职(m43)=====
    public boolean enableStartingClassSelect = true;   // 新玩家出生弹出选职界面
    public boolean startingClassGiveWeapon = true;     // 选职附赠该职业专属武器(默认开)

    // ===== 职业大招(m47,主动技能,默认 X 键)=====
    public boolean enableClassUltimate = true;
    public long ultimateCooldownTicks = 600;          // 大招冷却(默认30秒)
    // 战士 旋风斩
    public double ultWarriorRadius = 5.0;
    public double ultWarriorDamage = 12.0;
    // 坦克 不动如山
    public int ultTankDurationTicks = 200;
    public double ultTankRadius = 12.0;
    // 刺客 影遁
    public int ultAssassinDurationTicks = 120;
    // 术士 灭世
    public double ultWarlockHpCost = 6.0;
    public double ultWarlockRadius = 6.0;
    public double ultWarlockDamage = 16.0;
    // 武僧 百裂拳
    public double ultMonkRadius = 4.0;
    public double ultMonkDamage = 8.0;
    // 剑客 万剑归一
    public double ultSwordsmanRange = 6.0;
    public double ultSwordsmanDamage = 14.0;

    // ===== 时间进度系统 =====
    public boolean enableProgression = true;
    public boolean firstDayLong = true;          // 第一天白天放慢
    public double firstDayMinutes = 24.0;        // 第一天白天目标时长(分钟)
    public boolean newbieProtectDay1 = true;     // 第一天白天新手保护(不刷额外怪)
    public int eliteStartDay = 3;                // 第3天起才有精英
    public int eliteBoostDay = 5;                // 第5天起精英概率大增
    public double eliteEarlyMultiplier = 0.3;    // 第3~4天:精英概率×0.3(小概率)
    public double eliteBoostMultiplier = 1.65;   // 第5天起:精英概率×1.65(+65%)
    public int evolutionEveryDays = 10;          // 每10天进化一次
    public double evolutionPerStage = 0.5;       // 每阶段怪物强度 +50%
    public int mobDigStartDay = 5;               // 第5天起怪物才会挖方块

    public boolean enableQuests = true;
    /** 自动派发任务的间隔(tick),默认 6 分钟 */
    public int questIntervalTicks = 7200;
    /** 任务限时(tick),默认 3 分钟 */
    public int questTimeLimitTicks = 1800;   // 限时缩短(原3600)
    /** 开局宽限期(tick),此前不派发任务,默认 5 分钟 */
    public int questStartGraceTicks = 6000;
    /** 派发猎杀精英任务所需的最低永夜等级(此前只派可达成的逃离/存活) */
    public int questHuntEliteMinNightfall = 2;
    public int questHuntEliteCount = 3;       // 猎杀任务需击杀的精英数(随永夜+)
    public int questSlayCount = 20;           // 屠戮任务需击杀的怪物数(随永夜+)
    public double questFleeDistance = 120.0;  // 逃离任务所需距离(随永夜+)
    public double questPlayerScaling = 0.5;   // 每多一名在线玩家,任务目标量倍率增量

    // ============ 背包神器(文档第 14 章)============
    public boolean enableArtifacts = true;
    public int artifactMaxLevel = 6;

    // ============ 材料兑换(m66:背包按钮,10 碎片→结晶→核心→血核,等值兑换)============
    public boolean enableMaterialExchange = true;

    // ============ 开局赠礼(m67:每人首次进入发一个背包)============
    public boolean giveStartingBackpack = true;
    /** 开局赠送的物品 id。默认 Sophisticated Backpacks 的下界合金背包;软依赖:未装该 mod 则自动跳过。 */
    public String startingBackpackItem = "sophisticatedbackpacks:netherite_backpack";

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
            Yongye.LOGGER.error("[永夜] 读取配置失败,使用默认值", e);
            INSTANCE = new YongyeConfig();
        }
    }

    /** 重置为默认配置并写盘(/yongye config reset 调用)。 */
    public static void reset() {
        INSTANCE = new YongyeConfig();
        save();
    }

    public static void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("yongye.json");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(INSTANCE == null ? new YongyeConfig() : INSTANCE));
        } catch (IOException e) {
            Yongye.LOGGER.error("[永夜] 写入配置失败", e);
        }
    }
}
