package com.yongye.registry;

import com.yongye.Yongye;
import com.yongye.item.ArtifactItem;
import com.yongye.item.ChaosBladeItem;
import com.yongye.item.ArtifactType;
import com.yongye.item.ClassBookItem;
import com.yongye.item.ClassWeaponItem;
import com.yongye.item.TankShieldItem;
import com.yongye.item.NightWingItem;
import com.yongye.item.PlayerClass;
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
            new HealthSkillBookItem(new Item.Settings().maxCount(99)));

    // —— 混沌之刃:专属传说武器(固定高属性 + 三技能无需解锁) ——
    public static final Item WARD_BOOK = register("ward_book", new WardBookItem(new Item.Settings().maxCount(16)));

    // —— 职业选择书:右键开全职业选择界面,选定本命职业(取代旧的登录强制弹窗) ——
    public static final Item CLASS_SELECT_BOOK = register("class_select_book",
            new com.yongye.item.ClassSelectBookItem(new Item.Settings().maxCount(1)));

    public static final Item CHAOS_BLADE = register("chaos_blade",
            new ChaosBladeItem(new Item.Settings()
                    .maxDamage(2500)
                    .rarity(Rarity.EPIC)
                    .attributeModifiers(ChaosBladeItem.baseAttributes())));

    // —— 超稀有材料(用于技能书 / 神器升级,详见文档第 15 章)——
    // 堆叠上限 99(原版上限),后期掉落多时少占背包格,缓解"拿不下"
    public static final Item LIFE_SHARD            = register("life_shard", new Item(new Item.Settings().maxCount(99)));
    public static final Item LIFE_CRYSTAL          = register("life_crystal", new Item(new Item.Settings().maxCount(99)));
    public static final Item LIFE_CORE             = register("life_core", new Item(new Item.Settings().maxCount(99)));
    public static final Item CATASTROPHE_BLOOD_CORE = register("catastrophe_blood_core", new Item(new Item.Settings().maxCount(99)));
    public static final Item ENDLESS_NIGHT_DUST    = register("endless_night_dust", new Item(new Item.Settings().maxCount(99)));
    public static final Item RIFT_FRAGMENT         = register("rift_fragment", new Item(new Item.Settings().maxCount(99)));
    public static final Item ABYSS_SOUL_CRYSTAL    = register("abyss_soul_crystal", new Item(new Item.Settings().maxCount(99)));
    public static final Item ENDING_ESSENCE        = register("ending_essence", new Item(new Item.Settings().maxCount(99)));

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

    // —— 职业书(精英掉落,右键学习)——
    private static final Map<PlayerClass, Item> CLASS_BOOKS = new EnumMap<>(PlayerClass.class);
    static {
        for (PlayerClass c : PlayerClass.values()) {
            CLASS_BOOKS.put(c, register("class_book_" + c.id, new ClassBookItem(c, new Item.Settings().maxCount(1))));
        }
    }
    public static Item getClassBook(PlayerClass c) {
        return CLASS_BOOKS.get(c);
    }
    public static Map<PlayerClass, Item> classBooks() {
        return CLASS_BOOKS;
    }

    // —— 通用技能书(文档 13.4)——
    private static final Map<SkillType, Item> SKILL_BOOKS = new EnumMap<>(SkillType.class);

    static {
        for (SkillType t : SkillType.values()) {
            SKILL_BOOKS.put(t, register("skill_book_" + t.id, new SkillBookItem(t, new Item.Settings().maxCount(99))));
        }
    }

    public static Item getSkillBook(SkillType type) {
        return SKILL_BOOKS.get(type);
    }

    public static Map<SkillType, Item> skillBooks() {
        return SKILL_BOOKS;
    }

    // —— 职业专属武器(m42:精英/Boss 稀有掉落或创造获取)——
    private static final Map<PlayerClass, Item> CLASS_WEAPONS = new EnumMap<>(PlayerClass.class);
    static {
        for (PlayerClass c : PlayerClass.values()) {
            CLASS_WEAPONS.put(c, register("class_weapon_" + c.id,
                    new ClassWeaponItem(c, new Item.Settings()
                            .maxDamage(2000)
                            .rarity(Rarity.EPIC)
                            .attributeModifiers(ClassWeaponItem.baseAttributes(c)))));
        }
    }
    public static Item getClassWeapon(PlayerClass c) {
        return CLASS_WEAPONS.get(c);
    }
    public static Map<PlayerClass, Item> classWeapons() {
        return CLASS_WEAPONS;
    }

    // —— 坦克专属盾·磐盾(m44)——
    public static final Item TANK_SHIELD = register("tank_shield",
            new TankShieldItem(new Item.Settings()
                    .maxDamage(1500)
                    .rarity(Rarity.EPIC)
                    .attributeModifiers(TankShieldItem.offhandAttributes())));

        // —— 永夜之翼:可穿戴背饰,继承鞘翅自带滑翔(羽翼 voxel 模型) ——
    public static final Item NIGHT_WING = register("night_wing",
            new NightWingItem(new Item.Settings()
                    .maxDamage(648)
                    .rarity(Rarity.EPIC)));

private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(Yongye.MOD_ID, name), item);
    }

    public static void init() {
        Yongye.LOGGER.info("[永夜] 物品已注册");
    }
}
