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
import net.minecraft.util.Identifier;

/**
 * m310(作者点名):所有僵尸——僵尸/尸壳/溺尸/僵尸村民——眼睛变红。
 * m311(作者点名)分档:普通僵尸浅红微光,精英僵尸深红强光;紫气移交 {@link MobAuraFeatureRenderer}(全怪分档)。
 *
 * <p>红眼 = 一张除眼睛全透明的叠层贴图,按 m280 精英叠皮同款方式在原模型上重渲一层;
 * 用发光眼层(蜘蛛眼同款)+ 满亮 lightmap,黑夜里也是两点红光——夜蚀世界的僵尸该有的样子。
 * 紫光 = 渲染时客户端本地撒 WITCH 魔粒,只对看得见的僵尸生效,零网络、零服务端开销。
 *
 * <p>贴图两张:僵尸系(64x64 僵尸脸位)与僵尸村民(村民头 8x10,眼位不同)。仅客户端。
 */
@Environment(EnvType.CLIENT)
public class ZombieRedEyesFeatureRenderer<T extends Entity, M extends EntityModel<T>> extends FeatureRenderer<T, M> {

    /** 深红·强光(精英) / 浅红·微光(普通),m311 按怪分档 */
    private static final Identifier ZOMBIE_EYES_DEEP =
            Identifier.of(Yongye.MOD_ID, "textures/entity/zombie_red_eyes.png");
    private static final Identifier ZOMBIE_EYES_LIGHT =
            Identifier.of(Yongye.MOD_ID, "textures/entity/zombie_red_eyes_light.png");
    private static final Identifier VILLAGER_EYES_DEEP =
            Identifier.of(Yongye.MOD_ID, "textures/entity/zombie_villager_red_eyes.png");
    private static final Identifier VILLAGER_EYES_LIGHT =
            Identifier.of(Yongye.MOD_ID, "textures/entity/zombie_villager_red_eyes_light.png");

    private final Identifier texLight, texDeep;

    public ZombieRedEyesFeatureRenderer(FeatureRendererContext<T, M> context, boolean villagerLayout) {
        super(context);
        this.texLight = villagerLayout ? VILLAGER_EYES_LIGHT : ZOMBIE_EYES_LIGHT;
        this.texDeep = villagerLayout ? VILLAGER_EYES_DEEP : ZOMBIE_EYES_DEEP;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       T entity, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {
        if (!(entity instanceof LivingEntity living) || living.isInvisible()) return;
        YongyeConfig cfg = YongyeConfig.get();

        if (cfg.zombieRedEyes) {
            // m311:精英(名字含「精英」,与 EliteSkinFeatureRenderer 同判)深红强光,普通浅红微光
            boolean elite = living.hasCustomName() && living.getCustomName() != null
                    && living.getCustomName().getString().contains("精英");
            // RenderLayer.getEyes 已全绿(m310 编译通过)
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEyes(elite ? texDeep : texLight));
            // 0xF000F0 = 满亮 lightmap:再黑的夜,眼睛也是红的
            getContextModel().render(matrices, vc, 0xF000F0, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        }

        // m311:紫气不再在此发射——移交 MobAuraFeatureRenderer 全怪分档(普通轻微/精英中等/BOSS高等)
    }
}
