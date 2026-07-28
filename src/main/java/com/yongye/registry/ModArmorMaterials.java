package com.yongye.registry;

import com.yongye.Yongye;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.EnumMap;
import java.util.List;

/**
 * 盔甲材质注册(m265)。
 * BLIGHT = 夜蚀:整体优于下界合金(合金 20 防 3 韧 → m272 上调后 48 防 12 韧 + 每件 0.2 击退抗),
 * 另有「夜蚀共鸣」逐件百分比加成见 BlightSetHandler(m272,实机反馈「属性太低」)。
 * 修复材料=蚀锭;穿戴层贴图指向 textures/models/armor/blight_layer_{1,2}.png。
 * 构造器/注册写法与真实 1.21.1 源码逐字核对(ArmorMaterial 七参 record + registerReference)。
 */
public final class ModArmorMaterials {
    private ModArmorMaterials() {}

    public static final RegistryEntry<ArmorMaterial> BLIGHT = Registry.registerReference(
            Registries.ARMOR_MATERIAL,
            Identifier.of(Yongye.MOD_ID, "blight"),
            new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                        map.put(ArmorItem.Type.BOOTS, 12);      // m338 再上调(锻造有爆震代价,数值配得上)
                        map.put(ArmorItem.Type.LEGGINGS, 18);
                        map.put(ArmorItem.Type.CHESTPLATE, 24);
                        map.put(ArmorItem.Type.HELMET, 12);
                        map.put(ArmorItem.Type.BODY, 24);
                    }),
                    25,                                                    // 附魔亲和(m272 上调)
                    SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.ofItems(ModItems.BLIGHT_INGOT),
                    List.of(new ArmorMaterial.Layer(Identifier.of(Yongye.MOD_ID, "blight"))),
                    16.0f,                                                 // 韧性(m338:12→16)
                    0.25f                                                  // 每件击退抗性(m338:0.2→0.25)
            ));

    public static void init() {
        Yongye.LOGGER.info("[夜蚀] 盔甲材质已注册");
    }
}
