package com.yongye.item;

import net.minecraft.util.Formatting;

/**
 * 装备品质等级:普通 → 至尊,共 9 阶。由强化等级换算,越高阶解锁更高暴击率与攻速,
 * 并对应一个稀有度字母(D~EX)。颜色用于 tooltip 与武器介绍界面。
 */
public enum WeaponQuality {
    COMMON   ("普通", "D",   Formatting.GRAY,         0,     0.00, 0.00),
    FINE     ("精良", "C",   Formatting.WHITE,        10,    0.03, 0.10),
    SUPERIOR ("优秀", "B",   Formatting.GREEN,        25,    0.06, 0.20),
    ELITE    ("精锐", "B+",  Formatting.DARK_GREEN,   50,    0.09, 0.30),
    RARE     ("稀有", "A",   Formatting.AQUA,         100,   0.12, 0.45),
    EPIC     ("史诗", "S",   Formatting.LIGHT_PURPLE, 250,   0.16, 0.60),
    LEGENDARY("传说", "SS",  Formatting.GOLD,         500,   0.20, 0.80),
    ARTIFACT ("神器", "SSS", Formatting.RED,          1000,  0.25, 1.10),
    SUPREME  ("至尊", "EX",  Formatting.DARK_RED,     2500,  0.35, 1.60);

    public final String cn;        // 中文名
    public final String grade;     // 稀有度字母
    public final Formatting color;
    public final int minLevel;     // 达到此品质所需的最低强化等级
    public final double critChance; // 暴击率
    public final double attackSpeed;// 额外攻击速度

    WeaponQuality(String cn, String grade, Formatting color, int minLevel, double critChance, double attackSpeed) {
        this.cn = cn;
        this.grade = grade;
        this.color = color;
        this.minLevel = minLevel;
        this.critChance = critChance;
        this.attackSpeed = attackSpeed;
    }

    /** 强化等级 → 品质(取不超过该等级的最高阶)。 */
    public static WeaponQuality forLevel(int level) {
        WeaponQuality result = COMMON;
        for (WeaponQuality q : values()) {
            if (level >= q.minLevel) result = q;
        }
        return result;
    }

    /** 距离下一阶还差多少级;已到顶返回 -1。 */
    public int levelsToNext(int level) {
        int idx = ordinal();
        if (idx + 1 >= values().length) return -1;
        return values()[idx + 1].minLevel - level;
    }

    public WeaponQuality next() {
        int idx = ordinal();
        return idx + 1 < values().length ? values()[idx + 1] : this;
    }
}
