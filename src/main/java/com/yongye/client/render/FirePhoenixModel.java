package com.yongye.client.render;

import com.yongye.Yongye;
import com.yongye.entity.FirePhoenixEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/**
 * 浴火凤凰的 GeckoLib 模型定位:指向 assets/yongye/{geo,textures/entity,animations} 下的三份资源。
 */
public class FirePhoenixModel extends GeoModel<FirePhoenixEntity> {

    private static final Identifier MODEL =
            Identifier.of(Yongye.MOD_ID, "geo/fire_phoenix.geo.json");
    private static final Identifier TEXTURE =
            Identifier.of(Yongye.MOD_ID, "textures/entity/fire_phoenix.png");
    private static final Identifier ANIMATION =
            Identifier.of(Yongye.MOD_ID, "animations/fire_phoenix.animation.json");

    @Override
    public Identifier getModelResource(FirePhoenixEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(FirePhoenixEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(FirePhoenixEntity animatable) {
        return ANIMATION;
    }
}
