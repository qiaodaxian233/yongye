package com.yongye.client.render;

import com.yongye.entity.DeathMageEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 死亡法师的 GeckoLib 渲染器(照龙/蜘蛛模板)。
 */
public class DeathMageRenderer extends GeoEntityRenderer<DeathMageEntity> {

    public DeathMageRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DeathMageModel());
        // 人形体型;进游戏看比例再调。
        this.shadowRadius = 0.8f;
    }
}
