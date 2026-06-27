package com.yongye.client.render;

import com.yongye.entity.RedSpiderEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** RedSpider 的 GeckoLib 渲染器。 */
public class RedSpiderRenderer extends GeoEntityRenderer<RedSpiderEntity> {
    public RedSpiderRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new RedSpiderModel());
        this.shadowRadius = 1.6f;
    }
}
