package com.yongye.client.render;

import com.yongye.Yongye;
import com.yongye.entity.ToroDragonReplacement;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/**
 * 替身末影龙的 GeoModel——直接复用 m162 已放进仓库的同一套 geo/贴图/动画资源
 * (assets/yongye/{geo,textures/entity,animations}/toro_ender_dragon.*),不重复放资源。
 * 与 ToroEnderDragonModel 写法完全一致,仅泛型从实体换成替身对象 ToroDragonReplacement。
 */
public class ToroDragonReplacementModel extends GeoModel<ToroDragonReplacement> {

    private static final Identifier MODEL =
            Identifier.of(Yongye.MOD_ID, "geo/toro_ender_dragon.geo.json");
    private static final Identifier TEXTURE =
            Identifier.of(Yongye.MOD_ID, "textures/entity/toro_ender_dragon.png");
    private static final Identifier ANIMATION =
            Identifier.of(Yongye.MOD_ID, "animations/toro_ender_dragon.animation.json");

    @Override
    public Identifier getModelResource(ToroDragonReplacement animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(ToroDragonReplacement animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(ToroDragonReplacement animatable) {
        return ANIMATION;
    }
}
