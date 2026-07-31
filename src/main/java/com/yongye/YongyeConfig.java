package com.yongye;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 全局配置。文件位于 config/yongye.json,可在不改代码的前提下调平衡。
 */
public class YongyeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static YongyeConfig INSTANCE;

    /** 当前配置 schema 版本号。每次我重新平衡默认值时 +1;加载旧版本文件会在日志里警告"配置可能过时"。 */
    public static final int CURRENT_CONFIG_VERSION = 135; // m388 永夜音景+2 · m387 讨伐演出+1 · m386 拾取通知卡+1 · m385 怪物微型血条+1 · m384 死亡转场+1 · m383 命中音分层+1 · m382 多杀弹字+1 · m381 特效质量档+1 · m380 永夜转场+3 · m377 永夜氛围粒子+2 · m376 掉落光柱+1 · m375 UI动效+1 · m374 受击方向指示+1 · m373 伤害飘字+2 · m367 肉盾脱战回盾+1 · m366 猎杀勋章+9 · m365 血条缩放+1 · m364 每日悬赏+9 · m363 渐进解锁+1 · m361 主线目标常显+1 · m359 强化直供+1 · m357 自动存/学书直供+2 · m356 材料仓库+1 · m352 事件天象+1 · m351 BOSS图鉴页+1 · m350 任务书节点地图+1 · m348 新手引导+3 · m346 技能CD常显HUD+3 · m341 P0~P3修复+BOSS阶梯解锁 · m338 蚀域/锻造+2 · m339 BOSS加强改版 · m337 转移+1 · m336 跟手+1 · m335 性能护栏+4 · m334 反卡+9 · m332 试炼+1 · m331 龙魂+1 · m330 永夜+3 · m328 主线任务+2 · m325~m327 任务权重/保血/拖刀+3 · m323 合书+1 · m322 获取提示+1 · m320 召唤协同+3 · m316 疾跑姿态+2 · m311 全怪紫气分档+1 · m312 看板默认左上(-2,14) · ...m308 看板挪位紧凑+4 · m309 精英战斗AI+14 · m310 僵尸红眼紫光+2
    public int configVersion = CURRENT_CONFIG_VERSION;

    // —— 战利品宝箱(m245)——
    /** 总开关:BOSS 级怪掉战利品宝箱。 */
    public boolean lootCrateEnabled = true;
    /** 开箱摇奖次数缩放(1.0=普通3/稀有5/史诗7/传说9 次)。 */
    public double lootCrateRollScale = 1.0;
    /** 传说宝箱开出随机职业武器的概率(0~1)。 */
    public double lootCrateWeaponChance = 0.20;

    // —— 魔法阵技能特效(m246)——
    /** 总开关:大招施放脚下展开职业色魔法阵+音效。 */
    public boolean magicCircleEnabled = true;
    /** 魔法阵大小缩放(1.0 ≈ 半径 2.6 格)。 */
    public double magicCircleScale = 1.0;

    // —— 疾跑收刀(m247)——
    /** 总开关:疾跑时主手武器收到背后(第三人称观感,第一人称不变)。 */
    public boolean weaponOnBackEnabled = true;

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
    public int skillBookMaxLevel = 1000000000;       // 技能书等级上限(10亿,与 m127 属性上限 10亿 对齐;int 上限约 21.4 亿,装得下)
    public double skillStealChancePerLevel = 0.005; // 抢夺技能每级 +0.5% 夺取概率
    public double skillStealMaxChance = 0.9;         // 抢夺概率上限
    public double skillLifestealPerLevel = 0.004;    // m290 吸血技能:每级回 造成伤害×0.4%(作者点名不能太高)
    public double skillLifestealMax = 0.08;          // 吸血比例封顶 8%(技能书上限 10 亿级,必须靠这条封死)

    // —— m291 六新强化(百分比效果一律封顶,理由同吸血) ——
    public double skillCritChancePerLevel = 0.002;    // 暴击:每级 +0.2% 触发概率
    public double skillCritChanceMax = 0.25;          // 暴击概率封顶 25%
    public double skillCritMultiplier = 1.5;          // 暴击总倍率(触发时追加 (倍率-1)×原伤,走玩家名义伤害)
    public double skillSwiftMovePerLevel = 0.002;     // 迅捷:移速每级 +0.2%
    public double skillSwiftMoveMax = 0.30;           // 移速封顶 +30%
    public double skillSwiftAtkSpeedPerLevel = 0.002; // 迅捷:攻速每级 +0.2%
    public double skillSwiftAtkSpeedMax = 0.25;       // 攻速封顶 +25%
    public double skillPiercePerLevel = 0.003;        // 破甲:每级 +0.3% 无视护甲追加伤害(魔法伤)
    public double skillPierceMax = 0.30;              // 破甲封顶 30%
    public double skillSteadfastPerLevel = 0.005;     // 屹立:击退抗每级 +0.5%
    public double skillSteadfastMax = 0.60;           // 击退抗封顶 60%(属性本身 0~1)
    public double skillGreedXpPerLevel = 0.01;        // 贪婪:每级 +1% 额外击杀经验
    public double skillGreedXpMax = 1.0;              // 额外经验封顶 +100%
    public int skillGreedXpBase = 5;                  // 额外经验基准值(≈普通怪原生经验,额外经验=基准×比例)
    public int skillRejuvenateDelayTicks = 160;       // 回春:脱战 8 秒后启动
    public double skillRejuvenatePerLevel = 0.001;    // 回春:每级每秒回 最大生命×0.1%
    public double skillRejuvenateMax = 0.03;          // 回春每秒封顶 3% 最大生命

    /** 属性技能书(攻击/护甲/恢复/闪避/反伤/抗性)掉落几率:普通怪 / 精英 / Boss。普通怪受永夜等级加成。 */
    public double skillBookDropChanceNormal = 0.001;
    public double skillBookDropChanceElite = 0.001;
    public double skillBookDropChanceBoss = 1.0;
    public int skillBookEarlyGameDays = 6;        // 前几个游戏日视为前期
    public double skillBookEarlyGameChance = 0.1; // 前期技能书爆率压制系数
    /** 技能书"永夜越高越易爆"的倍率封顶(防止深渊层爆率失控):普通怪爆率 = skillBookDropChanceNormal × min(1+永夜×0.5, 此值) */
    public double skillBookNightfallMaxMult = 3.0;
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

    // ============ m309 精英战斗AI(作者点名) ============
    /** 跳劈:持武器精英起跳扑向目标,落地重击=其攻击力×倍率;开关/冷却/倍率/最远触发距离(格) */
    public boolean eliteLeapAttack = true;
    public int eliteLeapCooldownTicks = 100;
    public double eliteLeapDamageMult = 1.6;
    public double eliteLeapMaxRange = 10.0;
    /** 走位:精英骷髅左右横移+贴脸后撤+偶尔小跳(速度脉冲叠在原版AI上);强度=每次侧移冲量 */
    public boolean eliteSkeletonStrafe = true;
    public double eliteStrafeImpulse = 0.22;
    /** 精英苦力怕自爆对生物伤害倍率(作者点名翻倍;≤1=关) */
    public double eliteCreeperDamageMult = 2.0;
    /** 逃跑:血量<占比→撒腿跑并回血(每秒最大生命×Regen),回到Return占比或超时→咆哮杀回;苦力怕不逃 */
    public boolean eliteFleeEnable = true;
    public double eliteFleeHealthFraction = 0.20;
    public double eliteFleeReturnFraction = 0.90;
    public double eliteFleeRegenPerSecond = 0.05;
    public int eliteFleeMaxTicks = 300;
    /** 跳搭:近战精英目标在头顶时跳+脚下垫圆石;间隔越小搭越快(默8t≈2.5格/秒);受mobGriefing约束 */
    public boolean eliteBuildBlocks = true;
    public int eliteBuildIntervalTicks = 8;

    // ============ Boss 翻倍(文档第 7 章)============
    public boolean enableBoss = true;
    public double bossHealthMultiplier = 2.0;
    public double bossAttackMultiplier = 2.0;
    public double bossSpeedMultiplier = 1.2;
    public double bossKnockbackResistanceAdd = 0.4;
    /** 掉落奖励翻倍系数 */
    public double bossDropMultiplier = 2.0;
    /** 掠夺者队长强化为 Boss 的最低游戏天数(避免开局就遇到 Boss 级队长;真·Boss 凋灵/监守者等不受此限) */
    public int bossRaidCaptainMinDay = 8;

    // ============ 普通怪 BOSS 版(m60:第 N 天起,普通怪低概率"BOSS化")============
    public boolean enableMobBoss = true;
    public int mobBossStartDay = 5;   // m339:第 5 天开刷皮肤 BOSS(作者点名)             // 第几天起开始刷怪物BOSS(早于此天不刷)
    public double mobBossChance = 0.008;         // 每只普通怪生成时BOSS化的概率(低,作偶发精英BOSS)
    // m145:僵尸BOSS化时,做成"玩家皮肤BOSS"——名牌「<在线玩家名> BOSS」、用该玩家皮肤渲染、每个在线玩家同时最多一只
    public boolean enablePlayerSkinZombieBoss = true;
    public double mobBossHealthMultiplier = 25.0;
    public double mobBossAttackMultiplier = 6.0;
    public double mobBossSpeedMultiplier = 1.3;
    public double mobBossKnockbackResistanceAdd = 1.0;
    public double mobBossScaleMultiplier = 1.8;  // 体型放大(更像Boss);1.0=不放大。靠 GENERIC_SCALE 属性
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
    public double enhanceDamagePerLevel = 0.5;     // 武器每级 +攻击力(拐点内;拐点外见 m298 曲线)
    // ============ m298:方案D 超上限成长曲线(作者定稿:强化到后面就能打动末影龙) ============
    // 拐点内每级原样;拐点外每级增值 ×(等级/拐点)^指数。默认 K=1万 p=1.2:int 顶级攻击 ≈ 1.2e15,
    // 单刀过龙甲 ≈ 3.9e15 跨过龙血 1e19 的 float 粒度墙,一条龙命 ≈ 2600 刀。武器强化与攻击书同曲线。
    public boolean enableEnhanceCurve = true;        // 关=回纯线性(每级恒 0.5)
    public int enhanceCurveKneeLevel = 10000;        // 拐点等级 K(与强化失败曲线降底同点)
    public double enhanceCurveExponent = 1.2;        // 增压指数 p(0=退化为线性)
    public double skillAttackPerLevel = 0.5;         // 攻击书每级 +攻击(原写死 0.5,开成配置并入曲线)
    public double enhanceArmorPerLevel = 0.3;      // 盔甲每级 +护甲
    public double enhanceToughnessPerLevel = 0.1;  // 盔甲每级 +护甲韧性
    public double enhanceHealthPerLevel = 1.0;     // 盔甲每级 +最大生命
    public int enhanceDurabilityPerLevel = 8;      // 每级 +最大耐久
    public double enhanceCritBonusMultiplier = 0.75; // 暴击额外伤害 = 强化攻击加成 × 此值
    public double enhanceHybridDamageFraction = 0.3; // 攻防双修武器(如镇魂)的攻击成长 = 武器攻击/级 × 此值(m237:0.5→0.3,肉盾攻击再压,坦而不是战)
    public double enhanceWeaponHealthPerLevel = 0.1;  // m237:普通武器每级 +最大生命(肉盾系走 enhanceHealthPerLevel=1.0,十倍差距)
    // —— m236 强化继承(强化界面:材料槽放已强化装备 → 等级按比例并入左边装备,来源销毁) ——
    public boolean enableEnhanceInherit = true;
    public double enhanceInheritKeepFraction = 0.8;   // 继承保留比例(0.8=转移80%,1.0=无损)
    // ============ m299:召唤物免友伤 ============
    public boolean summonFriendlyFireImmune = true;  // 玩家(横扫/误点/弹射物)打不伤己方召唤物:傀儡/肝帝/暗影分身
    // ============ m300:击杀归属 ============
    public boolean summonKillsCreditOwner = true;    // 召唤物(傀儡/肝帝/暗影分身)击杀记主人:看板/掉落/保护卷/贪婪/任务/蚀域全口径
    public int dragonSoulPerKill = 1;                // m331 每次讨伐末影龙掉落龙魂数
    public boolean enableNgPlus = true;              // m330 永夜+总开关(状态仍持久,关=倍率不生效)
    public double ngPlusMobMult = 2.0;               // m330 二周目怪物血/攻倍率(封顶后乘)
    public double ngPlusLootMult = 2.0;              // m330 二周目掉落倍率(概率与保底数量)
    // ============ m335 性能护栏 ============
    public boolean lagGuardEnabled = true;           // 卡顿护栏:按 MSPT 节流波次刷怪(尸潮/烛光域/自定义投放)
    public double lagGuardSoftMspt = 35.0;           // 软阈值:低于全量,高于开始线性降量
    public double lagGuardHardMspt = 48.0;           // 硬阈值:达到即本波跳过
    public int itemCleanupBatchPerTick = 150;        // 掉落物清理每 tick 删除批量(分帧摊平尖峰)
    // ============ m334 反卡BUG(悬空卡怪/泡水躲怪) ============
    public boolean pillarCheesePunish = true;        // 悬空卡怪反制总开关
    public int pillarCheeseGraceTicks = 100;         // 宽限(5s):支撑块下两格悬空+附近有怪才计时
    public double pillarCheeseDamagePercent = 0.05;  // 每秒按最大生命扣血比例(5%)
    public double pillarCheeseMobRadius = 16.0;      // 附近多少格内有敌对怪才算"卡怪"
    public boolean waterCheesePunish = true;         // 泡水躲怪反制总开关
    public int waterCheeseGraceTicks = 1200;         // 宽限(60s):连续泡水超过即反制,离水清零
    public double waterCheeseDamagePercent = 0.03;   // 每秒按最大生命扣血比例(3%)
    public int waterCheeseSummonIntervalTicks = 100; // 每 5s 召一波溺尸
    public int waterCheeseSummonCount = 1;           // 每波召几只
    public double enhanceTransferKeepFraction = 1.0; // m337 强化转移保留比例(1=全额无损,0.8=损耗两成)
    public boolean classFollowWeapon = true;         // m336 拿着啥职业武器就是啥职业(生效判定/大招/小技能全跟手)
    public boolean enableClassTrials = true;         // m332 职业试炼支线(三关,奖励本命武器专属强化)
    public boolean enableMainQuest = true;           // m328 主线任务书(16 阶段,终点=讨伐末影龙)
    public boolean giveQuestBook = true;             // m328 首次进服发任务书(每人一次)
    public double questFleeWeight = 0.35;            // m325 逃离/走格任务抽取权重(其余类型各1.0;调0=不再派)
    public boolean healthKeepRatio = true;           // m326 最大生命变化时按百分比保血(切武器不再血条跳变)
    public int sprintWeaponStyle = 2;                // m327 疾跑武器样式:0=原版持刀 1=收背后(旧) 2=拖刀(新默认)
    public boolean enableBookMerge = true;           // m323 背包「合书」:全部书按类型一键合并(等级相加+扣结果档材料)
    public boolean itemSourceTooltips = true;        // m322 无配方物品的「获取:」tooltip 提示
    public boolean summonAssistFocus = true;         // m320 集火:主人攻击谁,半径内己方召唤物就打谁(强制切目标)
    public boolean summonAssistDefend = true;        // m320 护主:主人挨打,闲着(无活目标)的召唤物去支援
    public double summonAssistRadius = 32.0;         // m320 协同响应半径(格)

    // ============ m296:强化石滑动窗掉落(定稿设计) ============
    // 基准档 t:第1~5天=1;佩恩降临后=2;进永夜 I=4、每层+1;每次怪物进化(evolutionEveryDays)+1;封顶 stoneTierCap。
    public boolean enableEnhanceStoneDrops = true;   // 总开关:普通/精英/BOSS 按进度档位掉强化石
    public double stoneDropChanceNormal = 0.05;      // 普通怪掉石概率(不乘动态爆率:档位窗本身就是节奏闸)
    public double stoneNormalWeightT0 = 0.70;        // 普通怪窗权重:基准档 t
    public double stoneNormalWeightT1 = 0.25;        //               t+1
    public double stoneNormalWeightT2 = 0.05;        //               t+2
    public double stoneEliteWeightT1 = 0.50;         // 精英必掉一颗:t+1
    public double stoneEliteWeightT2 = 0.40;         //               t+2
    public double stoneEliteWeightT3 = 0.10;         //               t+3
    public int stoneBossMinCount = 3;                // BOSS 必掉颗数下限(随 bossDropMultiplier 放大)
    public int stoneBossMaxCount = 5;                // 上限(含)
    public int stoneBossMinOffset = 2;               // BOSS 档位窗:t+2
    public int stoneBossMaxOffset = 4;               //              ~t+4(均匀)
    public int stoneTierCap = 10;                    // 基准档封顶(最高档强化石,硬上限 10)
    public int stoneDaysPerTier = 3;                 // m301 天数硬顶:最终掉落档≤1+天数/此值(3=第9天起才出1000级档;0=关)
    public int autoScrollMaxStoneTier = 5;           // m302 自动强化卷轴最多自动吞的石档(5=只吞≤1万;0=石头全不吞;10=全吞)

    // ============ m304:皮肤 BOSS 格挡条 + 攻击平衡 ============
    public boolean enableBossGuard = true;           // 六只皮肤BOSS带格挡条:格挡在=减伤,打空=破防窗口全额
    public double bossGuardFraction = 0.20;          // 格挡上限 = 最大生命 × 此比例
    public double bossGuardDamageCut = 0.5;          // 格挡在时伤害减免比例(0.5=只吃一半)
    public int bossGuardBreakTicks = 200;            // 破防窗口时长(tick,200=10秒),窗口结束格挡回满
    public double bossGuardBreakDamageMult = 1.25;   // 破防期间伤害额外倍率(1=仅全额无加成)
    public double bossHitCapFraction = 0.35;         // 攻击平衡:皮肤BOSS对玩家单击≤最大生命×此比例(0=关)

    // ============ m305:烛之维度(烛块门/紫天/百倍刷怪+实体闸) ============
    public boolean enableCandleDimension = true;     // 总开关:点门/传送/维度刷怪
    public int candleDimSpawnIntervalTicks = 4;      // 每玩家刷怪波间隔(原版地表≈400t一波,4t≈百倍)
    public int candleDimSpawnRadius = 40;            // 刷怪环带外半径(内固定12)
    public int candleDimMaxNearbyHostiles = 120;     // 实体闸①:每玩家48格内敌对上限
    public int candleDimGlobalMaxHostiles = 400;     // 实体闸②:全维度敌对硬预算
    public int candleDimCleanupDistance = 96;        // 实体闸③:离所有玩家超此距离的敌对定期清除
    public int candleDimFilterAlpha = 40;            // 淡紫滤镜浓度(0~160,0=关)
    public boolean stoneTopTierEliteOnly = true;     // 封顶期收口:普通怪不出最高档(降为次档),10亿石只从精英/BOSS 出

    // ============ m297:技能书 ×100 分五档(1/100/1万/100万/1亿),随石档折半爬 ============
    public boolean enableStagedSkillBooks = true;    // 总开关:属性技能书等级走阶段书档(关=回旧的固定小等级)
    public int skillBookPercentTierCap = 2;          // 百分比类书最高档(2=100级;封顶都极低,高档纯浪费掉落位)
    public double skillBookBossAttackBias = 0.5;     // BOSS 书强制攻击书的概率(攻击书高档主要出处)
    public int bossBookMinCount = 1;                 // BOSS 必掉技能书本数下限
    public int bossBookMaxCount = 3;                 // 上限(含,随 bossDropMultiplier 放大)

    public int enhanceShardValue = 1;              // 生命碎片 = +1 级
    public int enhanceCrystalValue = 10;           // 生命结晶 = +10 级
    public int enhanceCoreValue = 100;             // 生命核心 = +100 级
    public int enhanceBloodCoreValue = 1000;       // 灾变血核 = +1000 级

    // —— m158:强化失败/碎裂 ——
    // 逐级 RNG:等级 < enhanceFailStartLevel(默认1000)必成功;此后成功率从 100% 线性降到
    // enhanceFailEndLevel(默认10000)处的 enhanceFailEndRate(默认0.10=10%),并以该值封底。
    // 失败只消耗本次材料、等级不变;等级 ≥ enhanceBreakLevel(默认10000)时,失败有 enhanceBreakChance
    // 概率使装备「碎裂」(直接销毁),除非已用强化保护卷(见下,挡一次碎裂)。
    public boolean enableEnhanceFailure = true;
    public int enhanceFailStartLevel = 1000;       // 此级之前(含)强化必成功,从下一级开始可能失败
    public int enhanceFailEndLevel = 10000;        // 成功率降到底的等级
    public double enhanceFailEndRate = 0.10;       // enhanceFailEndLevel 及以上的成功率(封底)
    public int enhanceBreakLevel = 10000;          // 达到此级后,失败可能令装备碎裂
    public double enhanceBreakChance = 0.25;       // 碎裂概率(仅在 ≥enhanceBreakLevel 的失败上判定;有保护卷则免)
    // m199:碎裂难度门——只有世界难度 ≥ 此档才可能「碎裂」;低于此档(或难度未设定)强化仍会失败、
    //       但装备不会碎、也不消耗保护卷。档位=GameDifficulty 序号(0游玩/1简单/2适中/3困难/4地狱/5深渊/6永夜),默认 3=困难。
    public int enhanceBreakMinDifficulty = 3;
    // m201:碎武器(碎裂)总开关——关掉后强化仍可能失败(白费材料/等级不涨),但装备永不碎裂。
    //       /yongye enhancebreak 或调试菜单「碎武器:切换」可开关。
    public boolean enableEnhanceBreak = true;
    // m198:整次强化保护。开启后,每次强化(操作)只要会摸到碎裂等级(≥enhanceBreakLevel),
    // 就自动消耗一张强化保护卷(优先用已激活的手动护盾),这一整次强化都不碎裂(不管里面尝试多少次);
    // 关闭则回到「手动右键激活、挡下一次碎裂」的老行为。可用 /yongye protectperop 或调试菜单开关。
    public boolean enhanceProtectPerOperation = true;

    // —— m159:强化保护卷(无法合成,仅怪物掉落 + 杀怪自动兑换)——
    public boolean enableProtectScroll = true;
    public double protectScrollDropChance = 0.002; // 敌对怪死亡掉落保护卷的概率(很低)
    public int protectScrollKillBase = 2000;       // 杀怪兑换:首个需 2000 击杀,每兑换 1 个后阈值翻倍(2000→4000→8000…)

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

    // ===== 武器技能升级(m131:用终焉精华升级三大技能,每级加伤+略减冷却)=====
    public boolean enableWeaponSkillUpgrade = true;
    public int skillUpgradeMaxLevel = 20;            // 每个技能最高升级等级
    public int skillUpgradeBaseCost = 1;             // 升到 Lv.1 需要的终焉精华数
    public int skillUpgradeCostPerLevel = 1;         // 每升一级额外多需的终焉精华数(线性递增)
    public double skillUpgradeDamagePerLevel = 0.25; // 每级技能 → 该技能伤害额外 +25%(乘在最终伤害上)
    public int skillUpgradeCdReductionPerLevel = 4;  // 每级技能 → 冷却 -4 tick
    public int skillUpgradeCdFloor = 40;             // 冷却下限(tick,2s),再升级也不会更短

    // ===== 怪物随进度递增(血量/攻击跟着玩家变强) =====
    /** m249 起弃用:按天数/永夜的进度成长恒开(作者点名「不能关」),此开关不再被读取,保留占位防旧 json 报死键。 */
    public boolean enableMobScaling = true;
    public double mobScalingPerNightfall = 0.8;       // 每级永夜 +60% 血
    public double mobScalingPerDay = 0.06;            // 每个游戏日 +4% 血
    public int mobScalingMaxDays = 100;               // 计入天数上限
    public double mobScalingPlayerHealthFactor = 0.5; // 按附近玩家「超出20的最大生命」比例的 50% 给怪加血
    public double mobScalingAttackRatio = 0.4;        // 攻击按血量缩放的 30% 同步提升
    public double mobScalingMaxMultiplier = 60.0;     // 缩放倍率上限

    // ===== 动态对位缩放(按附近最强玩家的攻击/血量,把怪等比拔高,保证有来有回)=====
    public boolean enableDynamicMobScaling = true;    // 总开关(m249:其中「血量对位」还需世界难度≥地狱才生效;伤害对位不受难度门约束)
    public double dynamicMobScanRadius = 64.0;         // 怪生成时搜索最近玩家的半径
    public double dynamicMobTargetHits = 8.0;          // 普通怪:期望玩家砍多少下才杀死(怪血 = 玩家攻击 × 此值)
    public double dynamicMobSurviveHits = 30.0;        // 普通怪:期望玩家被打多少下才致命(怪伤 = 玩家最大生命 ÷ 此值)
    public double dynamicMobBossTargetHits = 45.0;     // BOSS版:期望击杀次数(远高于普通怪,是块硬骨头)
    public double dynamicMobBossSurviveHits = 12.0;    // BOSS版:期望承受次数(打得更疼)

    // ===== 动态爆率(玩家越强、掉率越低,减缓滚雪球,让怪物成长追得上)=====
    public boolean enableDynamicLoot = true;           // 总开关(m249:即便开着,也仅世界难度≥地狱才实际衰减;困难及以下恒 1.0)
    public double dynamicLootK = 150.0;                // 强度半衰常数:强度=此值时掉率减半,=3×时剩 1/4
    public double dynamicLootFloor = 0.15;             // 掉率下限:再强也保底这个倍率(避免完全不掉)
    public double dynamicLootEnhanceWeight = 2.0;      // 强化等级在强度里的权重(1级强化 ≈ 几级技能书)
    public boolean dynamicLootScaleGuaranteed = true;  // 是否也按倍率缩减精英「必爆」的碎片/书数量(防滚雪球关键)

    // ===== 难度奖励(m150:世界难度越高,掉落越丰厚——和动态爆率倍率并乘)=====
    public boolean enableDifficultyLootBonus = true;   // 总开关:开后概率掉落与精英必爆数量都乘世界难度倍率
    public double difficultyLootFloor = 1.0;           // 难度奖励倍率下限:取 max(此值, 世界难度mobMult)。1.0=低难度不减奖励,只有困难以上加成;设<1.0可让低难度也少掉

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
    // 注:挖矿/砍树减速功能(MiningSpeedMixin)已于 m126 整段移除——挖掘恒为原版速度,不再有任何减速开关。

    // ============ 随机掉落(文档 11.2 普通怪概率)============
    public double lootChanceCommon = 0.35;
    public double lootChanceUseful = 0.15;
    public double lootChanceRare = 0.06;
    public double lootChanceEpic = 0.02;
    public double lootChanceGodly = 0.008;
    /** 普通怪掉落"生命碎片"的概率(文档 15.1) */
    public double lifeShardDropChance = 0.10;     // 生命碎片:普通/精英怪按此概率掉(原 1.0 必掉过高)
    public double lifeCrystalDropChance = 0.05;  // 生命结晶:普通怪 5%(精英翻倍=10%);结晶是进阶材料,故远低于碎片
    public double lifeCoreDropChance = 0.05;     // 生命核心:仅精英(普通怪绝不掉)
    public double bloodCoreDropChanceElite = 0.025; // 灾厄血核:仅精英,小概率
    public double classWeaponDropChanceElite = 0.04; // 精英掉落职业专属武器(随机职业)的几率
    // —— m90:精英「必爆套餐」(在上面的概率掉落之上额外保底,提高精英击杀收益)——
    /** 精英是否必爆保底套餐(碎片+结晶+随机技能书);关掉则只走概率掉落 */
    public boolean eliteGuaranteedDrops = true;
    /** 精英必爆:生命碎片数量 */
    public int eliteGuaranteedShards = 10;
    /** 精英必爆:生命结晶数量 */
    public int eliteGuaranteedCrystals = 1;
    /** 精英必爆:随机技能书数量(从血量书 + 6 种属性书里随机,等级见下) */
    public int eliteGuaranteedSkillBooks = 1;
    /** 精英必爆随机技能书的等级范围 [min,max] */
    public int eliteGuaranteedSkillBookMinLevel = 1;
    public int eliteGuaranteedSkillBookMaxLevel = 3;
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
    public double nightfallBeyondHpPerLevel = 2.0;
    /** 无尽永夜:开启后等级无上限(可一直往上升,深渊层无尽),关闭则仍受 nightfallMaxLevel 钳制。默认开。 */
    public boolean nightfallEndless = true;
    /** 久留升层:长时间处于永夜(等级≥1)会自动提升一层。默认开。 */
    public boolean nightfallTimeEscalate = true;
    /** 久留升层的间隔(分钟):在永夜中每持续这么久,永夜自动 +1。默认 30。 */
    public int nightfallTimeEscalateMinutes = 30;

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
    // —— 刷新提示增强(m81)——
    public boolean coreSpawnTitle = true;         // 刷新时给附近玩家 音效 + 屏幕中央标题(不止聊天)
    public int coreSpawnNotifyRadius = 120;       // 标题/音效通知半径(此范围内玩家收到)
    public boolean enableCoreLocator = true;      // HUD 方向箭头:指向最近的灾厄核心(像 boss 指示)
    public int coreLocatorRange = 220;            // 箭头只在核心位于此范围内时显示
    public boolean coreAltarStructure = true;     // 核心生成时建造祭坛结构(底座+立柱+灵魂灯),关掉则只放光秃秃一个核心块
    public boolean coreDeathRaisesNightfall = true; // 祭坛存在时玩家死亡 → 激发最近祭坛凝聚完毕 + 永夜提升一层

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
    /** 细柱兜底传送(m151:玩家用 1×1 高柱躲在正上方时,周围无落脚地面致普通传送失败——此项直接把怪传到玩家所在格)*/
    public boolean pursuitTeleportPillarCheese = true; // 总开关
    public double pillarCheeseMinHeight = 4.0;   // 玩家高出怪至少这么多格才判定为"躲高柱"(略高于搭塔触发的3格,先给搭塔/爬墙机会)
    public double pillarCheeseMaxHorizontal = 2.5; // 玩家水平距离小于此值才算"正上方"(同搭塔)
    public double pillarCheeseKnockback = 0.6;   // 传上柱后把玩家从柱上撞下去的水平冲量强度(0=只传不撞;柱仅1格宽,0.6 足以推出边缘坠落)
    // 触发还需"持续无进展达 pursuitStuckTicks";传送次数同样受 pursuitMaxTeleportsPerTick 限流

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
    /** m367 脱战回盾:受击/出手后须离开战斗这么多 tick 才恢复每秒续盾(战斗中盾被打掉不回;0=旧恒刷行为)。 */
    public int tankShieldCombatDelayTicks = 100;
    public double tankShieldReflect = 4.0;            // 持磐盾格挡时的反震伤害
    public double tankTrueDamageReduction = 0.15;     // 坦克真·百分比减伤(0~0.9;0=关。经 LivingEntity mixin 削减最终伤害)
    // —— 反苟机制(破解泡水/虚空搭方块/远程龟缩) ——
    public boolean enableAntiCheese = true;
    public int antiCheeseWaterSeconds = 8;            // 泡水超此秒数 → 召唤守护者追杀
    public int antiCheeseAirborneSeconds = 10;        // 长时间悬空(搭方块龟缩)超此秒数 → 召飞行怪
    public int antiCheeseGraceSeconds = 6;            // 进入苟态后多少秒宽限,超过才开始持续扣血
    public double antiCheeseDrainPerSecond = 4.0;     // 龟缩持续扣血(点/秒,按比例叠加最大生命)
    public double antiCheeseDrainMaxHpFraction = 0.02;// 额外按最大生命比例扣(应对高血量苟),每秒
    public boolean antiCheeseBreakRoof = true;        // 头顶有方块龟缩 → 破开顶盖(让空袭俯冲)
    public int antiCheeseRoofBreakHeight = 4;         // 向上破几格顶盖
    public boolean antiCheeseSummonEnderman = true;   // 顶盖/封闭龟缩 → 召末影人搬走结构方块
    // —— 定时清理掉落物 ——
    public boolean enableItemCleanup = true;          // 总开关
    public int itemCleanupFirstMinutes = 21;          // 服务器启动后多少分钟进行首次清理
    public int itemCleanupIntervalMinutes = 5;        // 之后每隔多少分钟清理一次
    // —— 战利品磁吸(只吸本 mod 贵重掉落物到玩家;原版杂物留给定时清理)——
    public boolean enableLootMagnet = false;          // m144 停用(Sophisticated Backpacks 自带磁吸);注:已不再挂载 LootMagnetHandler,此开关当前无效,保留备查
    public double lootMagnetRadius = 8.0;             // 磁吸半径(格)
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
    // 术士法术弹(右键蓄力施法)
    public double warlockBoltDamage   = 18.0;         // (旧)固定基础伤害,现作保底:伤害取 max(攻击力×倍率, 此值×倍率)
    public double warlockBoltMinMult  = 0.5;          // 保底倍率(手快松也有下限)= 攻击力 × 此倍率
    /** m261 起弃用:蓄力改按秒数线性增长(见 warlockBoltMultPerSecond/Cap),此字段不再被读取,保留占位。 */
    public double warlockBoltMaxMult  = 4.0;
    public double warlockBoltHpCost   = 3.0;          // 基准耗血(实际=×(0.4+0.2×蓄力秒数),封顶×3——蓄越久献祭越狠)
    public double warlockBoltRange    = 20.0;         // 射线最大射程(格)
    /** m261 起弃用:不再有固定满蓄时长(可无限按住),保留占位。 */
    public int    warlockBoltChargeTicks = 30;
    /** m261:每按住 1 秒,伤害倍率 +此值(默认 1.0:1 秒=×1、5 秒=×5)。 */
    public double warlockBoltMultPerSecond = 1.0;
    /** m261:倍率封顶(默认 ×10=按满 10 秒;到顶金字提示,再按不涨)。 */
    public double warlockBoltMultCap = 10.0;
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

    // ===== 开局难度 + 职业选择书(m130)=====
    public boolean enableDifficultySelect = true;      // 登录首次弹「难度选择」界面(取代旧的强制选职弹窗)
    public boolean giveClassSelectBook = true;         // 首次登录发一本「职业选择书」,玩家自行右键选职

    // ===== 武器后期吸血(m130:强化达阈值后,蓄满攻击命中按攻击力比例回血)=====
    public boolean enableWeaponLifesteal = true;
    public int weaponLifestealMinLevel = 1000;         // 武器强化达此等级才有吸血
    public double weaponLifestealBase = 0.1;           // 到达阈值时的吸血比例(0.1=攻击力的10%回血)
    public double weaponLifestealPerLevel = 0.0001;    // 每超过阈值 1 级,吸血比例 +0.0001(+0.1/千级)
    public double weaponLifestealMax = 0.5;            // 吸血比例上限(50%)

    // ===== 武器携带即生效(m133:职业武器带在背包就给加成,不必拿在主手)=====
    public boolean enableWeaponCarryBonus = true;

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
    /** 持有搜集任务时,击杀敌对怪掉落该任务目标物的概率(解决粘液球等前期难凑物的来源) */
    public double questGatherDropChance = 0.4;
    public int questGatherDropAmount = 1;     // 每次掉落的目标物数量

    // ===== m250 战斗爽任务强化(仅世界难度=战斗爽时生效) =====
    /** 战斗爽下任务难度总倍率:击杀数/搜集数/逃离距离全部照乘(1=不加难)。 */
    public double questBattleScale = 1.5;
    /** 战斗爽下搜集任务目标改为「全物品表随机」(技术性物品/刷怪蛋黑名单外),以抽奖滚动方式揭晓最终目标。 */
    public boolean questBattleAnyItem = true;
    /** 抽奖滚动时长(tick,60=3 秒;0=不滚动直接揭晓)。滚动期间任务不判定成功/失败。 */
    public int questBattleRollTicks = 60;
    /** m253:全物品随机追加黑名单的默认值——龙蛋/鞘翅/信标/图腾/下界合金一族/潜影盒/唱片/头颅等「前期不好拿」物(作者点名排除)。 */
    public static final String QUEST_BANS_DEFAULT =
            "minecraft:dragon_egg, minecraft:elytra, minecraft:nether_star, minecraft:beacon, minecraft:dragon_breath, minecraft:end_crystal, minecraft:totem_of_undying, minecraft:heavy_core, minecraft:trident, minecraft:ancient_debris, minecraft:netherite_*, minecraft:enchanted_golden_apple, minecraft:shulker_shell, *shulker_box, *smithing_template, minecraft:music_disc_*, minecraft:disc_fragment_5, minecraft:echo_shard, minecraft:recovery_compass, minecraft:sniffer_egg, *_head, *_skull";
    /** 全物品随机的追加黑名单:物品 id 逗号/空格分隔;支持通配——"xxx*"=前缀匹配(如 minecraft:netherite_*)、
     *  "*xxx"=后缀匹配(如 *shulker_box 含 16 色)。想解禁某项从这里删掉即可;清空会在下次启动被迁移回默认(留一个占位项可保持为空效果)。 */
    public String questBattleAnyItemExtraBans = QUEST_BANS_DEFAULT;

    // ============ 背包神器(文档第 14 章)============
    public boolean enableArtifacts = true;
    public int artifactMaxLevel = 6;

    // ============ 材料兑换(m66:背包按钮,10 碎片→结晶→核心→血核,等值兑换)============
    public boolean enableMaterialExchange = true;

    // ============ 开局赠礼(m67:每人首次进入发一个背包)============
    public boolean giveStartingBackpack = true;
    /** 开局赠送的物品 id。默认 Sophisticated Backpacks 的下界合金背包;软依赖:未装该 mod 则自动跳过。 */
    public String startingBackpackItem = "sophisticatedbackpacks:netherite_backpack";

    // ============ 开局背包升级(m154:每人首次进入发 高级磁铁 + 高级喂食 两个升级)============
    // 软依赖 Sophisticated Backpacks:按 id 在物品注册表查到才发,查不到(未装该 mod / id 写错)静默跳过、不崩。
    // 若发现没发到,多半是 id 不对——直接在这里改成正确 id 即可(无需改代码),物品名见游戏内 F3+H 或合成表。
    public boolean giveStartingUpgrades = true;
    public String startingMagnetUpgradeItem = "sophisticatedbackpacks:advanced_magnet_upgrade";
    public String startingFeedingUpgradeItem = "sophisticatedbackpacks:advanced_feeding_upgrade";

    // ============ 开局职业武器附魔(m154:选职发的武器自带 抢夺III + 火焰附加II)============
    // 仅作用于「选职时发放的那把」职业武器(开局礼包语义);改等级即可,0=不附该项。
    // 注:Looting/FireAspect 由原版按武器附魔组件结算,不依赖 SwordItem(与横扫不同),非剑类职业武器也生效。
    public boolean weaponStartingEnchants = true;
    public int weaponStartingLootingLevel = 3;
    public int weaponStartingFireAspectLevel = 2;
    /** 横扫之刃等级(默认3=满级)。注:职业武器非 SwordItem,原版横扫不触发,但 m146 的手搓横扫(WeaponCombatHandler)会读此等级发 AOE。 */
    public int weaponStartingSweepingLevel = 3;

    // ============ 创造模式监听 + 世界崩塌(m155)============
    // 反作弊陷阱:非豁免玩家在创造里持有「禁忌之物」(攻击强化技能书 / 任一稀有材料)→ 触发世界崩塌(全怪 ×100,永久);
    //            非豁免玩家第 2 次进入创造 → 强制改回生存。豁免名单里的玩家(默认含管理员)完全不受限、不触发陷阱。
    public boolean enableCreativeWatch = true;
    /** 豁免名单:逗号/空格分隔的玩家名(大小写不敏感)。名单内玩家可自由进创造测试,且不会触发世界崩塌。 */
    public String creativeExemptIds = "qiaodaxian, jiemoli";
    public boolean creativeForceSurvivalOnSecond = true;
    /** 世界崩塌后所有怪物的血量/攻击倍率(×100)。触发后永久生效、玩家无法关闭;人工恢复需停服改存档 yongye_doom.json。 */
    public double doomMobMultiplier = 100.0;

    // ============ 开局两本书(m122:每人首次进入发《永夜·缘起》+《幸存者手册》)============
    public boolean giveWelcomeBooks = true;

    // ============ 开局口粮(m143:每人首次进入发 N 个面包)============
    public boolean giveStartingFood = true;
    /** 开局赠送的面包数量(0 = 不发);默认 20,超过 64 自动拆成多组。 */
    public int startingFoodCount = 20;


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
    // —— m292 禁疗改版(作者三点名:减疗而非全禁/只BOSS触发/要有CD)——
    public boolean healBlockBossOnly = true;           // 只有 BOSS 级(IS_BOSS)命中才可施加
    public double healBlockHealReduction = 0.7;        // 「重创减疗」:期间治疗效果 ×(1-此值),默认减 70%
    public int healBlockImmunityTicks = 400;           // 一次重创结束后的免疫 CD(20秒),期间不可被再次施加
    public int healBlockImmuneHeartLevel = 4;          // 饕餮心脏 ≥ 此等级(神话)完全免疫重创;0=关闭免疫
    public double healBlockHeartReducePerLevel = 0.15; // 未达免疫级时,饕餮心脏每级缩短重创时长 15%

    // ============ m70:精英装备格挡 / 永夜尸潮 / 追杀微调 / 终焉神髓掉率 ============
    /** 精英专属:终焉神髓掉率(生命核心链最高一档) */
    public double endingEssenceDropChanceElite = 0.0125;

    // 精英装备:第 N 天起精英持武器 + 盾牌,可格挡
    public boolean eliteUseEquipment = true;
    public int eliteEquipStartDay = 5;
    public double eliteBlockChance = 0.30;        // 持盾精英完全格挡一次攻击的概率

    // 永夜尸潮:永夜 ≥1 在玩家周围维持高密度刷怪,蜂拥追杀
    public boolean enableNightfallHorde = true;
    public int nightfallHordeBase = 100;          // 永夜 I 目标怪量;V2=翻倍(线性 base×等级),封顶 max
    public int nightfallHordeMax = 200;           // 目标怪量上限(护 TPS,可调)
    public int nightfallHordeIntervalTicks = 40;  // 每隔多久补一批
    public int nightfallHordeBatch = 10;          // 每批最多新刷数(平滑爬升,避免瞬刷卡顿)
    public double nightfallHordeRadius = 24.0;     // 在玩家周围多大范围刷/统计
    public double nightfallHordeMinDistance = 8.0; // 刷怪点距玩家最小距离

    // 野生末影龙 BOSS:第 N 天起有几率在玩家头顶高空刷出(会飞、追杀,跟末地那条差不多)
    public boolean enableWildDragonSpawn = true;
    public int wildDragonMinDay = 9;  // m341 阶梯解锁                 // 第几天起才可能刷(游戏天数)
    public double wildDragonSpawnChance = 0.05;       // 每次检定的刷出概率
    public int wildDragonCheckIntervalTicks = 6000;   // 每隔多少 tick 检定一次(6000=5分钟)
    public int wildDragonMaxAlive = 1;                // 全服同时存活上限(稀有 BOSS 事件)
    public double wildDragonSpawnHeight = 28.0;       // 在玩家上方多少格高空生成
    public double dragonAttackReach = 16.0;           // 末影龙近战出手距离(格);越大越远处就能打到,不必贴身

    // ============ m174:阿努比斯 Stage2:狂怒/法术/召唤 ============
    /** HP 首次降到该比例以下触发狂怒(速度/攻击提升 + AoE 击退 + 血条改红)。 */
    public double anubisRageHealthThreshold = 0.5;
    /** 法术施放冷却(tick;狂怒后自动减半,最短 60t)。 */
    public int anubisSpellCooldownTicks = 300;
    /** 法术 AoE 半径(格)。 */
    public double anubisSpellRadius = 6.0;
    /** 法术 AoE 魔法伤害量。 */
    public double anubisSpellDamage = 68.0;
    /** 恶灵召唤冷却(tick)。 */
    public int anubisSummonCooldownTicks = 600;
    /** 同场恶灵数量上限(超过不再召唤)。 */
    public int anubisMaxWraiths = 4;
    /** 触发恶灵召唤的 HP 阈值(比例;HP 低于该值才召唤)。 */
    public double anubisSummonHealthThreshold = 0.75;

    // ============ m175:阿努比斯自然降临(第 N 天起地表刷出) ============
    /** 阿努比斯自然降临总开关。 */
    public boolean enableAnubisSpawn = true;
    /** 第几天起才可能降临(游戏天数)。 */
    public int anubisMinDay = 10;     // m341 最强压轴
    /** 每次检定的降临概率。 */
    public double anubisSpawnChance = 0.03;
    /** 每隔多少 tick 检定一次(6000=5分钟)。 */
    public int anubisCheckIntervalTicks = 6000;
    /** 全服同时存活上限(稀有 BOSS 事件)。 */
    public int anubisMaxAlive = 1;

    // ============ m176:五只新怪自然刷怪(红蛛/法师/凤凰=稀有BOSS事件,毒蛛/螃蟹=精英压力) ============
    /** 新怪自然刷怪总开关(不含阿努比斯,它走 m175 独立开关)。 */
    public boolean enableCustomMobSpawns = true;
    /** 每隔多少 tick 检定一次(1200=1分钟;BOSS/精英共用该节拍,各自概率独立)。 */
    public int customMobCheckIntervalTicks = 1200;
    /** BOSS·红蜘蛛:第几天起 / 每次检定概率 / 全服存活上限。 */
    public int redSpiderMinDay = 6;   // m341:第6天起一天解锁一只(弱→强)
    public double redSpiderSpawnChance = 0.012;
    public int redSpiderMaxAlive = 1;
    /** BOSS·死亡法师:第几天起 / 每次检定概率 / 全服存活上限。 */
    public int deathMageMinDay = 7;
    public double deathMageSpawnChance = 0.010;
    public int deathMageMaxAlive = 1;
    /** BOSS·浴火凤凰:第几天起 / 每次检定概率 / 全服存活上限(高空刷出)。 */
    public int phoenixMinDay = 8;
    public double phoenixSpawnChance = 0.008;
    public int phoenixMaxAlive = 1;
    /** 精英·毒液蜘蛛:第几天起 / 逐玩家每次检定概率 / 玩家附近48格同类上限。 */
    public int venomSpiderMinDay = 4;
    public double venomSpiderSpawnChance = 0.06;
    public int venomSpiderMaxNearby = 2;
    /** 精英·巨型螃蟹:第几天起 / 逐玩家每次检定概率 / 玩家附近48格同类上限。 */
    public int giantCrabMinDay = 6;
    public double giantCrabSpawnChance = 0.05;
    public int giantCrabMaxNearby = 1;

    // ============ m183:末地末影龙强化 + 末地尸潮增强 ============
    /** 末地末影龙强化总开关(10亿血/高防/三命/脱战回血;不影响自建龙)。 */
    public boolean enableEndDragonBuff = true;
    /** 末影龙最大生命(默认 10 亿 = m127 属性上限,正好装满)。 */
    public double endDragonHealth = 1.0E19;   // m222:作者点名终局龙血=10000000000000000000(u64 时代的门面)
    /** 末影龙护甲 / 护甲韧性(40+20 接近原版减伤公式 80% 上限)。 */
    public double endDragonArmor = 40.0;
    public double endDragonToughness = 20.0;
    /** 末影龙总命数(3 = 死两次满血复活,第三次才真死;≤1 = 关闭多命)。 */
    public int endDragonLives = 3;
    /** 连续多少秒没掉血后开始回血。 */
    public int endDragonRegenDelaySeconds = 30;
    /** 脱战后每秒回复最大生命的百分比(1.0 = 每秒 1% = 每秒 1000 万)。 */
    public double endDragonRegenPercent = 1.0;
    /** 末地尸潮:目标怪量倍率 / 刷出怪的血攻额外倍率(都只在末地生效)。 */
    public double endHordeTargetMultiplier = 1.5;
    public double endHordeStatMultiplier = 2.0;

    // ============ m189:怪物伤害来源检测(外模组伤害不作数) ============
    /** 总开关:怪物受到的伤害只认原版 / 夜蚀（本模组）来源;外模组武器与外模组召唤物的伤害一律无效。 */
    public boolean enableForeignDamageFilter = true;
    /** 伤害被判无效时,给玩家 action bar 提示(不刷聊天栏)。 */
    public boolean foreignDamageFilterHint = true;
    /** 额外放行的模组命名空间(逗号分隔,如 "somemod,othermod");minecraft 与 yongye 始终放行。 */
    public String foreignDamageFilterExtraNamespaces = "";

    // ============ m206:全物品标识(m214 起默认文案 DY:乔大仙) ============
    /** 悬停任意物品(原版+模组)的提示栏末尾追加一行标识;关掉即不显示。 */
    public boolean enableItemWatermark = true;
    /** 标识文字(所有物品统一显示,想换字改这里)。 */
    public String itemWatermarkText = "DY:乔大仙";

    // ============ m208:海报技能补齐(坦克真减伤 / 剑客剑气凌空) ============
    /** 坦克:所受一切伤害(含无视护甲的真实伤害)按此比例直接减免;0 关闭。 */
    public double tankFlatReductionFraction = 0.15;
    /** 剑客:剑气攒满 10 层后,下一次近战命中沿视线放出「剑气凌空」直线穿透(打完清零重攒)。 */
    public double swordsmanPierceRange = 12.0;
    public double swordsmanPierceDamage = 10.0;
    /** m190:外模组武器打怪被判无效时,怪物在聊天栏开口嘲讽(内置 20 条台词随机抽,如「哎呦喂,您拿前朝的剑,斩本朝的官?」)。 */
    public boolean foreignDamageTaunt = true;
    /** 嘲讽冷却:同一玩家两句嘲讽之间的最短间隔(tick,20=1秒;防连点刷屏)。 */
    public int foreignDamageTauntCooldownTicks = 60;
    /** 追加嘲讽台词:竖线 | 分隔的自定义台词,追加进内置台词池(台词里别用竖线;空=只用内置)。 */
    public String foreignDamageTauntExtraLines = "";

    // ============ m211:武器随强化等级动态染色 ============
    /** 职业武器+混沌之刃随强化等级变色:≤起始级保持纯黑白,越高越鲜艳(冰蓝→紫→品红→正红,全程无绿无黄);关=永远黑白。 */
    public boolean weaponTintEnabled = true;
    /** 染色起始强化等级:不超过它保持纯黑白(默认 100=「稀有」品质门槛)。 */
    public int weaponTintStartLevel = 100;
    /** 染色封顶强化等级:达到即纯红,再高不再变(默认 2500=「至尊」品质门槛)。 */
    public int weaponTintEndLevel = 2500;

    // ============ m212:夜蚀群系 ============
    /** 夜蚀群系总开关(侵蚀转化 + 全生物敌化 + 侵蚀掉落 + 自然侵蚀)。 */
    public boolean enableBlight = true;
    /** 自然侵蚀起始天数:第 N 天起才可能自然出现侵蚀区(命令 /yongye blight 不受限)。 */
    public int blightStartDay = 1;   // m338:第 1 天即可自然侵蚀(作者:11 天太慢)
    /** 每次检定自然出现新侵蚀区的概率。 */
    public double blightSeedChance = 0.03;
    /** 自然侵蚀检定间隔(tick,1200=1 分钟)。 */
    public int blightSeedCheckIntervalTicks = 1200;
    /** 自然侵蚀区半径(格)。 */
    public int blightZoneRadius = 40;
    /** 群系内全生物敌化的索敌半径(以玩家为中心,格)。 */
    public int blightAggroRange = 24;
    /** 被动生物(牛羊鸡猪村民等)在群系内攻击玩家的单次伤害(3.0=1.5 颗心)。 */
    public double blightPassiveDamage = 3.0;
    /** 被动生物追击玩家的移动速度倍率。 */
    public double blightPassiveSpeed = 1.25;
    /** 侵蚀掉落是否要求玩家击杀(关=摔死烧死也掉,可被刷)。 */
    public boolean blightDropRequirePlayerKill = true;
    /** 群系内死亡生物掉落永夜之尘的概率与数量上限。 */
    public double blightDustChance = 0.5;
    public int blightDustMax = 2;
    /** 被动生物额外:生命碎片概率。 */
    public double blightShardChance = 0.20;
    /** 怪物额外:裂隙碎片概率。 */
    public double blightRiftChance = 0.12;
    /** 全员极小概率:深渊魂晶。 */
    public double blightCrystalChance = 0.015;

    // ============ m263:BOSS 出场(皮肤 BOSS 出场演出 + 基础血量) ============
    /** 出场演出总开关:皮肤 BOSS(阿努比斯/凤凰/死亡法师/红蜘蛛/自建龙/佩恩)登场时
     *  给范围内玩家整屏标题+镜头重震+闪光+凋灵吼+魂火螺旋。 */
    public boolean enableBossEntrance = true;
    /** 出场演出作用半径(格)。 */
    public int bossEntranceRange = 48;
    /** 出场镜头震动强度(m239 打击感同一量纲;1.4≈击杀级重震)。 */
    public double bossEntranceShake = 1.4;
    /** 五只皮肤 BOSS 的出场基础血量(生成后还会再吃天数成长 + 玩家攻击对位,只增不减;
     *  改这些值需重启生效——属性在实体注册时烘焙)。 */
    public double anubisBaseHealth = 1.5E6;   // m339 加强
    public double phoenixBaseHealth = 6.0E5;
    public double deathMageBaseHealth = 4.5E5;
    public double redSpiderBaseHealth = 3.75E5;
    public double toroDragonBaseHealth = 9.0E5;

    // ============ m264:蚀矿(只在被侵蚀的土地上出现) ============
    /** 侵蚀区生成时每个区块播种的矿脉数(0=关)。 */
    public int blightOreVeinsPerChunk = 2;
    /** 单条矿脉的方块数上限(1~9 合理)。 */
    public int blightOreVeinSize = 5;
    /** 蚀矿缓慢生长检定间隔(tick,1200=1 分钟;老侵蚀区也能长出新矿)。 */
    public int blightOreGrowIntervalTicks = 1200;
    /** 每次检定为身处侵蚀区的每名玩家生长 1 块蚀矿的概率。 */
    public double blightOreGrowChance = 0.35;

    // ============ m265:夜蚀套装(灵魂绑定) ============
    /** 夜蚀盔甲灵魂绑定总开关:认主(第一个拿到的人)、别人捡不起、死亡不掉落重生归还。 */
    public boolean blightArmorSoulbound = true;
    /** m338 锻造爆震:夜蚀套装合成成功瞬间爆炸(演出+自伤,夜蚀之力不稳定)。 */
    public boolean blightForgeBlast = true;
    public double blightForgeBlastDamage = 6.0;      // 爆震自伤(3 颗心,magic 源)

    // ============ m268:皮肤 BOSS 技能包(伤害均为施放时点的基础值,不吃成长缩放) ============
    // —— 浴火凤凰 ——
    /** 烈焰吐息:冷却 / 单段伤害(直线火舌,命中点燃)。 */
    public int phoenixBeamCooldownTicks = 120;
    public double phoenixBeamDamage = 45.0;
    /** 火焰龙卷:冷却 / 伤害(目标脚下起火旋风,命中击飞+点燃)。 */
    public int phoenixTornadoCooldownTicks = 220;
    public double phoenixTornadoDamage = 38.0;
    /** 浴火重生(一次性):血量跌破该比例时蜷入烈焰之卵 5 秒无敌,随后回复最大血量×healRatio 并爆炎。 */
    public double phoenixRebirthThreshold = 0.30;
    public double phoenixRebirthHealRatio = 0.40;
    // —— 死亡法师 ——
    /** 魂火锁定:冷却 / 伤害(目标脚下魂火标记 1.25 秒后爆燃,附凋零)。 */
    public int mageStrikeCooldownTicks = 110;
    public double mageStrikeDamage = 52.0;
    /** 亡者音爆:冷却 / 伤害(近身范围击退+缓速)。 */
    public int mageNovaCooldownTicks = 180;
    public double mageNovaDamage = 38.0;
    /** 虚影闪现:被贴脸挨打后闪现到目标侧后方的冷却。 */
    public int mageBlinkCooldownTicks = 150;
    // —— 红蜘蛛 ——
    /** 蛛网陷阱:冷却(目标脚下铺蛛网+中毒)。 */
    public int spiderWebCooldownTicks = 150;
    /** 猛扑:冷却 / 落地范围伤害。 */
    public int spiderPounceCooldownTicks = 120;
    public double spiderPounceDamage = 38.0;
    /** 蛛群咆哮(一次性):血量跌破该比例时怒吼召唤 spiderBroodCount 只毒液蜘蛛围攻。 */
    public double spiderBroodHealthThreshold = 0.5;
    public int spiderBroodCount = 4;
    // —— 自建末影龙 ——
    /** 龙息射线:冷却 / 伤害(直线龙息,命中缓速)。 */
    public int toroBreathCooldownTicks = 110;
    public double toroBreathDamage = 45.0;
    /** 俯冲冲撞:冷却 / 撞击伤害(锁定目标高速俯冲,撞点范围伤害+大击退)。 */
    public int toroDiveCooldownTicks = 170;
    public double toroDiveDamage = 68.0;
    /** 重力撕裂(一次性):血量跌破该比例时 24 格内玩家被龙威掀上天(漂浮+伤害)。 */
    public double toroGravityHealthThreshold = 0.4;

    // ============ m217:战斗爽难度 ============
    /** m251 起弃用:反滚雪球在战斗爽已明确关闭(见 DifficultyManager.growthSuppressionOn),减半回拉不再被读取,保留占位防旧 json 报死键。 */
    public double battleFunSnowballRelief = 0.5;
    /** 战斗爽难度下永夜 V5+ 深渊倍增的缩放:每级倍率 step×(等级-5) 再乘此值(1=不减弱,0.5=减半,0=该机制在战斗爽中关闭)。 */
    public double battleFunBeyondScale = 0.5;

    // ============ m223:召唤师职业 ============
    /** 「召唤」一次召出的铁傀儡数量。 */
    public int ultSummonerGolemCount = 5;
    /** 傀儡寿命(秒),到点自散;再次召唤会先散掉上一批。 */
    public int ultSummonerGolemLifeSec = 60;
    /** 「强化」倍率:傀儡血/攻各 ×(1+此值),默认 1.0=翻一倍。 */
    public double summonerGolemBoostMult = 1.0;
    /** 「癫狂」(大招键,m232 起不再需要潜行)消耗的生命值。 */
    public double ultSummonerFrenzyHpCost = 20.0;
    /** 「癫狂」力量II+速度II 的持续时间(tick)。 */
    public int ultSummonerFrenzyDurationTicks = 400;
    /** 肝帝:生命 / 攻击 / 移速 / 寿命秒(注册期读取,改后需重启生效)。 */
    public double gandiHealth = 300.0;
    public double gandiAttack = 40.0;
    public double gandiSpeed = 0.35;
    public int gandiLifeSec = 60;
    /** 肝帝台词(登场/战斗/闲聊/告别,只发给召唤他们的朋友):关=全员沉默。 */
    public boolean gandiChatEnabled = true;
    // —— m227 肝帝台词池:竖线 | 分隔句子,随机抽一句;清空该字段=该类台词沉默。
    //    Debug「配置」页有查改入口;改句示例:/yongye config set gandiTalkDaofengSpawn 句1|句2
    public String gandiTalkDaofengSpawn  = "岛风到位!这地形我看看能改点啥。|圆梦镇施工队,进场!";
    public String gandiTalkDaofengCombat = "打架别拆我建筑啊!|先围一圈墙,稳住!";
    public String gandiTalkDaofengIdle   = "这块地……适合盖个圆梦镇。|薰衣草配夜蚀,还挺搭。";
    public String gandiTalkDaofengBye    = "我先回去画图纸了,下次见!";
    public String gandiTalkDaofengDeath  = "工地……先塌一半……";
    public String gandiTalkWananSpawn    = "晚安已上线,生电机器马上开转。|别慌,后勤交给我。";
    public String gandiTalkWananCombat   = "傀儡耐久我包了,放心冲!|效率!效率!";
    public String gandiTalkWananIdle     = "这刷铁机一小时能出三组……|红石一响,黄金万两。";
    public String gandiTalkWananBye      = "机器停了,我也该睡了,晚安~";
    public String gandiTalkWananDeath    = "机器,烧了……";
    public String gandiTalkBuganSpawn    = "不爱肝?骗人的,一百万方块都肝完了。|重活来了?正好活动筋骨。";
    public String gandiTalkBuganCombat   = "站我后面!这波我扛!|这点伤害,还没搬砖累。";
    public String gandiTalkBuganIdle     = "下个项目复刻白熊山,你说行吗?|肝到天亮,不算什么。";
    public String gandiTalkBuganBye      = "行了,回去继续搬我的百万方块。";
    public String gandiTalkBuganDeath    = "这波,扛不住了……";
    public String gandiTalkMirenSpawn    = "迷人参上,蒸汽机压满!|机械之城的火,借你用用。";
    public String gandiTalkMirenCombat   = "给傀儡点火!全速输出!|别省煤,烧就完了!";
    public String gandiTalkMirenIdle     = "回头带你看我的飞艇船坞。|机械之城,今晚亮灯。";
    public String gandiTalkMirenBye      = "蒸汽散了……我也撤了。";
    public String gandiTalkMirenDeath    = "锅炉,炸了……";
    public String gandiTalkJiemoSpawn    = "肝痒痒了,该活动一下了。|芥末到岗,今天也要肝到发光。";
    public String gandiTalkJiemoCombat   = "手不能停,肝就完事!|打怪也是肝,冲!";
    public String gandiTalkJiemoIdle     = "闲着也是闲着,再肝一单。|这点活,不够我塞牙缝的。";
    public String gandiTalkJiemoBye      = "先下了,回床上躺会儿……明天接着肝。";
    public String gandiTalkJiemoDeath    = "肝……肝不动了……";
    // —— m229 召唤物成长与鹰扬 ——
    /** 召唤物(傀儡+肝帝)附加生命 = 召唤者(朋友)最大生命 × 此比例(随你一起成长)。 */
    public double summonerOwnerHpRatio = 0.5;
    /** 召唤物附加攻击 = 召唤者攻击 × 此比例。 */
    public double summonerOwnerAtkRatio = 0.5;
    /** 手持「鹰扬」且本职业生效时,铁傀儡强化倍率额外加成。 */
    public double summonerStaffExtraBoost = 0.5;

    // —— m232 职业小技能(默认 C 键,与大招各自冷却互不占用) ——
    /** 职业小技能总开关。 */
    public boolean enableClassMinorSkill = true;
    /** 小技能冷却(tick,默认 300=15 秒;铁傀儡召唤也走这条,不再吃大招 CD)。 */
    public long minorSkillCooldownTicks = 300;
    /** 肉盾·盾击:半径 / 伤害(附带击退+缓慢II 3秒)。 */
    public double minorTankRadius = 3.0;
    public double minorTankDamage = 6.0;
    /** 战士·战吼:半径 / 持续(tick;周围怪虚弱+缓慢,自身力量I)。 */
    public double minorWarriorRadius = 6.0;
    public int minorWarriorDurationTicks = 100;
    /** 术士·生命虹吸:半径 / 伤害 / 每命中回血。 */
    /** m262 起弃用(小技能已改「暗影分身」):虹吸三字段不再被读取,保留占位。 */
    public double minorWarlockRadius = 4.0;
    public double minorWarlockDamage = 8.0;
    public double minorWarlockHealPerHit = 2.0;
    // —— m262 术士小技能·暗影分身(替换生命虹吸;按主人快照) ——
    /** 召唤分身数量。 */
    public int minorWarlockCloneCount = 2;
    /** 分身血量=主人最大生命×此比例。 */
    public double minorWarlockCloneHpRatio = 0.5;
    /** 分身攻击=主人攻击×此比例。 */
    public double minorWarlockCloneAtkRatio = 1.0;
    /** 分身寿命(tick,600=30 秒,到点魂火散场)。 */
    public int minorWarlockCloneLifeTicks = 600;
    /** 剑客·剑气斩:前方剑气距离 / 伤害。 */
    public double minorSwordsmanRange = 6.0;
    public double minorSwordsmanDamage = 8.0;
    /** 武僧·金钟罩:持续(tick;抗性II+回复I)。 */
    public int minorMonkDurationTicks = 100;
    /** 刺客·疾影步:冲刺力度 / 加速持续(tick,速度II)。 */
    public double minorAssassinDashStrength = 1.6;
    public int minorAssassinSpeedTicks = 60;

    // —— m233 召唤师强化包(三技能职业,每一件都要够硬) ——
    /** 傀儡持续回血(每秒,0=关):在场傀儡缓慢自愈,「强化」的血量翻倍更耐用。 */
    public double summonerGolemRegenPerSec = 2.0;
    /** 统御被动:场上有自己的召唤物(傀儡/朋友)存活时,召唤者获得抗性I。 */
    public boolean enableSummonerGuardAura = true;
    /** 统御:召唤物存活数达到此值时抗性升 II 级(默认 5=一整队傀儡)。 */
    public int summonerGuardAuraBigCount = 5;
    /** 癫狂自身增益等级(amplifier,0起):力量默认 2=力量III,速度默认 1=速度II。 */
    public int ultSummonerFrenzyPowerAmp = 2;
    public int ultSummonerFrenzySpeedAmp = 1;

    // —— m234 技能吃攻击力(统一「基础值 + 攻击 × 倍率」,与 m72 武器技能同公式;倍率 0=回固定值老行为) ——
    /** 大招:旋风斩 / 灭世 / 百裂拳 / 万剑归一 的攻击倍率。 */
    public double ultWarriorAttackRatio   = 2.0;
    public double ultWarlockAttackRatio   = 3.0;
    public double ultMonkAttackRatio      = 1.5;
    public double ultSwordsmanAttackRatio = 2.5;
    /** 小技能:盾击 / 剑气斩 的攻击倍率(minorWarlockAttackRatio 自 m262 弃用——虹吸已改分身,保留占位)。 */
    public double minorTankAttackRatio      = 0.5;
    public double minorWarlockAttackRatio   = 0.8;
    public double minorSwordsmanAttackRatio = 1.0;
    /** 被动:术士潜行AOE / 剑客剑气凌空 的攻击倍率(算完再乘持职业武器的 ×1.5)。 */
    public double warlockAoeAttackRatio     = 0.8;
    public double swordsmanPierceAttackRatio = 1.0;

    // —— m239 沉浸式战斗手感(打击感:镜头微震 + FOV 顿挫 + 命中粒子 + 击杀闪光/确认音;纯视听反馈不改伤害) ——
    /** 战斗手感总开关。 */
    public boolean enableCombatFx = true;
    /** 镜头抖动强度倍率(0=关抖动,1=默认,2=加倍)。 */
    public double combatFxShakeScale = 1.0;
    /** FOV 顿挫强度倍率(命中瞬间视野轻微拉近;0=关)。 */
    public double combatFxFovKick = 1.0;
    /** 命中/击杀时在怪身上补打击粒子(CRIT 火花,击杀加消散烟)。 */
    public boolean combatFxParticles = true;
    /** 击杀时屏幕淡金闪光一瞬。 */
    public boolean combatFxKillFlash = true;
    /** 击杀确认音(经典"叮")。 */
    public boolean combatFxKillSound = true;
    /** m373 伤害飘字:命中怪物时在其身上弹出漂浮伤害数字(普通=白字/重击=金色大字)。 */
    public boolean enableDamageNumbers = true;
    /** 伤害飘字大小倍率(0.3~3.0,1=默认;渲染端钳制)。 */
    public double damageNumberScale = 1.0;
    /** m374 受击方向指示:挨打瞬间准星四周对应方向弹红色弧形指示(转视角实时对齐来源)。 */
    public boolean enableHurtDirectionFx = true;
    /** m375 UI 动效:界面开场淡入 + 按钮悬停过渡/按压下沉/入场上浮三件套(关=回旧静态观感)。 */
    public boolean enableUiFx = true;
    /** m376 稀有掉落光柱:地上稀有掉落起品质色光柱(蓝=稀有/紫=史诗/金=传说,纯客户端)。 */
    public boolean enableLootBeam = true;
    /** m377 永夜氛围粒子:永夜等级≥1 玩家四周空中飘灰烬浮尘,等级越高越浓(纯客户端)。 */
    public boolean enableNightAmbientParticles = true;
    /** 永夜氛围粒子浓度倍率(0~3,0=关,1=默认)。 */
    public double nightAmbientDensity = 1.0;
    /** m380 永夜升级/消退转场演出(升级=压暗+心跳+血红字幕;赎夜=金色微光+铃音)。 */
    public boolean enableNightfallTransition = true;
    /** 转场强度倍率(0~2,0=关,1=默认;同时缩放罩浓度与音量)。 */
    public double transitionIntensity = 1.0;
    /** 全局弱闪光模式:所有整屏闪光/压暗类特效强度减半(低刺激;后续闪光类 FX 均须查询本项)。 */
    public boolean reduceScreenFlash = false;
    /** m381 特效质量档:0=OFF 全关 / 1=LOW / 2=MEDIUM / 3=HIGH(默认)。装饰性特效统一走 FxBudget 降级。 */
    public int fxQuality = 3;
    /** m382 击杀连锁演出:短时多杀弹中屏大字(双杀/三杀/五连绝灭…)+升调音。 */
    public boolean enableMultiKillFx = true;
    /** m383 命中音材质分层:打骨=脆响/硬甲=铿锵/肉=闷响(原版音变调零新资源,共用手感节流不炸耳)。 */
    public boolean enableCombatHitSound = true;
    /** m384 死亡/重生转场:死亡黑幕渐入暗纱、重生黑幕渐出+「第 N 天·永夜阶段」状况提示。 */
    public boolean enableDeathTransition = true;
    /** m385 怪物头顶微型血条:最近命中的怪头顶显 3 秒插值血条(精英紫条+金线+菱记;BOSS 走画框条不重复)。 */
    public boolean enableMobHealthBar = true;
    /** m386 拾取通知卡:稀有+物品真正进包时屏幕右缘滑入品质色通知卡(背包差分,满包/被截胡不误报)。 */
    public boolean enablePickupNotice = true;
    /** m387 BOSS 讨伐终结演出:击杀 BOSS 顿帧加长+金闪+「◆ 讨伐成功 ◆」字幕+凯旋音(只作用击杀者客户端)。 */
    public boolean enableBossKillFx = true;
    /** m388 永夜环境音景:永夜≥2 每 20~40s(≥4 级 12~28s)远处随机幽响,战斗中降概率、演出避让。 */
    public boolean enableNightAmbientSound = true;
    /** 音景音量(0~2,0=静音等效关;默认 0.6 刻意压低当底噪)。 */
    public double nightAmbientSoundVolume = 0.6;

    // —— m240 拔刀剑式攻击动画(学习 SlashBlade-Refabricated:斩击轨迹弧光 + 第三人称三式连击姿态;纯视觉零伤害改动) ——
    /** 斩击轨迹总开关(挥砍出刀光,颜色随武器强化等级染色管线走)。 */
    public boolean enableSlashFx = true;
    /** 第三人称拔刀姿态(挥砍时身体拧转+持械臂大弧度摆动,三式连击循环)。 */
    public boolean slashFxPose = true;
    /** 原版近战武器(剑/斧/三叉戟)也出斩击;关=只有本模组武器出。外模组武器恒不出(伤害本就被过滤)。 */
    public boolean slashFxVanillaWeapons = true;
    /** MoBends 式全身发力(m243):蓄力反向包络+躯干前倾大拧+头部视线锁定反补+攻击弓步;关=回 m242 简版姿态。 */
    public boolean slashFxBends = true;
    /** 状态动作(m242,学 SlashBlade 的状态触发式):空中=回旋斩、疾跑=突进突刺、潜行=居合横斩;关=只保留地面连击。 */
    public boolean slashFxContextMoves = true;
    // —— m259 武器右键格挡(法杖除外;有格挡值,被打掉即破防) ——
    /** 武器格挡总开关。 */
    public boolean enableWeaponGuard = true;
    /** 格挡值上限=最大生命×此比例(跟随成长曲线,后期照样挡得动)。 */
    public double guardMaxHealthFraction = 0.6;
    /** 格挡值上限保底(开局血量低时不至于一戳就破)。 */
    public double guardMinValue = 20.0;
    /** 回复:每秒回 上限×此比例(0.08≈12.5 秒回满)。 */
    public double guardRegenFractionPerSec = 0.08;
    /** 回复延迟:距上次挡下超过此 tick 才开始回复(40=2 秒)。 */
    public int guardRegenDelayTicks = 40;
    /** 破防硬直:被击穿后多少 tick 内无法格挡,期满格挡值直接回满(100=5 秒)。 */
    public int guardBreakRecoverTicks = 100;
    /** 举盾心跳时长(tick):右键每 4t 重发一次交互,8=松开右键约 0.4 秒后自然放下。 */
    public int guardHoldTicks = 8;
    /** 正面判定:攻击者方向与视线的点积下限(0.15≈只挡前方约 160° 扇面,背刺挡不住)。 */
    public double guardFrontalDot = 0.15;

    // ============ m269:完美格挡·弹反 ============
    /** 完美格挡开关:起手瞬间接住攻击 → 全免不耗格挡值+反噬+弹开攻击者+反击强化。 */
    public boolean enableParry = true;
    /** 弹反判定窗口(tick,6=0.3 秒;心跳续期不刷新,按住不放蹭不出来)。 */
    public int parryWindowTicks = 6;
    /** 反噬伤害=被挡伤害×此比例(保底自己一刀的攻击力;只反 6 格内的近身攻击者)。 */
    public double parryReflectFraction = 1.0;
    /** 弹反成功后自身反击强化时长(tick,60=3 秒力量II+速度I)。 */
    public int parryBuffTicks = 60;

    // ============ m270:处决斩杀 ============
    /** 处决开关:近战把敌对怪打进斩杀线 → 这刀直接终结(魂柱+暴击雨+终结音)。 */
    public boolean enableExecute = true;
    /** 斩杀线:剩余血量 ≤ 最大血量×此比例即触发。 */
    public double executeThresholdFraction = 0.12;
    /** 最大血量 ≥ 此值的目标豁免处决(BOSS 该一刀一刀磨;m263 后 BOSS 全在 25 万+)。 */
    public double executeBossHpExempt = 50000.0;

    // ============ m272:夜蚀共鸣(套装加成) ============
    /** 每穿 1 件夜蚀盔甲:最大生命 +此比例(乘基础值,随成长曲线走)。 */
    public double blightSetHpPct = 0.10;
    /** 每穿 1 件夜蚀盔甲:攻击力 +此比例。 */
    public double blightSetAtkPct = 0.06;
    /** 集齐 4 件夜蚀盔甲:移动速度 +此比例。 */
    public double blightSetSpeedPct = 0.10;

    // ============ m273:连击计数器 ============
    /** 连击系统开关:命中累计连击数,HUD 显示,高连击加攻速/伤害。 */
    public boolean enableCombo = true;
    /** 断连时限(tick):这么久没打中任何怪,连击清零(100=5 秒)。 */
    public int comboTimeoutTicks = 100;
    /** 每 5 连击:伤害 +此比例(ADD_MULTIPLIED_TOTAL)。 */
    public double comboDamagePerTier = 0.04;
    /** 连击伤害加成封顶。 */
    public double comboDamageCap = 0.40;
    /** 每 5 连击:攻速 +此比例。 */
    public double comboSpeedPerTier = 0.03;
    /** 连击攻速加成封顶。 */
    public double comboSpeedCap = 0.30;
    /** m279 连击华丽特效开关:升档冲击环+称号弹字(凌厉/狂怒/无双/灭世)+升调音效+高档辉光抖动+断连提示;关=素版数字。 */
    public boolean enableComboFancyFx = true;
    /** m281 夜蚀装备不可摧毁:掉落物免疫火/岩浆/爆炸、永不消失、虚空自动归还主人、怪物捡不走、精英缴械抢不走、定时清理豁免、耐久永不损毁。 */
    public boolean blightArmorIndestructible = true;
    /** m287 濒死危机演出:血量低于阈值,屏幕边缘血红渐晕随心跳呼吸+监守者心跳音,越残越浓越急(纯客户端观感)。 */
    public boolean enableLowHpFx = true;
    /** m287 濒死阈值:血量比例低于此值触发危机演出。 */
    public double lowHpFxThreshold = 0.20;
    /** m288 战况看板:左上角显示 天数 · 累计击杀 · 下一阶段预告(含久留升层倒计时)。 */
    public boolean enableHudInfoPanel = true;
    /** m308 看板紧凑模式:三行全换短文案(预告行如「5天后:佩恩降临+2」),宽度约省一半。false=m289 完整长文案。 */
    public boolean hudInfoCompact = true;
    /** m308 看板停靠位:0=左中 1=左上(m312 起默认,作者点名) 2=左下 3=右上 4=右中 5=右下。/yongye config set hudInfoAnchor N 即改。 */
    public int hudInfoAnchor = 1;
    /** m346 技能CD常显HUD:R/G/V(武器技能)+X(大招)+C(小技能)剩余冷却常驻显示在血条面板左沿外,
     *  就绪金键绿字、冷却灰名橙秒+蓝色恢复进度线;服务端每10t下发,客户端本地递减保平滑。关=不发包不显示。 */
    public boolean enableSkillCdHud = true;
    /** m346 技能CD HUD 微调偏移X(GUI 像素,正=向右;基准=右缘贴血条面板左沿外)。 */
    public int skillCdHudOffsetX = 0;
    /** m346 技能CD HUD 微调偏移Y(GUI 像素,正=向下;基准=底行 h-50 向上堆)。 */
    public int skillCdHudOffsetY = 0;
    /** m365 BOSS血条整体缩放(作者点名「血条还是很大,要缩小」):对自绘画框血条的框/槽/名字/血量数字/行距
     *  整体乘算,1.0=旧大小,默认0.7;设置屏「界面·HUD」有预设档,/yongye config set bossBarScale 任意值可微调
     *  (渲染时钳到 0.3~1.5)。原版样式血条(无画框的)不受影响。 */
    public double bossBarScale = 0.7;
    /** m348 新手前3天引导:引导期内按间隔 actionbar 提示第一件没做的事(选职→学书→强化→找核心,
     *  都做了轮播核心箭头/任务书/第5天BOSS预警),每次登录另发一条聊天版路线总纲。 */
    public boolean enableNewbieGuide = true;
    /** m348 引导持续天数:游戏天数小于此值才提示(0=等于关)。 */
    public int newbieGuideDays = 3;
    /** m348 引导提示间隔(秒,下限 10 防刷屏)。 */
    public int newbieGuideIntervalSeconds = 45;
    /** m350 任务书节点地图:主线 16 阶段画成蛇形节点链(完成✔墨绿/当前金色呼吸/未解锁暗灰,连线走过变金,
     *  点节点选中看详情,悬停浮条),试炼页同款 3 节点横链;关=回旧双列按钮列表。 */
    public boolean enableQuestNodeMap = true;
    /** m351 任务书 BOSS 图鉴页:七 BOSS(红蛛/死法/凤凰/托罗龙/阿努比斯/佩恩/末影龙)各显解锁天数(实时配置)、
     *  个人击杀次数(逐 BOSS 计数,死亡保留)、弱点打法、掉落预览;关=页签不显示且不发图鉴包。 */
    public boolean enableBossAtlasPage = true;
    /** m352 事件限定天象视觉:血月事件才红月、酸雨事件才绿雨(贴图不再常驻覆盖原版,治「天天血月」);
     *  关=天象事件也用原版月亮/雨(玩法效果不受影响)。 */
    public boolean enableEventSkyVisuals = true;
    /** m356 材料仓库:无限堆叠的成长物资仓库(强化材料/强化石/技能书/终焉精华/保护卷),
     *  背包「仓库」钮开界面,一键存入/按行取出,数据存玩家附件死亡不丢;关=服务端拒绝存取。 */
    public boolean enableVault = true;
    /** m357 自动入库:每 5 秒把背包区(9~35 格)的可入库材料静默收进仓库;**热栏 0~8 刻意不收**——
     *  手上/热栏留的书和石头代表玩家想手动用。关=只走界面手动存。 */
    public boolean vaultAutoDeposit = true;
    /** m357 学书仓库直供:背包「学书」按钮把仓库里存的技能书也一并学掉(自动存开着时必须开,否则学书扑空)。 */
    public boolean vaultAutoUseBooks = true;
    /** m359 强化仓库直供:背包「强化」一键强化时,仓库里的强化石/传统材料按同一分账语义并入
     *  (传统材料先扣碎裂不退,强化石成功后才扣);关=只吃背包材料。 */
    public boolean vaultUseForEnhance = true;
    /** m361 主线目标常显:战况看板第 4 行显示当前主线阶段与进度(「主线【见血】杀怪 13/20」),
     *  已达成转绿提醒开任务书领奖——目标永远钉在屏幕上,治「没有东西推着前进」。 */
    public boolean enableMainQuestHud = true;
    /** m363 渐进解锁:背包功能按钮随主线阶段逐个点亮(常驻=成长/任务/设置;阶段1=学书合书仓库;
     *  阶段2=强化装备兑换转移;阶段5=饰品;天赋/本命=选职即亮),升档金字播报「新功能解锁」;
     *  前期界面干净、每阶段都有开新玩具的爽点(玩家反馈「太多了又很乱」)。关=全部按钮常驻(老玩家口径)。 */
    public boolean enableProgressiveUnlock = true;

    // ============ m364 每日悬赏(玩家反馈「没啥内容」方案B) ============
    /** 每日悬赏总开关:第 2 天起每个游戏日随机 3 张(讨伐/猎首/锻造/坚守),完成自动发奖,
     *  三张全清攒连击、连击给次日奖励加成;任务书第 5 页签「悬赏」查看。关=不生成、页签隐藏。 */
    public boolean enableDailyBounty = true;
    /** 讨伐目标基数:击杀怪物数 = 基数 + 天数×2(封顶 200)。 */
    public int bountyKillBase = 12;
    /** 猎首目标基数:击杀精英数 = 基数 + 天数/4(封顶 12)。 */
    public int bountyEliteBase = 2;
    /** 锻造目标基数:强化提升级数 = 基数 × 当天强化石基准档面值 10^(档-1)(跟随石头经济)。 */
    public int bountyEnhanceBase = 30;
    /** 坚守目标:当日累计存活分钟数(死亡当日进度清零)。 */
    public int bountySurviveMinutes = 8;
    /** 单张悬赏奖励:强化石颗数(档位=当天基准档+1,精英档;连击加成后至少 1 颗)。 */
    public int bountyRewardStones = 2;
    /** 单张悬赏奖励:终焉精华个数(连击加成后至少 1 个)。 */
    public int bountyRewardEssence = 1;
    /** 连击加成:每层连击奖励 +N%(目标不变只加奖励)。 */
    public int bountyStreakBonusPercent = 25;
    /** 连击封顶层数(默 4 = 奖励最高 ×2.0)。 */
    public int bountyStreakCap = 4;

    // ============ m366 猎杀勋章(击杀里程碑三选一,作者定稿甲案:永久小加成/独立记账) ============
    /** 猎杀勋章总开关:累计击杀达到里程碑 → 弹三选一卡,选一枚永久小加成勋章(独立成长线,
     *  不碰技能书/强化/职业数据;动态对位剔除勋章乘区=真收益)。关=不计数不弹卡、已获得加成随之卸下。 */
    public boolean enableHuntMedal = true;
    /** 首个里程碑所需击杀数。 */
    public int huntMilestoneBase = 10;
    /** 每完成一次里程碑,下一次所需击杀数递增量(线性曲线:第 k 次 = base + k×growth,后期越杀越久才弹)。 */
    public int huntMilestoneGrowth = 6;
    /** 猛攻勋章:每层攻击伤害 +N%(ADD_MULTIPLIED_TOTAL 乘在一切之后)。 */
    public double huntMedalAttackPct = 2.0;
    /** 体魄勋章:每层最大生命 +N%。 */
    public double huntMedalHealthPct = 2.0;
    /** 迅捷勋章:每层移动速度 +N%。 */
    public double huntMedalSpeedPct = 1.0;
    /** 坚壁勋章:每层护甲值 +N%(乘装备提供的总护甲)。 */
    public double huntMedalArmorPct = 2.0;
    /** 疾手勋章:每层攻击速度 +N%。 */
    public double huntMedalAtkSpeedPct = 1.5;
    /** 不屈勋章:每层护甲韧性 +N%(乘装备提供的总韧性)。 */
    public double huntMedalToughnessPct = 2.0;

    // ============ m310 僵尸红眼+紫光(作者点名,纯客户端观感) ============
    /** 所有僵尸(僵尸/尸壳/溺尸/僵尸村民)眼睛发红光,暗处也亮 */
    public boolean zombieRedEyes = true;
    /** 紫气总开关:m311 起扩为全怪分档——普通怪轻微/精英中等/BOSS高等(僵尸不再特殊) */
    public boolean zombiePurpleAura = true;
    /** m311 紫气密度倍率(0~4):嫌淡拉高、嫌卡调低,0=只留 BOSS 螺旋也没有=等于关 */
    public double mobAuraScale = 1.0;
    /** m308 看板微调偏移X(GUI 像素,正=向右;叠加在停靠位上,越界自动钳回屏内)。m312 默认 -2(作者点名)。 */
    public int hudInfoOffsetX = -2;
    /** m308 看板微调偏移Y(GUI 像素,正=向下)。m312 默认 14(作者点名)。 */
    public int hudInfoOffsetY = 14;

    // ============ m274:BOSS 半血狂暴 ============
    /** BOSS 阶段转换开关:六只皮肤 BOSS + 佩恩血量跌破阈值 → 狂暴变招 + 全场演出。 */
    public boolean enableBossRage = true;
    /** 狂暴触发阈值(血量比例)。 */
    public double bossRageThreshold = 0.5;
    /** 狂暴后攻击力 +此比例。 */
    public double bossRageAtkPct = 0.35;
    /** 狂暴后移动速度 +此比例。 */
    public double bossRageSpeedPct = 0.25;

    // ============ m275:击杀顿帧 ============
    /** 顿帧开关:重击/击杀命中瞬间,第一人称挥臂定住几个 tick(时停感)。 */
    public boolean enableCombatFxHitstop = true;
    /** 顿帧时长倍率(重击 2t、击杀 4t 的基准上乘;0.5~2 合理)。 */
    public double combatFxHitstopScale = 1.0;

    // ============ m276:自动强化卷 / 自动吃书卷 ============
    /** 自动卷轴时长(tick,1200=60 秒;重复使用叠加,封顶 5 分钟)。 */
    public int autoScrollDurationTicks = 1200;
    /** 自动强化间隔(tick):生效期间每隔这么久,吞掉背包(含潜影盒)全部强化材料强化一次。 */
    public int autoEnhanceIntervalTicks = 60;
    /** 自动吃书间隔(tick):生效期间每隔这么久,自动学一本背包(含潜影盒)里的技能书/血量书。 */
    public int autoBookIntervalTicks = 30;
    /** 敌对怪死亡掉落自动强化卷的概率。 */
    public double autoEnhanceScrollDropChance = 0.003;
    /** 敌对怪死亡掉落自动吃书卷的概率。 */
    public double autoBookScrollDropChance = 0.003;
    /** 任务奖励里出自动强化卷的基础概率(随永夜等级小幅上浮)。 */
    public double questAutoEnhanceScrollChance = 0.10;
    /** 任务奖励里出自动吃书卷的基础概率(随永夜等级小幅上浮)。 */
    public double questAutoBookScrollChance = 0.10;

    // —— m258 空中回旋斩范围伤害(七式之四不再纯视觉:转一圈扫一圈) ——
    /** 空中回旋斩范围伤害总开关。 */
    public boolean enableSpinSlashAoe = true;
    /** 回旋斩半径(格)。 */
    public double spinSlashRadius = 3.5;
    /** 回旋斩伤害=攻击力×此倍率(略低于正刀,毕竟是整圈)。 */
    public double spinSlashDamageRatio = 0.8;
    /** 回旋斩冷却(tick,12≈一次挥击节奏,防狂点叠圈)。 */
    public int spinSlashCooldownTicks = 12;

    // —— m257 蓄力重斩(学 Epic Fight 的按住派生:按住攻击键蓄力,松开放前方锥形重斩) ——
    /** 蓄力重斩总开关。 */
    public boolean enableChargeSlash = true;
    /** 起蓄门槛(tick,按住不足此时长=普通攻击不触发,12=0.6 秒)。 */
    public int chargeSlashMinTicks = 12;
    /** 满蓄时长(tick,30=1.5 秒;蓄到即「叮」提示,再按住不涨)。 */
    public int chargeSlashMaxTicks = 30;
    /** 最低蓄力伤害倍率(刚过门槛就松开,伤害=攻击力×此值)。 */
    public double chargeSlashDamageMultMin = 1.6;
    /** 满蓄伤害倍率。 */
    public double chargeSlashDamageMultMax = 3.2;
    /** 重斩锥形范围(格)。 */
    public double chargeSlashRange = 5.0;
    /** 冷却(tick,100=5 秒)。 */
    public int chargeSlashCooldownTicks = 100;

    // —— m255 武器技能特效夸张化(混沌斩剑气推进 / 吞噬吸魂漩涡 / 终焉血阵天罚,多帧演出) ——
    /** 武器技能大演出总开关(关=只留三招原有的简版粒子)。 */
    public boolean weaponSkillFancyFx = true;
    /** 武器技能特效密度倍率(0.2~3 生效钳制;嫌炸眼调 1 以下,嫌不够猛拉 2+,粒子多卡就调低)。 */
    public double weaponSkillFxScale = 1.5;

    /** 真·骨骼级拔刀动作(m254,player-animator 驱动的关键帧动画,含蓄力/爆发/弓步全身发力):
     *  开=本地玩家挥砍播放七式真动作(程序化姿态自动让位);关=回 m243 程序化姿态。动作 JSON 在
     *  assets/yongye/player_animations/,游戏内 F3+T 重载资源即可热调参。 */
    public boolean slashFxAnimLib = true;
    /** m260:持械战斗站姿(Epic Fight 感:手持武器=备战架势循环动画,只动上身不影响走跑)。 */
    public boolean slashFxBattleStance = true;
    /** m260:格挡姿态(按住右键格挡时武器横举护体的循环动画;法杖不参与)。 */
    public boolean slashFxGuardPose = true;
    /** 第三人称拔刀姿态幅度倍率(m248,0.3~2.5 生效钳制;1=旧版幅度,默认 1.35 更夸张跟手,嫌浮夸调回 1)。 */
    public double slashFxPoseScale = 1.35;
    /** m256:贴图化刀光(拉丝质感刀身+旧纯色带降档当辉光,学 EpicACG 路线);关=回纯色刀光。 */
    public boolean slashFxTextured = true;
    /** 斩击轨迹大小倍率。 */
    public double slashFxSize = 1.0;
    /** 斩击轨迹亮度(0~1,0=等效关闭)。 */
    public double slashFxAlpha = 0.75;

    /** m316:MoBends 式疾跑姿态(躯干随步幅拧身±40°+前倾起伏+头部反补锁视线+屈肘泵臂+步幅加大;
     *  扒 Iwoplaza/MoBends SprintAnimationBit 关键值,MIT 协议,见 THIRD_PARTY_NOTICES)。关=回原版直臂跑。 */
    public boolean sprintPose = true;
    /** m316:疾跑姿态幅度倍率(0.3~2.0 生效钳制;嫌浮夸调 0.7,想更炸调 1.4)。 */
    public double sprintPoseScale = 1.0;

    /** 追杀:墙后卡住时,若能在玩家身边找到安全落点就传送过去;找不到则靠挖墙+起跳翻越(三者组合) */
    public boolean pursuitTeleportWallStuck = true;
    /** 追杀:撞低墙时给一次起跳冲量帮助翻越 */
    public boolean pursuitJumpWalls = true;

    // ============ m72:技能按攻击力计算 / 佩恩失目标传送 / 被夺武器找回 ============
    // 武器主动技能:额外按「玩家攻击力 × 比例」计入伤害(武器越强技能越强)
    public double skillSlashAttackRatio = 1.5;
    public double skillDevourAttackRatio = 1.0;
    public double skillFinalityAttackRatio = 2.5;
    // 佩恩技能:伤害按「佩恩攻击力 × 比例」计算(攻击随时间线缩放,技能随之变强)
    public double painPushAttackRatio = 0.30;
    public double painPullAttackRatio = 0.15;
    public double painDevastationAttackRatio = 0.50;
    // 佩恩长时间找不到玩家 → 传送到随机玩家身边追杀
    public boolean painLostTeleport = true;
    public int painLostTeleportTicks = 1200;     // 1 分钟无目标即传送
    // 被夺武器找回:强化转移到新武器时保留的比例(损失 1/3 → 保留 2/3)
    public double weaponRecoverKeepFraction = 0.6667;
    // 精英缴械:是否也抢玩家身上穿的盔甲(抢到直接穿身上,击杀掉落归还)
    public boolean eliteStealArmor = true;
    public double eliteStealArmorChance = 0.25;

    // ============ m73:精英词缀 / 佩恩阶段化 / 存活排行 ============
    public boolean enableEliteAffix = true;
    public double eliteAffixChance = 0.5;        // 精英带词缀概率(命中则随机 1~2 个)
    public double eliteLifestealRatio = 0.5;     // 嗜血词缀:按造成伤害的比例回血
    public double eliteAffixExplodeDamage = 12.0;// 爆裂词缀:死亡时 4 格内 AoE 伤害(不破坏地形)
    public boolean enablePainPhases = true;      // 佩恩按血量分 3 阶段,逐阶段狂暴
    public boolean enableSurvivalRank = true;    // 记录最高永夜层数 / 最高天数,/yongye top 排行

    // ============ m74:永夜天象(血月/酸雨/流星雨,按永夜等级解锁)============
    public boolean enableNightfallWeather = true;
    public int weatherCheckIntervalTicks = 600;    // 每 30s 检定一次是否降下天象
    public double weatherTriggerChance = 0.20;      // 检定命中概率
    public int weatherEventDurationTicks = 1200;    // 单次天象持续 60s
    public int bloodMoonMinNightfall = 2;           // 血月解锁永夜层
    public int acidRainMinNightfall = 3;            // 酸雨解锁永夜层
    public int meteorMinNightfall = 4;              // 流星雨解锁永夜层
    public double acidRainDamage = 2.0;             // 酸雨:露天玩家每秒受伤
    public double meteorDamage = 12.0;              // 流星雨:落点 AoE 伤害
    public double meteorRadius = 24.0;              // 流星落点距玩家范围
    public double meteorImpactRadius = 3.0;         // 单颗流星杀伤半径

    // ============ m76:永夜剥夺视线(沉浸感)============
    public boolean enableNightfallDarkness = true;
    public int nightfallDarknessMinLevel = 1;   // 永夜达到该层即压缩视野(客户端恒定暗角,不闪)
    // 旧的原版「黑暗」效果(StatusEffects.DARKNESS)自带呼吸式脉动会"一闪一闪",默认关闭;
    // 改用客户端恒定暗角(vignette)实现"固定不闪"的视野压缩。想要原版脉动黑暗可设 true。
    public boolean nightfallDarknessEffect = false;

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
                // m214:标识默认文案改版「抖音:乔大仙 → DY:乔大仙」;仅当文件里仍是旧默认值时迁移,自定义文案不动
                if ("抖音:乔大仙".equals(INSTANCE.itemWatermarkText)) INSTANCE.itemWatermarkText = "DY:乔大仙";
                // m222:终局龙血默认值改版 1e9→1e19;仅当仍是旧默认值时迁移,自定义不动
                if (INSTANCE.endDragonHealth == 1.0E9) INSTANCE.endDragonHealth = 1.0E19;
                // m237:肉盾武器攻击折减默认值改版 0.5→0.3;仅当仍是旧默认值时迁移,自定义不动
                if (INSTANCE.enhanceHybridDamageFraction == 0.5) INSTANCE.enhanceHybridDamageFraction = 0.3;
                // m312:看板默认停靠位改版 左中(0,0,0)→左上(1,-2,14);仅当三项均仍为旧默认时迁移,自定义不动
                if (INSTANCE.hudInfoAnchor == 0 && INSTANCE.hudInfoOffsetX == 0 && INSTANCE.hudInfoOffsetY == 0) {
                    INSTANCE.hudInfoAnchor = 1; INSTANCE.hudInfoOffsetX = -2; INSTANCE.hudInfoOffsetY = 14;
                }
                // m253:战斗爽全物品黑名单默认值改版 ""→内置「前期拿不到」清单;仅当仍为空(m250 旧默认)时迁移,自定义不动
                if (INSTANCE.questBattleAnyItemExtraBans == null || INSTANCE.questBattleAnyItemExtraBans.isBlank())
                    INSTANCE.questBattleAnyItemExtraBans = QUEST_BANS_DEFAULT;
                // m338/m339:蚀域第1天+皮肤BOSS第5天+BOSS全线加强;仅当仍为旧默认时迁移,自定义不动
                if (INSTANCE.blightStartDay == 12 || INSTANCE.blightStartDay == 11) INSTANCE.blightStartDay = 1;
                if (INSTANCE.mobBossStartDay == 10) INSTANCE.mobBossStartDay = 5;
                // m341:皮肤BOSS阶梯解锁(第6天起一天一只,弱→强);旧默认(含 m339 短暂的 5)均迁移
                if (INSTANCE.redSpiderMinDay == 12) INSTANCE.redSpiderMinDay = 6;
                if (INSTANCE.deathMageMinDay == 14) INSTANCE.deathMageMinDay = 7;
                if (INSTANCE.phoenixMinDay == 16) INSTANCE.phoenixMinDay = 8;
                if (INSTANCE.wildDragonMinDay == 10 || INSTANCE.wildDragonMinDay == 5) INSTANCE.wildDragonMinDay = 9;
                if (INSTANCE.anubisMinDay == 5) INSTANCE.anubisMinDay = 10;
                if (INSTANCE.anubisBaseHealth == 1.0E6) INSTANCE.anubisBaseHealth = 1.5E6;
                if (INSTANCE.phoenixBaseHealth == 4.0E5) INSTANCE.phoenixBaseHealth = 6.0E5;
                if (INSTANCE.deathMageBaseHealth == 3.0E5) INSTANCE.deathMageBaseHealth = 4.5E5;
                if (INSTANCE.redSpiderBaseHealth == 2.5E5) INSTANCE.redSpiderBaseHealth = 3.75E5;
                if (INSTANCE.toroDragonBaseHealth == 6.0E5) INSTANCE.toroDragonBaseHealth = 9.0E5;
                if (INSTANCE.phoenixBeamCooldownTicks == 160) INSTANCE.phoenixBeamCooldownTicks = 120;
                if (INSTANCE.phoenixBeamDamage == 30.0) INSTANCE.phoenixBeamDamage = 45.0;
                if (INSTANCE.phoenixTornadoCooldownTicks == 300) INSTANCE.phoenixTornadoCooldownTicks = 220;
                if (INSTANCE.phoenixTornadoDamage == 25.0) INSTANCE.phoenixTornadoDamage = 38.0;
                if (INSTANCE.mageStrikeCooldownTicks == 140) INSTANCE.mageStrikeCooldownTicks = 110;
                if (INSTANCE.mageStrikeDamage == 35.0) INSTANCE.mageStrikeDamage = 52.0;
                if (INSTANCE.mageNovaCooldownTicks == 240) INSTANCE.mageNovaCooldownTicks = 180;
                if (INSTANCE.mageNovaDamage == 25.0) INSTANCE.mageNovaDamage = 38.0;
                if (INSTANCE.mageBlinkCooldownTicks == 200) INSTANCE.mageBlinkCooldownTicks = 150;
                if (INSTANCE.spiderWebCooldownTicks == 200) INSTANCE.spiderWebCooldownTicks = 150;
                if (INSTANCE.spiderPounceCooldownTicks == 160) INSTANCE.spiderPounceCooldownTicks = 120;
                if (INSTANCE.spiderPounceDamage == 25.0) INSTANCE.spiderPounceDamage = 38.0;
                if (INSTANCE.toroBreathCooldownTicks == 140) INSTANCE.toroBreathCooldownTicks = 110;
                if (INSTANCE.toroBreathDamage == 30.0) INSTANCE.toroBreathDamage = 45.0;
                if (INSTANCE.toroDiveCooldownTicks == 220) INSTANCE.toroDiveCooldownTicks = 170;
                if (INSTANCE.toroDiveDamage == 45.0) INSTANCE.toroDiveDamage = 68.0;
                if (INSTANCE.anubisSpellDamage == 45.0) INSTANCE.anubisSpellDamage = 68.0;
                // —— 陈旧检查:对比文件键 vs 当前字段,警告死键/缺失键/版本不符 ——
                try {
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    List<String> obsolete = obsoleteKeys(obj);   // 文件有、代码已删
                    List<String> missing = missingKeys(obj);     // 代码有、文件没有(新加的)
                    if (!obsolete.isEmpty())
                        Yongye.LOGGER.warn("[夜蚀] 配置含 {} 个陈旧字段(已从代码删除,下次保存自动清除): {}", obsolete.size(), obsolete);
                    if (!missing.isEmpty())
                        Yongye.LOGGER.info("[夜蚀] 配置缺 {} 个新字段(已采用默认值,下次保存自动补全): {}", missing.size(), missing);
                    if (INSTANCE.configVersion != CURRENT_CONFIG_VERSION)
                        Yongye.LOGGER.info("[夜蚀] 配置版本 {} → {}:默认值改版已按「仅旧默认迁移」自动完成(自定义值不动),版本号已写盘对齐,无需处理。",
                                INSTANCE.configVersion, CURRENT_CONFIG_VERSION);
                } catch (RuntimeException ignore) { /* 诊断失败不影响正常加载 */ }
                boolean verChanged = INSTANCE.configVersion != CURRENT_CONFIG_VERSION;   // m345
                INSTANCE.configVersion = CURRENT_CONFIG_VERSION; // 对齐版本号
                if (verChanged) save();   // m345:立即写盘——否则"下次保存"可能永不发生,提示每次启动重复(作者 106≠107 报告)
            } else {
                INSTANCE = new YongyeConfig();
                save();
            }
        } catch (IOException | RuntimeException e) {
            Yongye.LOGGER.error("[夜蚀] 读取配置失败,使用默认值", e);
            INSTANCE = new YongyeConfig();
        }
    }

    /** 当前配置 schema 的全部字段名(public、非 static)。 */
    private static List<String> schemaFieldNames() {
        List<String> names = new ArrayList<>();
        for (Field f : YongyeConfig.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || !Modifier.isPublic(m)) continue;
            names.add(f.getName());
        }
        return names;
    }

    /** 文件里有、但代码已删除的"死键"。 */
    private static List<String> obsoleteKeys(JsonObject obj) {
        List<String> fields = schemaFieldNames();
        List<String> out = new ArrayList<>();
        for (String k : obj.keySet()) if (!fields.contains(k)) out.add(k);
        return out;
    }

    /** 代码里有、但文件缺少的"新键"(会采用默认值)。 */
    private static List<String> missingKeys(JsonObject obj) {
        List<String> out = new ArrayList<>();
        for (String f : schemaFieldNames()) if (!obj.has(f)) out.add(f);
        return out;
    }

    /**
     * 现读现查:重新读盘对比,返回给玩家看的诊断报告(/yongye config check 调用)。
     * 报告:版本是否一致、死键列表、缺失键列表、字段总数。
     */
    public static String diagnose() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("yongye.json");
        StringBuilder sb = new StringBuilder();
        int total = schemaFieldNames().size();
        if (!Files.exists(path)) {
            return "配置文件尚未生成(将于下次保存时按当前默认值创建)。当前字段总数: " + total;
        }
        try {
            JsonObject obj = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            int fileVer = obj.has("configVersion") ? obj.get("configVersion").getAsInt() : -1;
            List<String> obsolete = obsoleteKeys(obj);
            List<String> missing = missingKeys(obj);
            sb.append("配置版本: 文件 ").append(fileVer).append(" / 当前 ").append(CURRENT_CONFIG_VERSION)
                    .append(fileVer == CURRENT_CONFIG_VERSION ? "(一致)" : "(不一致,默认值可能已调整)").append('\n');
            sb.append("字段总数: 文件 ").append(obj.size()).append(" / 代码 ").append(total).append('\n');
            sb.append("陈旧死键(").append(obsolete.size()).append("): ").append(obsolete.isEmpty() ? "无" : obsolete).append('\n');
            sb.append("缺失新键(").append(missing.size()).append("): ").append(missing.isEmpty() ? "无" : missing);
        } catch (IOException | RuntimeException e) {
            return "读取/解析配置文件失败: " + e.getMessage();
        }
        return sb.toString();
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
            Yongye.LOGGER.error("[夜蚀] 写入配置失败", e);
        }
    }

    /** 配置文件(yongye.json)的绝对路径——供「导出配置」打印给用户定位。 */
    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("yongye.json");
    }

    /** 按字段名反射读取当前值并转成字符串(供爆率编辑器回传当前值)。无此字段/不可读返回空串。 */
    public static String getFieldString(String key) {
        try {
            java.lang.reflect.Field f = YongyeConfig.class.getField(key);
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) return "";
            Object v = f.get(get());
            return String.valueOf(v);
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }
}
