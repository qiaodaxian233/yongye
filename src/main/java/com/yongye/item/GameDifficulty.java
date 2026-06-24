package com.yongye.item;

import com.yongye.registry.ModAttachments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;

/**
 * 游戏难度(玩家开局选择,每人一次,死亡保留)。
 *  - 难度只影响「动态对位缩放」的强度倍率(怪物随玩家成长被拔高的幅度),不改原版基础。
 *    因此低难度 ≈ 接近原版,高难度 = 怪物远超你的成长曲线。详见 DynamicScaling。
 *  - 倍率作用在「对位目标血量/伤害」上;由于对位只增不减,难度低不会把怪压到比原版还弱,
 *    只是少拔高;难度高则把怪往死里堆。
 * 顺序即 ordinal,存进 ModAttachments.DIFFICULTY(int);-1=未选,按「适中」处理。
 */
public enum GameDifficulty {
    PLAY    ("游玩", 0.5,  "最轻松的体验。怪物只比原版略强,适合观光、建造、熟悉机制。",          Formatting.GREEN),
    EASY    ("简单", 0.8,  "压力很小,留足成长空间,适合休闲推进剧情。",                          Formatting.DARK_GREEN),
    NORMAL  ("适中", 1.0,  "标准平衡。怪物与你同步成长,打得有来有回——推荐首次游玩。",          Formatting.YELLOW),
    HARD    ("困难", 1.6,  "怪物明显更肉更痛,需要认真配装、强化与操作。",                        Formatting.GOLD),
    HELL    ("地狱", 2.5,  "残酷。一个失误就可能致命,为熟练的硬核玩家准备。",                    Formatting.RED),
    ABYSS   ("深渊", 4.0,  "深渊级压制。怪物远超你的成长曲线,九死一生。",                        Formatting.DARK_RED),
    ETERNAL ("永夜", 6.0,  "永夜真正的样子——绝望难度,只为最强者而存在。",                      Formatting.DARK_PURPLE);

    public final String cn;
    public final double mobMult;
    public final String desc;
    public final Formatting color;

    GameDifficulty(String cn, double mobMult, String desc, Formatting color) {
        this.cn = cn;
        this.mobMult = mobMult;
        this.desc = desc;
        this.color = color;
    }

    /** 按 ordinal 取难度;越界(含 -1 未选)返回「适中」。 */
    public static GameDifficulty byOrdinal(int i) {
        GameDifficulty[] v = values();
        if (i < 0 || i >= v.length) return NORMAL;
        return v[i];
    }

    /** 读取该玩家已选难度的怪物强度倍率;未选(=-1)按「适中」(1.0)。 */
    public static double mobMultOf(PlayerEntity p) {
        int idx = p.getAttachedOrElse(ModAttachments.DIFFICULTY, -1);
        return byOrdinal(idx).mobMult;
    }
}
