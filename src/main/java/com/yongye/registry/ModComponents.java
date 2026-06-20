package com.yongye.registry;

import com.mojang.serialization.Codec;
import com.yongye.Yongye;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 自定义数据组件。
 * SKILL_LEVEL: 存放技能书的等级(V值),挂在 ItemStack 上。
 */
public final class ModComponents {
    private ModComponents() {}

    public static final ComponentType<Integer> SKILL_LEVEL = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Yongye.MOD_ID, "skill_level"),
            ComponentType.<Integer>builder()
                    .codec(Codec.INT)
                    .packetCodec(PacketCodecs.INTEGER)
                    .build()
    );

    public static final ComponentType<Integer> ARTIFACT_LEVEL = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Yongye.MOD_ID, "artifact_level"),
            ComponentType.<Integer>builder()
                    .codec(Codec.INT)
                    .packetCodec(PacketCodecs.INTEGER)
                    .build()
    );

    /** ENHANCE_LEVEL: 装备(武器/盔甲)的强化等级,挂在 ItemStack 上,无上限;品质由等级换算。 */
    public static final ComponentType<Integer> ENHANCE_LEVEL = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Yongye.MOD_ID, "enhance_level"),
            ComponentType.<Integer>builder()
                    .codec(Codec.INT)
                    .packetCodec(PacketCodecs.INTEGER)
                    .build()
    );

    public static void init() {
        Yongye.LOGGER.info("[亡途荒夜] 数据组件已注册");
    }
}
