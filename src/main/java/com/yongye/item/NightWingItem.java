package com.yongye.item;

import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 永夜之翼(m100)——可穿戴背饰,继承原版鞘翅自带滑翔功能。
 * 穿鞘翅槽即可滑翔;手持/物品栏显示为「恶意检察官」羽翼 voxel 模型。
 * 【1.21.1 说明】滑翔在 1.21.1 硬编码于 ElytraItem 类(glider 数据组件是 1.21.2+),
 * 故继承 ElytraItem 是让自定义物品获得滑翔功能的正确做法。
 */
public class NightWingItem extends ElytraItem {

    public NightWingItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("永夜之翼").formatted(Formatting.DARK_RED, Formatting.BOLD));
        tooltip.add(Text.literal("血色羽翼,载你掠过永夜长空。").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("✦ 穿在鞘翅槽即可滑翔").formatted(Formatting.LIGHT_PURPLE));
    }
}
