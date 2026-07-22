package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.entity.GanDiEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/**
 * 肝帝渲染器(m223):直接借原版玩家模型(宽臂)+ 模组皮肤贴图。
 * 换皮肤 = 覆盖 textures/entity/gandi.png,零代码。
 * 待编译验证:EntityModelLayers.PLAYER / PlayerEntityModel(ModelPart, boolean slim) /
 * BipedEntityRenderer(ctx, model, shadowRadius) 三处 yarn 名(常规写法,风险低)。
 */
public class GanDiRenderer extends BipedEntityRenderer<GanDiEntity, PlayerEntityModel<GanDiEntity>> {

    private static final Identifier TEX = Identifier.of(Yongye.MOD_ID, "textures/entity/gandi.png");

    public GanDiRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(GanDiEntity entity) {
        return TEX;
    }
}
