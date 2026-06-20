package com.yongye.recipe;

import com.yongye.YongyeConfig;
import com.yongye.item.ArtifactItem;
import com.yongye.item.ArtifactType;
import com.yongye.registry.ModItems;
import com.yongye.registry.ModRecipes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/**
 * 神器升级:1 件神器(等级 L) + 对应阶段材料 → 1 件同类型 L+1 神器。
 * 阶段材料:L+1 为 2~3 用生命结晶;4 用生命核心;5 用灾变血核;6 用终焉神髓。
 */
public class ArtifactUpgradeRecipe extends SpecialCraftingRecipe {

    public ArtifactUpgradeRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    private static Item requiredMaterial(int resultLevel) {
        if (resultLevel >= 6) return ModItems.ENDING_ESSENCE;
        if (resultLevel >= 5) return ModItems.CATASTROPHE_BLOOD_CORE;
        if (resultLevel >= 4) return ModItems.LIFE_CORE;
        return ModItems.LIFE_CRYSTAL;
    }

    private static boolean isMaterial(Item item) {
        return item == ModItems.LIFE_CRYSTAL || item == ModItems.LIFE_CORE
                || item == ModItems.CATASTROPHE_BLOOD_CORE || item == ModItems.ENDING_ESSENCE;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        ArtifactType type = null;
        int level = -1;
        int artifactCount = 0;
        Item materialFound = null;
        int materialCount = 0;
        int other = 0;

        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof ArtifactItem ai) {
                type = ai.getType();
                level = ArtifactItem.getLevel(s);
                artifactCount++;
            } else if (isMaterial(s.getItem())) {
                materialFound = s.getItem();
                materialCount += s.getCount();
            } else {
                other++;
            }
        }

        if (other > 0 || artifactCount != 1 || type == null) return false;
        if (level >= YongyeConfig.get().artifactMaxLevel) return false;
        return materialCount == 1 && materialFound == requiredMaterial(level + 1);
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() instanceof ArtifactItem ai) {
                return ArtifactItem.create(ai.getType(), ArtifactItem.getLevel(s) + 1);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ARTIFACT_UPGRADE;
    }
}
