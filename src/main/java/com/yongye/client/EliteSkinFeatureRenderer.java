package com.yongye.client;

import com.yongye.Yongye;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.Identifier;

/**
 * 精英怪叠层贴图:在原版模型上再渲染一层 mod 内的精英贴图,只对名字含「精英」的怪生效。
 * 不替换原版贴图——普通同种怪保持原样。仅客户端。
 *
 * 注:1.21.1 为实体渲染状态重构(1.21.2)之前的旧体系,FeatureRenderer 仍是实体式回调。
 */
@Environment(EnvType.CLIENT)
public class EliteSkinFeatureRenderer<T extends Entity, M extends EntityModel<T>> extends FeatureRenderer<T, M> {

    public EliteSkinFeatureRenderer(FeatureRendererContext<T, M> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       T entity, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {
        if (!(entity instanceof LivingEntity living)) return;
        if (!isElite(living)) return;
        Identifier tex = textureFor(living);
        if (tex == null) return;

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(tex));
        // 用上下文模型(即原版同款模型)叠渲一层精英贴图;LEQUAL 深度测试下后绘者覆盖,无 z-fighting。
        getContextModel().render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
    }

    /** 名字含「精英」即视为精英(自定义名会同步到客户端)。 */
    private static boolean isElite(LivingEntity e) {
        return e.hasCustomName() && e.getCustomName() != null
                && e.getCustomName().getString().contains("精英");
    }

    /** 按怪种返回 mod 内精英贴图;没有对应贴图的种类返回 null(保持原版外观)。 */
    private static Identifier textureFor(LivingEntity e) {
        String name;
        if (e instanceof AbstractSkeletonEntity) name = "elite_skeleton";
        else if (e instanceof WitchEntity) name = "elite_witch";
        else if (e instanceof ZombieEntity) name = "elite_zombie";
        else if (e instanceof CreeperEntity) name = "elite_creeper";
        else if (e instanceof SpiderEntity) name = "elite_spider";
        else return null;
        return Identifier.of(Yongye.MOD_ID, "textures/entity/" + name + ".png");
    }
}
