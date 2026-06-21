package com.yongye.client;

/**
 * 客户端侧缓存:由服务端 StatsPayload 更新,成长面板 / 背包职业标签读取。
 */
public final class ClientStats {
    private ClientStats() {}

    public static int health = 0;
    public static int[] levels = new int[0];
    public static String className = "";   // 本命职业 id("" = 无)

    public static void update(int h, int[] l, String cn) {
        health = h;
        levels = l;
        className = cn == null ? "" : cn;
    }
}
