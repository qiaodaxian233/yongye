package com.yongye.registry;

import com.yongye.Yongye;
import com.yongye.entity.AnubisEntity;
import com.yongye.entity.AnubisWraithEntity;
import com.yongye.entity.DeathMageEntity;
import com.yongye.entity.FirePhoenixEntity;
import com.yongye.entity.GiantCrabEntity;
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

    // m345 说明:启动时每个自定义实体各有一条 "No data fixer registered for yongye:xxx" 日志——
    // 这是 EntityType.Builder.build(String) 在**开发环境**做 DataFixer 选型查询的固有提示
    // (yarn 1.21.1 该方法唯一重载,字符串仅用于此查询),模组不做跨版本存档升级本就无需注册 fixer;
    // 生产环境日志级别不同、玩家侧无此噪音。压制它需 mixin 进 Util 的日志路径,风险远大于收益,故仅文档化。
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

    // BOSS·浴火凤凰(m169;免疫火/岩浆)
    public static final RegistryKey<EntityType<?>> FIRE_PHOENIX_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Yongye.MOD_ID, "fire_phoenix"));
    public static final EntityType<FirePhoenixEntity> FIRE_PHOENIX =
            Registry.register(Registries.ENTITY_TYPE, FIRE_PHOENIX_KEY.getValue(),
                    EntityType.Builder.create(FirePhoenixEntity::new, SpawnGroup.MONSTER)
                            // 体型按模型自带 hitbox 骨骼(51×53×51 单位 ≈ 3.2×3.3 格);进游戏再调
                            .dimensions(3.2f, 3.3f)
                            .makeFireImmune()
                            .build("fire_phoenix"));

    // BOSS·死亡法师(m170)
    public static final RegistryKey<EntityType<?>> DEATH_MAGE_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Yongye.MOD_ID, "death_mage"));
    public static final EntityType<DeathMageEntity> DEATH_MAGE =
            Registry.register(Registries.ENTITY_TYPE, DEATH_MAGE_KEY.getValue(),
                    EntityType.Builder.create(DeathMageEntity::new, SpawnGroup.MONSTER)
                            // 人形体型(模型整体包围盒 ≈ 2.3 宽含法袍/手臂,取身体 1.0×2.2);进游戏再调
                            .dimensions(1.0f, 2.2f)
                            .build("death_mage"));

    // 精英·巨型螃蟹(m170)
    public static final RegistryKey<EntityType<?>> GIANT_CRAB_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Yongye.MOD_ID, "giant_crab"));
    public static final EntityType<GiantCrabEntity> GIANT_CRAB =
            Registry.register(Registries.ENTITY_TYPE, GIANT_CRAB_KEY.getValue(),
                    EntityType.Builder.create(GiantCrabEntity::new, SpawnGroup.MONSTER)
                            // 体型按模型自带 hitbox 骨骼(44×24×48 单位 ≈ 3.0×1.5 格);进游戏再调
                            .dimensions(3.0f, 1.5f)
                            .build("giant_crab"));

    // BOSS·阿努比斯(m172;DragonCore 包主体形态)
    public static final RegistryKey<EntityType<?>> ANUBIS_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Yongye.MOD_ID, "anubis"));
    public static final EntityType<AnubisEntity> ANUBIS =
            Registry.register(Registries.ENTITY_TYPE, ANUBIS_KEY.getValue(),
                    EntityType.Builder.create(AnubisEntity::new, SpawnGroup.MONSTER)
                            // 模型整体包围盒宽 2.44 / 高 6.43 格(hitbox 骨是纯 pivot 没尺寸);进游戏再调
                            .dimensions(2.5f, 6.4f)
                            .build("anubis"));

    // 小怪·阿努比斯恶灵(m173;未来给阿努比斯当召唤物)
    public static final RegistryKey<EntityType<?>> ANUBIS_WRAITH_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Yongye.MOD_ID, "anubis_wraith"));
    public static final EntityType<AnubisWraithEntity> ANUBIS_WRAITH =
            Registry.register(Registries.ENTITY_TYPE, ANUBIS_WRAITH_KEY.getValue(),
                    EntityType.Builder.create(AnubisWraithEntity::new, SpawnGroup.MONSTER)
                            // 视觉高约 1.9 格(几何在原点下方、靠动画抬升,详见实体类注释);进游戏再调
                            .dimensions(1.0f, 1.9f)
                            .build("anubis_wraith"));

    // 术士·暗影分身(m262,术士小技能召唤物;玩家模型+暗紫剪影皮肤,友军)
    public static final RegistryKey<EntityType<?>> WARLOCK_CLONE_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Yongye.MOD_ID, "warlock_clone"));
    public static final EntityType<com.yongye.entity.WarlockCloneEntity> WARLOCK_CLONE =
            Registry.register(Registries.ENTITY_TYPE, WARLOCK_CLONE_KEY.getValue(),
                    EntityType.Builder.create(com.yongye.entity.WarlockCloneEntity::new, SpawnGroup.CREATURE)
                            .dimensions(0.6f, 1.8f)   // 玩家同款体型
                            .build("warlock_clone"));

    public static void init() {
        FabricDefaultAttributeRegistry.register(WARLOCK_CLONE, com.yongye.entity.WarlockCloneEntity.createCloneAttributes());
        FabricDefaultAttributeRegistry.register(TORO_ENDER_DRAGON, ToroEnderDragonEntity.createDragonAttributes());
        FabricDefaultAttributeRegistry.register(VENOM_SPIDER, VenomSpiderEntity.createVenomSpiderAttributes());
        FabricDefaultAttributeRegistry.register(RED_SPIDER, RedSpiderEntity.createRedSpiderAttributes());
        FabricDefaultAttributeRegistry.register(FIRE_PHOENIX, FirePhoenixEntity.createFirePhoenixAttributes());
        FabricDefaultAttributeRegistry.register(DEATH_MAGE, DeathMageEntity.createDeathMageAttributes());
        FabricDefaultAttributeRegistry.register(GIANT_CRAB, GiantCrabEntity.createGiantCrabAttributes());
        FabricDefaultAttributeRegistry.register(ANUBIS, AnubisEntity.createAnubisAttributes());
        FabricDefaultAttributeRegistry.register(ANUBIS_WRAITH, AnubisWraithEntity.createAnubisWraithAttributes());
        Yongye.LOGGER.info("[夜蚀] 自定义实体已注册:toro_ender_dragon / venom_spider / red_spider / fire_phoenix / death_mage / giant_crab / anubis / anubis_wraith");
    }
}
