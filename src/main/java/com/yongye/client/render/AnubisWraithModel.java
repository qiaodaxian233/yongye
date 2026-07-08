package com.yongye.client.render;

import com.yongye.Yongye;
import com.yongye.entity.AnubisWraithEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/**
 * 阿努比斯恶灵的 GeckoLib 模型定位:指向 assets/yongye/{geo,textures/entity,animations} 下的三份资源。
 */
public class AnubisWraithModel extends GeoModel<AnubisWraithEntity> {

    private static final Identifier MODEL =
            Identifier.of(Yongye.MOD_ID, "geo/anubis_wraith.geo.json");
    private static final Identifier TEXTURE =
            Identifier.of(Yongye.MOD_ID, "textures/entity/anubis_wraith.png");
    private static final Identifier ANIMATION =
            Identifier.of(Yongye.MOD_ID, "animations/anubis_wraith.animation.json");

    @Override
    public Identifier getModelResource(AnubisWraithEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(AnubisWraithEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(AnubisWraithEntity animatable) {
        return ANIMATION;
    }
}
