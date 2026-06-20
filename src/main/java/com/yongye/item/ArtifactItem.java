package com.yongye.item;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModComponents;
import com.yongye.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 背包神器物品:放在背包即生效(实际效果由 ArtifactManager 应用)。
 * 等级 1~6 存于 ARTIFACT_LEVEL 组件。
 */
public class ArtifactItem extends Item {

    private final ArtifactType type;

    public ArtifactItem(ArtifactType type, Settings settings) {
        super(settings);
        this.type = type;
    }

    public ArtifactType getType() {
        return type;
    }

    public static ItemStack create(ArtifactType type, int level) {
        int clamped = Math.max(1, Math.min(YongyeConfig.get().artifactMaxLevel, level));
        ItemStack stack = new ItemStack(ModItems.getArtifact(type));
        stack.set(ModComponents.ARTIFACT_LEVEL, clamped);
        return stack;
    }

    public static int getLevel(ItemStack stack) {
        Integer lvl = stack.get(ModComponents.ARTIFACT_LEVEL);
        return lvl == null ? 1 : lvl;
    }

    @Override
    public Text getName(ItemStack stack) {
        int lvl = getLevel(stack);
        return Text.literal("[" + ArtifactType.tierName(lvl) + "] ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.translatable("item.yongye.artifact_" + type.id));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType ttype) {
        tooltip.add(Text.translatable("item.yongye.artifact." + type.id + ".desc").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.yongye.artifact.in_bag").formatted(Formatting.DARK_GRAY));
    }
}
