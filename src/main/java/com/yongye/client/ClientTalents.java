package com.yongye.client;

import java.util.List;
import java.util.Map;

/**
 * 客户端侧天赋状态缓存:由服务端 TalentSyncPayload 更新,天赋界面 TalentScreen 读取。
 */
public final class ClientTalents {
    private ClientTalents() {}

    public static int points = 0;                       // 可用天赋点
    public static List<String> classes = List.of();     // 已习得职业 id
    public static Map<String, Integer> learned = Map.of(); // 节点 id -> 已点等级

    public static void update(int p, List<String> c, Map<String, Integer> l) {
        points = p;
        classes = c == null ? List.of() : c;
        learned = l == null ? Map.of() : l;
    }
}
