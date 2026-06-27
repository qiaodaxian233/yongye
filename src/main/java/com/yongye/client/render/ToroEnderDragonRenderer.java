package com.yongye.client.render;

import com.yongye.entity.ToroEnderDragonEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 末影龙的 GeckoLib 渲染器。把 GeoModel 传给父类即可,无需手动注册模型层/网格。
 */
public class ToroEnderDragonRenderer extends GeoEntityRenderer<ToroEnderDragonEntity> {

    public ToroEnderDragonRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ToroEnderDragonModel());
        // 模型很大,阴影半径给大点;进游戏看比例再调(也可能需要在这里 setScale)
        this.shadowRadius = 1.8f;
    }
}
