package com.yongye.recipe;

import com.yongye.registry.ModRecipes;
import com.yongye.system.EquipmentEnhancer;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/**
 * 装备无限强化:工作台放入 1 件可强化装备(武器/盔甲)+ 任意强化材料
 * (生命碎片 +1 / 结晶 +10 / 核心 +100 / 灾变血核 +1000,每格消耗 1 个)→ 同装备,强化等级累加。
 * 无上限,品质随等级提升(普通→至尊)。高品质需要高阶材料,提升不再廉价。
 */
public class EquipmentEnhanceRecipe extends SpecialCraftingRecipe {

    public EquipmentEnhanceRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        int equipCount = 0;
        int materialLevels = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (EquipmentEnhancer.isMaterial(s.getItem())) {
                materialLevels += EquipmentEnhancer.materialValue(s.getItem()); // 每格只消耗 1 个
            } else if (EquipmentEnhancer.isEnhanceable(s.getItem())) {
                equipCount++;
                if (equipCount > 1) return false;
            } else {
                return false;
            }
        }
        return equipCount == 1 && materialLevels > 0;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        ItemStack equipment = ItemStack.EMPTY;
        int materialLevels = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (EquipmentEnhancer.isMaterial(s.getItem())) {
                materialLevels += EquipmentEnhancer.materialValue(s.getItem());
            } else if (EquipmentEnhancer.isEnhanceable(s.getItem())) {
                equipment = s;
            }
        }
        if (equipment.isEmpty() || materialLevels <= 0) return ItemStack.EMPTY;
        return EquipmentEnhancer.addLevels(equipment, materialLevels);
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.EQUIPMENT_ENHANCE;
    }
}
