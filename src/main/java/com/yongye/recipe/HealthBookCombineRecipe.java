package com.yongye.recipe;

import com.yongye.YongyeConfig;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.registry.ModItems;
import com.yongye.registry.ModRecipes;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/**
 * 血量强化技能书同级合成:
 *   2 本同级技能书(V_L) [+ 对应阶段材料] → 1 本 V_{L+1}。
 *
 * 阶段材料门槛(文档 13.2,阈值可在配置调整):
 *   结果等级 >= catastropheBloodCoreThreshold(默认1000) -> 需 灾变血核
 *   结果等级 >= lifeCoreThreshold(默认100)            -> 需 生命核心
 *   结果等级 >= lifeCrystalThreshold(默认10)          -> 需 生命结晶
 *   低于上述                                          -> 无需额外材料
 */
public class HealthBookCombineRecipe extends SpecialCraftingRecipe {

    public HealthBookCombineRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    /** 计算结果等级所需材料,返回 null 表示不需要。 */
    private static net.minecraft.item.Item requiredMaterial(int resultLevel) {
        YongyeConfig c = YongyeConfig.get();
        if (resultLevel >= c.catastropheBloodCoreThreshold) return ModItems.CATASTROPHE_BLOOD_CORE;
        if (resultLevel >= c.lifeCoreThreshold)             return ModItems.LIFE_CORE;
        if (resultLevel >= c.lifeCrystalThreshold)          return ModItems.LIFE_CRYSTAL;
        return null;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        int bookCount = 0;
        int bookLevel = -1;
        net.minecraft.item.Item materialFound = null;
        int materialCount = 0;
        int otherCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof HealthSkillBookItem) {
                int lvl = HealthSkillBookItem.getLevel(s);
                if (bookCount == 0) {
                    bookLevel = lvl;
                } else if (lvl != bookLevel) {
                    return false; // 等级不一致
                }
                bookCount++;
            } else if (isMaterial(s.getItem())) {
                materialFound = s.getItem();
                materialCount += s.getCount();
            } else {
                otherCount++;
            }
        }

        if (otherCount > 0) return false;
        if (bookCount != 2) return false;
        if (bookLevel < 1) return false;

        int max = YongyeConfig.get().skillBookMaxLevel;
        if (bookLevel >= max) return false; // 已封顶

        int resultLevel = bookLevel + 1;
        net.minecraft.item.Item need = requiredMaterial(resultLevel);
        if (need == null) {
            return materialCount == 0;
        } else {
            return materialFound == need && materialCount == 1;
        }
    }

    private static boolean isMaterial(net.minecraft.item.Item item) {
        return item == ModItems.LIFE_CRYSTAL
                || item == ModItems.LIFE_CORE
                || item == ModItems.CATASTROPHE_BLOOD_CORE;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        int bookLevel = 1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() instanceof HealthSkillBookItem) {
                bookLevel = HealthSkillBookItem.getLevel(s);
                break;
            }
        }
        return HealthSkillBookItem.create(bookLevel + 1);
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.HEALTH_BOOK_COMBINE;
    }
}
