package com.yongye.registry;

import com.yongye.Yongye;
import com.yongye.recipe.ArtifactUpgradeRecipe;
import com.yongye.recipe.HealthBookCombineRecipe;
import com.yongye.recipe.SkillBookCombineRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModRecipes {
    private ModRecipes() {}

    public static final RecipeSerializer<HealthBookCombineRecipe> HEALTH_BOOK_COMBINE =
            Registry.register(Registries.RECIPE_SERIALIZER,
                    Identifier.of(Yongye.MOD_ID, "health_book_combine"),
                    new SpecialRecipeSerializer<>(HealthBookCombineRecipe::new));

    public static final RecipeSerializer<SkillBookCombineRecipe> SKILL_BOOK_COMBINE =
            Registry.register(Registries.RECIPE_SERIALIZER,
                    Identifier.of(Yongye.MOD_ID, "skill_book_combine"),
                    new SpecialRecipeSerializer<>(SkillBookCombineRecipe::new));

    public static final RecipeSerializer<ArtifactUpgradeRecipe> ARTIFACT_UPGRADE =
            Registry.register(Registries.RECIPE_SERIALIZER,
                    Identifier.of(Yongye.MOD_ID, "artifact_upgrade"),
                    new SpecialRecipeSerializer<>(ArtifactUpgradeRecipe::new));

    public static void init() {
        Yongye.LOGGER.info("[亡途荒夜] 配方序列化器已注册");
    }
}
