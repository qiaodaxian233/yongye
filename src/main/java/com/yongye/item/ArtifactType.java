package com.yongye.item;

/**
 * 背包神器类型(文档 14.3)。每种神器是一件独立物品,等级 1~6 存于 ARTIFACT_LEVEL 组件。
 * 等级名称:残破/完整/远古/神话/灾变/终焉。
 */
public enum ArtifactType {
    LIFE_IDOL("life_idol"),                 // 生命神像:增加最大生命
    IRON_CORE("iron_core"),                 // 铁壁核心:减少物理伤害(近似:抗性)
    BONE_ARROW_CHARM("bone_arrow_charm"),   // 骨箭护符:克制精英骷髅,概率免疫箭矢
    VOODOO_BOTTLE("voodoo_bottle"),         // 巫毒净瓶:克制精英女巫,清除负面
    ESCAPIST_BOOTS("escapist_boots"),       // 逃亡者之靴:增加移速
    GRAVEDIGGER_COMPASS("gravedigger_compass"), // 掘墓人罗盘:指向附近高价值目标
    UNDYING_EMBER("undying_ember"),         // 不灭余烬:受致命伤保命
    NIGHTFALL_EYE("nightfall_eye"),         // 永夜之眼:永夜视野+降低锁定
    GLUTTON_HEART("glutton_heart"),         // 饕餮心脏:击杀回血
    WORLD_ANCHOR("world_anchor");           // 世界锚石:降低附近怪挖墙爬墙

    public final String id;

    ArtifactType(String id) {
        this.id = id;
    }

    public static final String[] TIER_NAMES = {"残破", "完整", "远古", "神话", "灾变", "终焉"};

    public static String tierName(int level) {
        return TIER_NAMES[Math.max(0, Math.min(TIER_NAMES.length - 1, level - 1))];
    }
}
