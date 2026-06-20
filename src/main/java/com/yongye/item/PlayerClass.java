package com.yongye.item;

/** 六大职业。 */
public enum PlayerClass {
    TANK("tank", "肉盾"),
    WARRIOR("warrior", "战士"),
    WARLOCK("warlock", "术士"),
    SWORDSMAN("swordsman", "剑客"),
    MONK("monk", "武僧"),
    ASSASSIN("assassin", "刺客");

    public final String id;
    public final String cn;

    PlayerClass(String id, String cn) {
        this.id = id;
        this.cn = cn;
    }

    public static PlayerClass byId(String id) {
        for (PlayerClass c : values()) if (c.id.equals(id)) return c;
        return null;
    }
}
