package com.yongye.client.render;

import com.yongye.entity.VenomSpiderEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** VenomSpider 的 GeckoLib 渲染器。 */
public class VenomSpiderRenderer extends GeoEntityRenderer<VenomSpiderEntity> {
    public VenomSpiderRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new VenomSpiderModel());
        this.shadowRadius = 1.0f;
    }
}
