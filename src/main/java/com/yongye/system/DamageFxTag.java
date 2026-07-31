package com.yongye.system;

/**
 * m406(路线图 14):伤害语义档同栈标记。
 * 暴击/处决的追伤都是"proc 点先知道、再调 entity.damage()"——damage() 会同步走进
 * CombatFxHandler 的 ALLOW_DAMAGE 观察者(同服务端主线程同调用栈),所以 proc 点在
 * damage() 前把语义档写进这里,观察者 consume() 取走并清零,即可给飘字 payload 打上
 * CRITICAL/EXECUTION,**战斗数值一个字不碰**。只在服务端主线程读写,无并发问题;
 * consume 即清防止标记泄漏到下一次无关伤害。
 */
public final class DamageFxTag {
    private DamageFxTag() {}

    /** 待打标(0=无;取值=DamageNumberPayload.CRITICAL/EXECUTION)。 */
    private static int next = 0;

    public static void mark(int kind) { next = kind; }

    /** 取走并清零。 */
    public static int consume() {
        int v = next;
        next = 0;
        return v;
    }
}
