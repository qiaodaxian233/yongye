package com.yongye.entity;

import net.minecraft.entity.EntityType;

// GeckoLib 4.x 「替换原版实体」官方 API。这几条 import 与 m162 已编过的 ToroEnderDragonEntity 同包。
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 「夜绿末影龙」替身动画对象(GeoReplacedEntity)。
 *
 * <p>关键:这是一个**独立的轻量对象**,不是实体——原版末影龙实体(EnderDragonEntity)一个字节都不碰。
 * GeckoLib 用它承载动画,渲染器把它和原版龙绑定,从而只换「外观模型」,而飞行、BOSS 血条、
 * 龙息、水晶回血、阶段、死亡演出等原版龙的全部行为**原样保留**。
 *
 * <p>原版龙永远在空中飞,所以这里恒定循环 fly 动画(动画文件里确有 fly 这条)。
 */
public class ToroDragonReplacement implements GeoReplacedEntity {

    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("fly");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fly", 5, state -> state.setAndContinue(FLY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /** 声明本替身要接管的原版实体类型 = 末影龙。 */
    @Override
    public EntityType<?> getReplacingEntityType() {
        return EntityType.ENDER_DRAGON;
    }
}
