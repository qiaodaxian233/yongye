package com.yongye.client.render;

import com.yongye.Yongye;
import com.yongye.entity.ToroEnderDragonEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib 模型定位:指向放在 assets/yongye/{geo,textures/entity,animations} 下的三份资源。
 */
public class ToroEnderDragonModel extends GeoModel<ToroEnderDragonEntity> {

    private static final Identifier MODEL =
            Identifier.of(Yongye.MOD_ID, "geo/toro_ender_dragon.geo.json");
    private static final Identifier TEXTURE =
            Identifier.of(Yongye.MOD_ID, "textures/entity/toro_ender_dragon.png");
    private static final Identifier ANIMATION =
            Identifier.of(Yongye.MOD_ID, "animations/toro_ender_dragon.animation.json");

    @Override
    public Identifier getModelResource(ToroEnderDragonEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(ToroEnderDragonEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(ToroEnderDragonEntity animatable) {
        return ANIMATION;
    }
}
