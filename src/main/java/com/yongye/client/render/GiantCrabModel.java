package com.yongye.client.render;

import com.yongye.Yongye;
import com.yongye.entity.GiantCrabEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/**
 * 巨型螃蟹的 GeckoLib 模型定位:指向 assets/yongye/{geo,textures/entity,animations} 下的三份资源。
 */
public class GiantCrabModel extends GeoModel<GiantCrabEntity> {

    private static final Identifier MODEL =
            Identifier.of(Yongye.MOD_ID, "geo/giant_crab.geo.json");
    private static final Identifier TEXTURE =
            Identifier.of(Yongye.MOD_ID, "textures/entity/giant_crab.png");
    private static final Identifier ANIMATION =
            Identifier.of(Yongye.MOD_ID, "animations/giant_crab.animation.json");

    @Override
    public Identifier getModelResource(GiantCrabEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GiantCrabEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(GiantCrabEntity animatable) {
        return ANIMATION;
    }
}
