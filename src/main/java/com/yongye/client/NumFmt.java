package com.yongye.client;

import java.util.Locale;

/**
 * 统一紧凑数字格式(m219,作者:「数字太大了,用 1K 1B 这样」)。
 * 规则:<1 万原样(整数不带小数点);≥1 万 K;≥100 万 M;≥10 亿 B;≥1 万亿 T。
 * 商 <100 保一位小数(整值省略小数),≥100 取整:1.5K / 15.3K / 153K / 2.5B / 999T。
 * 血条 / HUD / 成长面板 / 装备介绍共用,保证全模组数字口径一致。
 */
public final class NumFmt {
    private NumFmt() {}

    public static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1e12) return one(v / 1e12) + "T";
        if (a >= 1e9)  return one(v / 1e9)  + "B";
        if (a >= 1e6)  return one(v / 1e6)  + "M";
        if (a >= 1e4)  return one(v / 1e3)  + "K";
        return (v == Math.floor(v)) ? String.valueOf((long) v)
                : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String one(double q) {
        if (Math.abs(q) >= 100 || q == Math.floor(q)) return String.valueOf(Math.round(q));
        return String.format(Locale.ROOT, "%.1f", q);
    }
}
