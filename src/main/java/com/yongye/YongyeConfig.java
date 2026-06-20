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
    public double eliteHealthMultiplier = 5.0;
    public double eliteAttackMultiplier = 2.0;
    public double eliteSpeedMultiplier = 1.3;
    public double eliteKnockbackResistanceAdd = 0.6;
    public double eliteFollowRangeAdd = 24.0;
    /** 精英骷髅每秒箭数 / 精英女巫每秒投掷数 */
    public int eliteSkeletonArrowsPerSecond = 5;
    public int eliteWitchPotionsPerSecond = 5;
    /** 精英瞬移:与目标水平距离超过该值且冷却结束时触发(格);冷却(tick) */
    public double eliteTeleportTriggerDistance = 24.0;
    public int eliteTeleportCooldownTicks = 100;
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
