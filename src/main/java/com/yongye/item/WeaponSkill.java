package com.yongye.item;

/**
 * 武器主动技能。按武器品质解锁,按键触发,带冷却。索引须与客户端按键、网络包一致。
 *   0 混沌斩  : 稀有解锁
 *   1 深渊吞噬: 史诗解锁
 *   2 终焉降临: 神器解锁
 */
public enum WeaponSkill {
    SLASH   ("混沌斩",   "向前方释放能量斩击,造成范围伤害并击退。",     WeaponQuality.RARE),
    DEVOUR  ("深渊吞噬", "吸取周围敌人的生命,转化为自身治疗。",         WeaponQuality.EPIC),
    FINALITY("终焉降临", "召唤终焉之力,对大范围内敌人造成毁灭性打击。", WeaponQuality.ARTIFACT);

    public final String cn;
    public final String desc;
    public final WeaponQuality unlockTier;

    WeaponSkill(String cn, String desc, WeaponQuality unlockTier) {
        this.cn = cn;
        this.desc = desc;
        this.unlockTier = unlockTier;
    }

    /** 该等级是否已解锁此技能。 */
    public boolean isUnlocked(int enhanceLevel) {
        return WeaponQuality.forLevel(enhanceLevel).ordinal() >= unlockTier.ordinal();
    }
}
