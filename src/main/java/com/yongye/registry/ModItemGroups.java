package com.yongye.registry;

import com.yongye.Yongye;
import com.yongye.item.HealthSkillBookItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 创造模式物品组。
 */
public final class ModItemGroups {
    private ModItemGroups() {}

    public static final RegistryKey<ItemGroup> GENERAL_KEY =
            RegistryKey.of(Registries.ITEM_GROUP.getKey(), Identifier.of(Yongye.MOD_ID, "general"));

    public static void init() {
        Registry.register(Registries.ITEM_GROUP, GENERAL_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(ModItems.LIFE_CORE))
                .displayName(Text.translatable("itemGroup.yongye.general"))
                .build());

        ItemGroupEvents.modifyEntriesEvent(GENERAL_KEY).register(entries -> {
            // 技能书:放几本不同等级方便测试
            entries.add(ModItems.CHAOS_BLADE);
            entries.add(ModItems.WARD_BOOK);
            for (com.yongye.item.PlayerClass c : com.yongye.item.PlayerClass.values()) {
                entries.add(ModItems.getClassBook(c));
            }
            entries.add(HealthSkillBookItem.create(1));
            entries.add(HealthSkillBookItem.create(5));
            entries.add(HealthSkillBookItem.create(10));
            entries.add(HealthSkillBookItem.create(100));
            entries.add(HealthSkillBookItem.create(1000));
            // 其它技能书:每种放 V1/V10/V100 三档示例
            for (com.yongye.item.SkillType st : com.yongye.item.SkillType.values()) {
                entries.add(com.yongye.item.SkillBookItem.create(st, 1));
                entries.add(com.yongye.item.SkillBookItem.create(st, 10));
                entries.add(com.yongye.item.SkillBookItem.create(st, 100));
            }
            // 材料
            entries.add(ModItems.LIFE_SHARD);
            entries.add(ModItems.LIFE_CRYSTAL);
            entries.add(ModItems.LIFE_CORE);
            entries.add(ModItems.CATASTROPHE_BLOOD_CORE);
            entries.add(ModItems.ENDLESS_NIGHT_DUST);
            entries.add(ModItems.RIFT_FRAGMENT);
            entries.add(ModItems.ABYSS_SOUL_CRYSTAL);
            entries.add(ModItems.ENDING_ESSENCE);
            // 方块
            entries.add(ModBlocks.CATASTROPHE_CORE);

            // 背包神器:每种放 残破(1)/远古(3)/终焉(6) 三档示例
            for (com.yongye.item.ArtifactType t : com.yongye.item.ArtifactType.values()) {
                entries.add(com.yongye.item.ArtifactItem.create(t, 1));
                entries.add(com.yongye.item.ArtifactItem.create(t, 3));
                entries.add(com.yongye.item.ArtifactItem.create(t, 6));
            }
        });

        Yongye.LOGGER.info("[亡途荒夜] 物品组已注册");
    }
}
