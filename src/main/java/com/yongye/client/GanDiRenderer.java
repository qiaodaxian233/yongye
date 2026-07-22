package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.entity.GanDiEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * 肝帝渲染器(m224:四变体四皮肤,细/宽臂按皮肤格式切模型)。
 * 0岛风(细臂)/1晚安(宽臂)/2不爱肝(细臂)/3迷人(宽臂);皮肤在 textures/entity/gandi_*.png,覆盖即换。
 * 待编译验证:EntityModelLayers.PLAYER/PLAYER_SLIM、PlayerEntityModel(ModelPart,boolean)、
 * render(entity,yaw,tickDelta,matrices,vertexConsumers,light) 覆写签名。
 */
public class GanDiRenderer extends BipedEntityRenderer<GanDiEntity, PlayerEntityModel<GanDiEntity>> {

    private static final Identifier[] TEX = {
            Identifier.of(Yongye.MOD_ID, "textures/entity/gandi_daofeng.png"),
            Identifier.of(Yongye.MOD_ID, "textures/entity/gandi_wanan.png"),
            Identifier.of(Yongye.MOD_ID, "textures/entity/gandi_bugan.png"),
            Identifier.of(Yongye.MOD_ID, "textures/entity/gandi_miren.png"),
    };
    private static final boolean[] SLIM = {true, false, true, false};

    private final PlayerEntityModel<GanDiEntity> wide;
    private final PlayerEntityModel<GanDiEntity> slim;

    public GanDiRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(GanDiEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        this.model = SLIM[clamp(entity.getVariant())] ? slim : wide;
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(GanDiEntity entity) {
        return TEX[clamp(entity.getVariant())];
    }

    private static int clamp(int v) { return Math.max(0, Math.min(3, v)); }
}
