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
 * BLIGHT = 夜蚀:整体优于下界合金(合金 20 防 3 韧 → 夜蚀 33 防 6 韧 + 每件 0.1 击退抗),
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
                        map.put(ArmorItem.Type.BOOTS, 6);
                        map.put(ArmorItem.Type.LEGGINGS, 9);
                        map.put(ArmorItem.Type.CHESTPLATE, 12);
                        map.put(ArmorItem.Type.HELMET, 6);
                        map.put(ArmorItem.Type.BODY, 12);
                    }),
                    20,                                                    // 附魔亲和(同金以下、钻石以上档)
                    SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.ofItems(ModItems.BLIGHT_INGOT),
                    List.of(new ArmorMaterial.Layer(Identifier.of(Yongye.MOD_ID, "blight"))),
                    6.0f,                                                  // 韧性
                    0.1f                                                   // 每件击退抗性
            ));

    public static void init() {
        Yongye.LOGGER.info("[夜蚀] 盔甲材质已注册");
    }
}
