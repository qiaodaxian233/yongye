package com.yongye.client.render;

import com.yongye.entity.AnubisEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 阿努比斯的 GeckoLib 渲染器(照龙/蜘蛛模板)。
 */
public class AnubisRenderer extends GeoEntityRenderer<AnubisEntity> {

    public AnubisRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new AnubisModel());
        // 高约 6.4 格的巨型体型;进游戏看比例再调。
        this.shadowRadius = 1.6f;
    }
}
