package com.yongye.client.render;

import com.yongye.Yongye;
import com.yongye.entity.VenomSpiderEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** VenomSpider 的 GeoModel,指向 assets/yongye/{geo,textures/entity,animations}/venom_spider.* */
public class VenomSpiderModel extends GeoModel<VenomSpiderEntity> {
    private static final Identifier MODEL = Identifier.of(Yongye.MOD_ID, "geo/venom_spider.geo.json");
    private static final Identifier TEXTURE = Identifier.of(Yongye.MOD_ID, "textures/entity/venom_spider.png");
    private static final Identifier ANIMATION = Identifier.of(Yongye.MOD_ID, "animations/venom_spider.animation.json");

    @Override
    public Identifier getModelResource(VenomSpiderEntity animatable) { return MODEL; }
    @Override
    public Identifier getTextureResource(VenomSpiderEntity animatable) { return TEXTURE; }
    @Override
    public Identifier getAnimationResource(VenomSpiderEntity animatable) { return ANIMATION; }
}
