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
 * 0岛风(细臂)/1晚安(宽臂)/2不爱肝(细臂)/3迷人(宽臂)/4芥末(宽臂);皮肤在 textures/entity/gandi_*.png,覆盖即换。
 * 待编译验证:EntityModelLayers.PLAYER/PLAYER_SLIM、PlayerEntityModel(ModelPart,boolean)、
 * render(entity,yaw,tickDelta,matrices,vertexConsumers,light) 覆写签名。
 */
public class GanDiRenderer extends BipedEntityRenderer<GanDiEntity, PlayerEntityModel<GanDiEntity>> {

    private static final Identifier[] TEX = {
            Identifier.of(Yongye.MOD_ID, "textures/entity/gandi_daofeng.png"),
            Identifier.of(Yongye.MOD_ID, "textures/entity/gandi_wanan.png"),
            Identifier.of(Yongye.MOD_ID, "textures/entity/gandi_bugan.png"),
            Identifier.of(Yongye.MOD_ID, "textures/entity/gandi_miren.png"),
            Identifier.of(Yongye.MOD_ID, "textures/entity/gandi_jiemo.png"),
    };
    private static final boolean[] SLIM = {true, false, true, false, false};

    private final PlayerEntityModel<GanDiEntity> wide;
    private final PlayerEntityModel<GanDiEntity> slim;

    public GanDiRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
    }

    // —— m413 自定义皮肤(作者点名:输 ID 自动拉皮肤)——配置串解析结果按串缓存,零逐帧 split ——
    private static String parsedRaw = null;
    private static String[] parsedIds = new String[0];

    /** 该变体槽的自定义皮肤;null=没配/还没拉到 → 回退原贴图与原臂型。 */
    private static GanDiSkinCache.SkinData customSkin(int v) {
        String raw = com.yongye.YongyeConfig.get().summonGanDiSkins;
        if (raw == null) raw = "";
        if (!raw.equals(parsedRaw)) { parsedRaw = raw; parsedIds = raw.split(",", -1); }
        if (v >= parsedIds.length) return null;
        return GanDiSkinCache.get(parsedIds[v]);
    }

    @Override
    public void render(GanDiEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        int v = clamp(entity.getVariant());
        GanDiSkinCache.SkinData custom = customSkin(v);
        this.model = (custom != null ? custom.slim() : SLIM[v]) ? slim : wide;
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(GanDiEntity entity) {
        int v = clamp(entity.getVariant());
        GanDiSkinCache.SkinData custom = customSkin(v);
        return custom != null ? custom.tex() : TEX[v];
    }

    private static int clamp(int v) { return Math.max(0, Math.min(4, v)); }
}
