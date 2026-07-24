package com.yongye.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 强化石(m294,作者供图十档"眼球"渐变,一图一档):
 * 面值 = 10^(档-1):1 / 10 / 100 / 1000 / 1万 / 10万 / 100万 / 1000万 / 1亿 / 10亿。
 * 一颗 = 直接 +面值 强化等级——**必得、不走失败/碎裂 RNG**(口径同工作台 addLevels 与
 * 强化继承 m236 的先例:"等级本就是挣来的,不再赌一次");传统材料(碎片/结晶/核心/血核)
 * 仍走逐级 RNG 老规矩,两条线并存。
 * 封顶 10 档 = 10 亿:等级存 int(上限 21.4 亿,m293 已加固),两颗 10 亿石多一点即摸顶,
 * 是"整颗都有效"的最大档——不做 100 亿(会有七成八被 int 顶蒸发)。
 */
public class EnhanceStoneItem extends Item {

    /** 档位 1~10。 */
    public final int tier;

    public EnhanceStoneItem(int tier, Settings settings) {
        super(settings);
        this.tier = tier;
    }

    /** 面值:10^(tier-1)。long 计算后钳 int(10 档 = 10 亿,本身在 int 内,防御性钳)。 */
    public static int valueOf(int tier) {
        long v = 1L;
        for (int i = 1; i < tier; i++) v *= 10L;
        return (int) Math.min(Integer.MAX_VALUE, v);
    }

    /** 中文单位紧凑显示:整万显示 N万、整亿显示 N亿,其余原样。 */
    public static String cn(long v) {
        if (v >= 100_000_000L && v % 100_000_000L == 0) return (v / 100_000_000L) + "亿";
        if (v >= 10_000L && v % 10_000L == 0) return (v / 10_000L) + "万";
        return String.valueOf(v);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("强化材料:一颗 = +" + cn(valueOf(tier)) + " 级(必得,不碎)")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("第 " + tier + "/10 档").formatted(Formatting.DARK_GRAY));
    }
}
