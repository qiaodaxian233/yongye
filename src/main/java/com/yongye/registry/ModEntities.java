package com.yongye.registry;

import com.yongye.Yongye;
import com.yongye.entity.RedSpiderEntity;
import com.yongye.entity.ToroEnderDragonEntity;
import com.yongye.entity.VenomSpiderEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * 自定义实体注册。本 mod 此前没有自定义实体(BOSS/精英都是给原版怪挂标记+皮肤层),
 * 这是第一个。需要 GeckoLib 前置渲染基岩模型。
 */
public final class ModEntities {
    private ModEntities() {}

    public static final RegistryKey<EntityType<?>> TORO_ENDER_DRAGON_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Yongye.MOD_ID, "toro_ender_dragon"));

    public static final EntityType<ToroEnderDragonEntity> TORO_ENDER_DRAGON =
            Registry.register(Registries.ENTITY_TYPE, TORO_ENDER_DRAGON_KEY.getValue(),
                    EntityType.Builder.create(ToroEnderDragonEntity::new, SpawnGroup.MONSTER)
                            // 体型先给个大致值(模型很大),进游戏看碰撞箱再调
                            .dimensions(6.0f, 5.0f)
                            .build("toro_ender_dragon"));

    // 精英·毒液蜘蛛
    public static final RegistryKey<EntityType<?>> VENOM_SPIDER_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Yongye.MOD_ID, "venom_spider"));
    public static final EntityType<VenomSpiderEntity> VENOM_SPIDER =
            Registry.register(Registries.ENTITY_TYPE, VENOM_SPIDER_KEY.getValue(),
                    EntityType.Builder.create(VenomSpiderEntity::new, SpawnGroup.MONSTER)
                            .dimensions(1.6f, 1.0f)
                            .build("venom_spider"));

    // BOSS·红蜘蛛
    public static final RegistryKey<EntityType<?>> RED_SPIDER_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Yongye.MOD_ID, "red_spider"));
    public static final EntityType<RedSpiderEntity> RED_SPIDER =
            Registry.register(Registries.ENTITY_TYPE, RED_SPIDER_KEY.getValue(),
                    EntityType.Builder.create(RedSpiderEntity::new, SpawnGroup.MONSTER)
                            .dimensions(3.0f, 1.8f)
                            .build("red_spider"));

    public static void init() {
        FabricDefaultAttributeRegistry.register(TORO_ENDER_DRAGON, ToroEnderDragonEntity.createDragonAttributes());
        FabricDefaultAttributeRegistry.register(VENOM_SPIDER, VenomSpiderEntity.createVenomSpiderAttributes());
        FabricDefaultAttributeRegistry.register(RED_SPIDER, RedSpiderEntity.createRedSpiderAttributes());
        Yongye.LOGGER.info("[永夜] 自定义实体已注册:toro_ender_dragon / venom_spider / red_spider");
    }
}
