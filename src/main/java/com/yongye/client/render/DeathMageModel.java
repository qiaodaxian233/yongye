package com.yongye.client.render;

import com.yongye.Yongye;
import com.yongye.entity.DeathMageEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/**
 * 死亡法师的 GeckoLib 模型定位:指向 assets/yongye/{geo,textures/entity,animations} 下的三份资源。
 */
public class DeathMageModel extends GeoModel<DeathMageEntity> {

    private static final Identifier MODEL =
            Identifier.of(Yongye.MOD_ID, "geo/death_mage.geo.json");
    private static final Identifier TEXTURE =
            Identifier.of(Yongye.MOD_ID, "textures/entity/death_mage.png");
    private static final Identifier ANIMATION =
            Identifier.of(Yongye.MOD_ID, "animations/death_mage.animation.json");

    @Override
    public Identifier getModelResource(DeathMageEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(DeathMageEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(DeathMageEntity animatable) {
        return ANIMATION;
    }
}
