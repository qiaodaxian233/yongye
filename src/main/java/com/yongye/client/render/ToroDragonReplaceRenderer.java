package com.yongye.client.render;

import com.yongye.entity.ToroDragonReplacement;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/**
 * 把原版末影龙(EnderDragonEntity)的渲染器替换成 GeckoLib 的夜绿龙模型。
 *
 * <p>泛型 &lt;原版实体, 替身对象&gt;:第一个是被替换的原版龙,第二个是承载动画的替身。
 * 构造函数把「替身 GeoModel」+「替身对象实例」交给父类即可,GeckoLib 负责其余渲染。
 *
 * <p>对应 GeckoLib 官方示例 ReplacedCreeperRenderer(v4 / 1.21.1),只是去掉了苦力怕特有的
 * 膨胀缩放 preRender——龙不需要。模型很大,阴影半径给大些。
 */
public class ToroDragonReplaceRenderer
        extends GeoReplacedEntityRenderer<EnderDragonEntity, ToroDragonReplacement> {

    public ToroDragonReplaceRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ToroDragonReplacementModel(), new ToroDragonReplacement());
        this.shadowRadius = 2.5f;
    }

    /**
     * 修「末影龙倒着飞」(m185):原版末影龙是全 MC 唯一一只朝向反着存的实体——它的
     * bodyYaw 与飞行方向恒差 180°(历史遗留:Notch 的原始龙模型就是反着建的,原版
     * EnderDragonEntityRenderer 用 rotate(-yaw) 而非普通生物的 rotate(180-yaw) 来补偿,
     * 二者正好差 180°)。GeckoLib 的 GeoReplacedEntityRenderer#applyRotations 走的是
     * 普通生物写法 {@code mulPose(YP.rotationDegrees(180f - rotationYaw))}(已拉
     * GeckoLib 4.8 branch-1.21.1 源码逐字核对),套在原版龙身上就是尾巴朝前倒飞。
     * 这里把 yaw 补 180° 再交给父类,恰好抵消。自建龙(ToroEnderDragonEntity)是正常
     * yaw 语义的 HostileEntity,走另一个渲染器,不受影响。
     *
     * <p>签名依据 GeckoLib 4.8 源码 6 参活版(5 参已 @Deprecated;首参是替身对象非实体;
     * PoseStack 在 yarn 开发环境重映射为 MatrixStack)——【待编译验证】,若 build 报
     * 「method does not override」把报错贴来即改。
     */
    @Override
    protected void applyRotations(ToroDragonReplacement animatable, MatrixStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTick, float nativeScale) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw + 180f, partialTick, nativeScale);
    }
}
