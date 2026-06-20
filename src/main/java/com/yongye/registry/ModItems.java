package com.yongye.registry;

import com.yongye.Yongye;
import com.yongye.item.HealthSkillBookItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 物品注册。
 * Phase 1: 血量强化技能书 + 超稀有材料系统(8 种材料)。
 */
public final class ModItems {
    private ModItems() {}

    public static final Item HEALTH_SKILL_BOOK = register("health_skill_book",
            new HealthSkillBookItem(new Item.Settings().maxCount(64)));

    // —— 超稀有材料(用于技能书 / 神器升级,详见文档第 15 章)——
    public static final Item LIFE_SHARD            = register("life_shard", new Item(new Item.Settings()));
    public static final Item LIFE_CRYSTAL          = register("life_crystal", new Item(new Item.Settings()));
    public static final Item LIFE_CORE             = register("life_core", new Item(new Item.Settings()));
    public static final Item CATASTROPHE_BLOOD_CORE = register("catastrophe_blood_core", new Item(new Item.Settings()));
    public static final Item ENDLESS_NIGHT_DUST    = register("endless_night_dust", new Item(new Item.Settings()));
    public static final Item RIFT_FRAGMENT         = register("rift_fragment", new Item(new Item.Settings()));
    public static final Item ABYSS_SOUL_CRYSTAL    = register("abyss_soul_crystal", new Item(new Item.Settings()));
    public static final Item ENDING_ESSENCE        = register("ending_essence", new Item(new Item.Settings()));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(Yongye.MOD_ID, name), item);
    }

    public static void init() {
        Yongye.LOGGER.info("[亡途荒夜] 物品已注册");
    }
}
