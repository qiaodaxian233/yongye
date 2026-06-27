package com.yongye.client.render;

import com.yongye.entity.ToroDragonReplacement;
import net.minecraft.client.render.entity.EntityRendererFactory;
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
}
