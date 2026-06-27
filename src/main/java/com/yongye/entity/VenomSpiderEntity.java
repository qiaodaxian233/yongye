package com.yongye.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.world.World;

// GeckoLib 4.x —— 与 m162/m164/m165 已编过的实体同包(切勿改包路径)。
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 精英·毒液蜘蛛(GeckoLib 渲染基岩模型 + 动画)。
 * 扩展原版 SpiderEntity → 白嫖爬墙 + 蜘蛛 AI;只换外观/动画 + 拔高属性当精英怪。
 * 动画:idle→waiting,移动→walking(动画文件确有这两条)。
 */
public class VenomSpiderEntity extends SpiderEntity implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("waiting");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walking");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public VenomSpiderEntity(EntityType<? extends SpiderEntity> type, World world) {
        super(type, world);
    }

    /** 精英级属性(在原版蜘蛛基础上拔高)。 */
    public static DefaultAttributeContainer.Builder createVenomSpiderAttributes() {
        return SpiderEntity.createSpiderAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    // ===== GeckoLib =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
