package com.yongye.client.render;

import com.yongye.Yongye;
import com.yongye.entity.AnubisEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/**
 * 阿努比斯的 GeckoLib 模型定位:指向 assets/yongye/{geo,textures/entity,animations} 下的三份资源。
 */
public class AnubisModel extends GeoModel<AnubisEntity> {

    private static final Identifier MODEL =
            Identifier.of(Yongye.MOD_ID, "geo/anubis.geo.json");
    private static final Identifier TEXTURE =
            Identifier.of(Yongye.MOD_ID, "textures/entity/anubis.png");
    private static final Identifier ANIMATION =
            Identifier.of(Yongye.MOD_ID, "animations/anubis.animation.json");

    @Override
    public Identifier getModelResource(AnubisEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(AnubisEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(AnubisEntity animatable) {
        return ANIMATION;
    }
}
