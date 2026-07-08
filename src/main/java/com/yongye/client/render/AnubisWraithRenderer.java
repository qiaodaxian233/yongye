package com.yongye.client.render;

import com.yongye.entity.AnubisWraithEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 阿努比斯恶灵的 GeckoLib 渲染器(照龙/蜘蛛模板)。
 */
public class AnubisWraithRenderer extends GeoEntityRenderer<AnubisWraithEntity> {

    public AnubisWraithRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new AnubisWraithModel());
        // 小型木乃伊;进游戏看比例再调。
        this.shadowRadius = 0.6f;
    }
}
