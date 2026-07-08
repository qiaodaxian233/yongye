package com.yongye.client.render;

import com.yongye.entity.GiantCrabEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 巨型螃蟹的 GeckoLib 渲染器(照龙/蜘蛛模板)。
 */
public class GiantCrabRenderer extends GeoEntityRenderer<GiantCrabEntity> {

    public GiantCrabRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GiantCrabModel());
        // 横宽体型;进游戏看比例再调。
        this.shadowRadius = 1.4f;
    }
}
