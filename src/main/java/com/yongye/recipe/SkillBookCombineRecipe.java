package com.yongye.recipe;

import com.yongye.YongyeConfig;
import com.yongye.item.SkillBookItem;
import com.yongye.item.SkillType;
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
 * 通用技能书同级合成:2 本同类型同等级技能书(V_L) [+ 阶段材料] → 1 本同类型 V_{L+1}。
 * 阶段材料门槛沿用血量技能书的阈值(配置可调)。
 */
public class SkillBookCombineRecipe extends SpecialCraftingRecipe {

    public SkillBookCombineRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    private static Item requiredMaterial(int resultLevel) {
        YongyeConfig c = YongyeConfig.get();
        if (resultLevel >= c.catastropheBloodCoreThreshold) return ModItems.CATASTROPHE_BLOOD_CORE;
        if (resultLevel >= c.lifeCoreThreshold) return ModItems.LIFE_CORE;
        if (resultLevel >= c.lifeCrystalThreshold) return ModItems.LIFE_CRYSTAL;
        return null;
    }

    private static boolean isMaterial(Item item) {
        return item == ModItems.LIFE_CRYSTAL || item == ModItems.LIFE_CORE
                || item == ModItems.CATASTROPHE_BLOOD_CORE;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        SkillType type = null;
        int bookLevel = -1;
        int bookCount = 0;
        Item materialFound = null;
        int materialCount = 0;
        int other = 0;

        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof SkillBookItem sb) {
                int lvl = SkillBookItem.getLevel(s);
                if (bookCount == 0) {
                    type = sb.getType();
                    bookLevel = lvl;
                } else if (sb.getType() != type || lvl != bookLevel) {
                    return false; // 类型或等级不一致
                }
                bookCount++;
            } else if (isMaterial(s.getItem())) {
                materialFound = s.getItem();
                materialCount += s.getCount();
            } else {
                other++;
            }
        }

        if (other > 0 || bookCount != 2 || type == null || bookLevel < 1) return false;
        if (bookLevel >= YongyeConfig.get().skillBookMaxLevel) return false;

        Item need = requiredMaterial(bookLevel + 1);
        return need == null ? materialCount == 0 : (materialFound == need && materialCount == 1);
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() instanceof SkillBookItem sb) {
                return SkillBookItem.create(sb.getType(), SkillBookItem.getLevel(s) + 1);
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
        return ModRecipes.SKILL_BOOK_COMBINE;
    }
}
