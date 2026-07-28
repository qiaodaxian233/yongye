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
 *   2 本技能书(V_a + V_b,等级可不同)[+ 对应阶段材料(按结果级)] → 1 本 V_{a+b}(封顶钳制;m319 加法合并,不再亏级)。
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
        long lvlSum = 0;
        int lvlHigh = 0;
        net.minecraft.item.Item materialFound = null;
        int materialCount = 0;
        int otherCount = 0;

        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof HealthSkillBookItem) {
                int lvl = HealthSkillBookItem.getLevel(s);
                lvlSum += Math.max(1, lvl);        // m319:加法合并,等级可不同
                lvlHigh = Math.max(lvlHigh, lvl);
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
        if (lvlSum < 2) return false;

        int max = YongyeConfig.get().skillBookMaxLevel;
        int resultLevel = (int) Math.min(max, lvlSum);
        if (resultLevel <= lvlHigh) return false; // 封顶后合成不涨级,禁掉防误合亏书
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
        long sum = 0;   // m319:两本等级相加,封顶钳制
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() instanceof HealthSkillBookItem) {
                sum += Math.max(1, HealthSkillBookItem.getLevel(s));
            }
        }
        if (sum < 2) return ItemStack.EMPTY;
        return HealthSkillBookItem.create((int) Math.min(YongyeConfig.get().skillBookMaxLevel, sum));
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
