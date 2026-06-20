package com.yongye.item;

/**
 * 技能书类型(文档 13.4 的"其它技能书")。血量强化是单独的 HealthSkillBookItem,这里是新增的几种。
 *  - ATTACK 攻击强化:增加攻击伤害
 *  - ARMOR 护甲强化:增加护甲与护甲韧性
 *  - REGEN 生命恢复:持续回血
 *  - EVASION 闪避强化:概率完全闪避一次伤害(上限 50%)
 *  - THORNS 反伤强化:受击反弹伤害
 *  - RESISTANCE 抗性强化:抗火 + 削弱/清除负面状态
 *  - SATIETY 饱食度强化:持续补充饱食度与饱和度,等级越高越不会饿
 */
public enum SkillType {
    ATTACK("attack"),
    ARMOR("armor"),
    REGEN("regen"),
    EVASION("evasion"),
    THORNS("thorns"),
    RESISTANCE("resistance"),
    SATIETY("satiety");

    public final String id;

    SkillType(String id) {
        this.id = id;
    }
}
