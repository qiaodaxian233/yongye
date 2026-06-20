package com.yongye.registry;

import com.yongye.Yongye;
import com.yongye.item.ArtifactItem;
import com.yongye.item.ChaosBladeItem;
import com.yongye.item.ArtifactType;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.item.SkillBookItem;
import com.yongye.item.WardBookItem;
import com.yongye.item.SkillType;
import net.minecraft.item.Item;
import net.minecraft.util.Rarity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * 物品注册。
 * Phase 1: 血量强化技能书 + 超稀有材料系统(8 种材料)。
 * Phase 4: 背包神器(10 种)。
 */
public final class ModItems {
    private ModItems() {}

    public static final Item HEALTH_SKILL_BOOK = register("health_skill_book",
            new HealthSkillBookItem(new Item.Settings().maxCount(64)));

    // —— 混沌之刃:专属传说武器(固定高属性 + 三技能无需解锁) ——
    public static final Item WARD_BOOK = register("ward_book", new WardBookItem(new Item.Settings().maxCount(16)));

    public static final Item CHAOS_BLADE = register("chaos_blade",
            new ChaosBladeItem(new Item.Settings()
                    .maxDamage(2500)
                    .rarity(Rarity.EPIC)
                    .attributeModifiers(ChaosBladeItem.baseAttributes())));

    // —— 超稀有材料(用于技能书 / 神器升级,详见文档第 15 章)——
    public static final Item LIFE_SHARD            = register("life_shard", new Item(new Item.Settings()));
    public static final Item LIFE_CRYSTAL          = register("life_crystal", new Item(new Item.Settings()));
    public static final Item LIFE_CORE             = register("life_core", new Item(new Item.Settings()));
    public static final Item CATASTROPHE_BLOOD_CORE = register("catastrophe_blood_core", new Item(new Item.Settings()));
    public static final Item ENDLESS_NIGHT_DUST    = register("endless_night_dust", new Item(new Item.Settings()));
    public static final Item RIFT_FRAGMENT         = register("rift_fragment", new Item(new Item.Settings()));
    public static final Item ABYSS_SOUL_CRYSTAL    = register("abyss_soul_crystal", new Item(new Item.Settings()));
    public static final Item ENDING_ESSENCE        = register("ending_essence", new Item(new Item.Settings()));

    // —— 背包神器(文档第 14 章)——
    private static final Map<ArtifactType, Item> ARTIFACTS = new EnumMap<>(ArtifactType.class);

    static {
        for (ArtifactType t : ArtifactType.values()) {
            ARTIFACTS.put(t, register("artifact_" + t.id, new ArtifactItem(t, new Item.Settings().maxCount(16))));
        }
    }

    public static Item getArtifact(ArtifactType type) {
        return ARTIFACTS.get(type);
    }

    public static Map<ArtifactType, Item> artifacts() {
        return ARTIFACTS;
    }

    // —— 通用技能书(文档 13.4)——
    private static final Map<SkillType, Item> SKILL_BOOKS = new EnumMap<>(SkillType.class);

    static {
        for (SkillType t : SkillType.values()) {
            SKILL_BOOKS.put(t, register("skill_book_" + t.id, new SkillBookItem(t, new Item.Settings().maxCount(64))));
        }
    }

    public static Item getSkillBook(SkillType type) {
        return SKILL_BOOKS.get(type);
    }

    public static Map<SkillType, Item> skillBooks() {
        return SKILL_BOOKS;
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(Yongye.MOD_ID, name), item);
    }

    public static void init() {
        Yongye.LOGGER.info("[亡途荒夜] 物品已注册");
    }
}
