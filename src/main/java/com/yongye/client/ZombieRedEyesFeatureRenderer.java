package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;

/**
 * m310(作者点名):所有僵尸——僵尸/尸壳/溺尸/僵尸村民——眼睛变红 + 周身冒紫色魔粒。
 *
 * <p>红眼 = 一张除眼睛全透明的叠层贴图,按 m280 精英叠皮同款方式在原模型上重渲一层;
 * 用发光眼层(蜘蛛眼同款)+ 满亮 lightmap,黑夜里也是两点红光——夜蚀世界的僵尸该有的样子。
 * 紫光 = 渲染时客户端本地撒 WITCH 魔粒,只对看得见的僵尸生效,零网络、零服务端开销。
 *
 * <p>贴图两张:僵尸系(64x64 僵尸脸位)与僵尸村民(村民头 8x10,眼位不同)。仅客户端。
 */
@Environment(EnvType.CLIENT)
public class ZombieRedEyesFeatureRenderer<T extends Entity, M extends EntityModel<T>> extends FeatureRenderer<T, M> {

    private static final Identifier ZOMBIE_EYES =
            Identifier.of(Yongye.MOD_ID, "textures/entity/zombie_red_eyes.png");
    private static final Identifier VILLAGER_EYES =
            Identifier.of(Yongye.MOD_ID, "textures/entity/zombie_villager_red_eyes.png");

    private final Identifier tex;

    public ZombieRedEyesFeatureRenderer(FeatureRendererContext<T, M> context, boolean villagerLayout) {
        super(context);
        this.tex = villagerLayout ? VILLAGER_EYES : ZOMBIE_EYES;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       T entity, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {
        if (!(entity instanceof LivingEntity living) || living.isInvisible()) return;
        YongyeConfig cfg = YongyeConfig.get();

        if (cfg.zombieRedEyes) {
            // 【待编译验证】RenderLayer.getEyes(蜘蛛眼同款发光层,标准API首用;
            //   报错把下一行换成 RenderLayer.getEntityCutoutNoCull(tex)——不叠加发光但满亮照样红)
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEyes(tex));
            // 0xF000F0 = 满亮 lightmap:再黑的夜,眼睛也是红的
            getContextModel().render(matrices, vc, 0xF000F0, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        }

        // 紫色魔粒:每帧小概率一粒(约每秒数粒),只有被渲染(=看得见)的僵尸才冒,自动就近削减
        if (cfg.zombiePurpleAura && living.getRandom().nextFloat() < 0.16f) {
            double px = living.getX() + (living.getRandom().nextDouble() - 0.5) * living.getWidth() * 1.1;
            double py = living.getY() + living.getRandom().nextDouble() * living.getHeight();
            double pz = living.getZ() + (living.getRandom().nextDouble() - 0.5) * living.getWidth() * 1.1;
            living.getWorld().addParticle(ParticleTypes.WITCH, px, py, pz, 0, 0.03, 0);
        }
    }
}
