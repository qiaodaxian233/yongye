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
 * 通用技能书合成(m319 改加法合并):2 本**同类型**技能书(V_a + V_b,等级可不同)[+ 阶段材料]
 * → 1 本同类型 V_{a+b}(封顶钳制)。旧版 2×V_L→V_{L+1} 是亏级陷阱——学书是加法
 * (SkillEffectManager.learn: cur+level),两本 V5 分开学 = +10 级,旧合成只剩 V6 平白亏 4 级。
 * 阶段材料门槛按**结果等级**取档(配置阈值不变)。
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
        long lvlSum = 0;      // m319:等级相加(long 防 65535+65535 级溢出边界)
        int lvlHigh = 0;      // 两本中较高的一本,用于"合了不涨没意义"判定
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
                } else if (sb.getType() != type) {
                    return false; // 类型不一致(等级可不同:m319 加法合并)
                }
                lvlSum += Math.max(1, lvl);
                lvlHigh = Math.max(lvlHigh, lvl);
                bookCount++;
            } else if (isMaterial(s.getItem())) {
                materialFound = s.getItem();
                materialCount += s.getCount();
            } else {
                other++;
            }
        }

        if (other > 0 || bookCount != 2 || type == null || lvlSum < 2) return false;
        int cap = YongyeConfig.get().skillBookMaxLevel;
        int resultLevel = (int) Math.min(cap, lvlSum);
        if (resultLevel <= lvlHigh) return false; // 封顶后合成不涨级,禁掉防误合亏书

        Item need = requiredMaterial(resultLevel);
        return need == null ? materialCount == 0 : (materialFound == need && materialCount == 1);
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        SkillType type = null;
        long sum = 0;   // m319:两本等级相加,封顶钳制
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() instanceof SkillBookItem sb) {
                if (type == null) type = sb.getType();
                sum += Math.max(1, SkillBookItem.getLevel(s));
            }
        }
        if (type == null) return ItemStack.EMPTY;
        return SkillBookItem.create(type, (int) Math.min(YongyeConfig.get().skillBookMaxLevel, sum));
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
