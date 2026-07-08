package com.yongye.client.render;

import com.yongye.entity.FirePhoenixEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 浴火凤凰的 GeckoLib 渲染器(照龙/蜘蛛模板)。
 */
public class FirePhoenixRenderer extends GeoEntityRenderer<FirePhoenixEntity> {

    public FirePhoenixRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new FirePhoenixModel());
        // 翼展很大,阴影半径给大点;进游戏看比例再调。
        this.shadowRadius = 2.2f;
    }
}
