package com.yongye.client.render;

import com.yongye.Yongye;
import com.yongye.entity.RedSpiderEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** RedSpider 的 GeoModel,指向 assets/yongye/{geo,textures/entity,animations}/red_spider.* */
public class RedSpiderModel extends GeoModel<RedSpiderEntity> {
    private static final Identifier MODEL = Identifier.of(Yongye.MOD_ID, "geo/red_spider.geo.json");
    private static final Identifier TEXTURE = Identifier.of(Yongye.MOD_ID, "textures/entity/red_spider.png");
    private static final Identifier ANIMATION = Identifier.of(Yongye.MOD_ID, "animations/red_spider.animation.json");

    @Override
    public Identifier getModelResource(RedSpiderEntity animatable) { return MODEL; }
    @Override
    public Identifier getTextureResource(RedSpiderEntity animatable) { return TEXTURE; }
    @Override
    public Identifier getAnimationResource(RedSpiderEntity animatable) { return ANIMATION; }
}
