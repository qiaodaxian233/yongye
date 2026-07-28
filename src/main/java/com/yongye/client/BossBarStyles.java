package com.yongye.client;

import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * m341(P0 崩溃修复):BOSS 血条画框样式与同类合并组。
 * 原为 BossBarStyleMixin 的内部类——Mixin 禁止 mixin 包内的类被运行时代码直接类加载
 * (crash-2026-07-29_01.53.05 实锤),移出到普通客户端包;mixin 通过嵌套类 import 引用,改动最小。
 */
public final class BossBarStyles {
    private BossBarStyles() {}

    /** 画框几何,全部是<b>贴图像素</b>:框宽高/槽偏移 xy/槽宽高/牌匾中心 y(-1=无牌匾,名字悬浮框顶上方)。 */
    public record Style(Identifier frame, Identifier back, Identifier fill,
                        int fw, int fh, int sx, int sy, int sw, int sh, int pcy) {}

    /** 同类合并组。 */
    public static final class Group {
        public final Style st;
        public final List<ClientBossBar> members = new ArrayList<>();
        public Group(Style st) { this.st = st; }
    }
}
