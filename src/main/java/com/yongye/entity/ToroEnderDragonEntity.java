package com.yongye.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

// GeckoLib 4.x —— 与 m162/m164 已编过的实体同包(切勿改包路径)。
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 自定义末影龙 BOSS(夜绿/转龙核「真正的末影龙」模型,GeckoLib 渲染基岩模型+动画)。
 *
 * <p>m165:从「地面近战怪」改成**会飞的空中 BOSS**(跟末地那条差不多)——
 * 用 FlightMoveControl(飞行移动控制,自带无重力)+ BirdNavigation(飞行寻路),
 * MeleeAttackGoal 走飞行导航在 3D 里追玩家、俯冲攻击,不在地上走。
 */
public class ToroEnderDragonEntity extends HostileEntity implements GeoEntity {

    // 飞行动画(动画文件确有 fly/fly_idle/fly_walk):悬停 fly_idle、移动 fly_walk。
    private static final RawAnimation FLY_IDLE = RawAnimation.begin().thenLoop("fly_idle");
    private static final RawAnimation FLY_MOVE = RawAnimation.begin().thenLoop("fly_walk");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public ToroEnderDragonEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        // 飞行移动控制(第三参 true = 无重力);并显式置无重力,确保不会坠落。
        this.moveControl = new FlightMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    /** BOSS 基础属性(数值先给个能打的起点,后续平衡再调)。 */
    public static DefaultAttributeContainer.Builder createDragonAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 500.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.8)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    /** 飞行寻路(裸构造,避免调用可能随版本改名的 setter)。 */
    @Override
    protected EntityNavigation createNavigation(World world) {
        return new BirdNavigation(this, world);
    }

    @Override
    protected void initGoals() {
        // 近战追击走飞行导航,在 3D 空间里追玩家并俯冲攻击(无地面游荡 goal,不会落地走)。
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 32.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    /** 飞行 BOSS 不吃摔落伤害(无重力本就不会摔,双保险)。 */
    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    // ===== GeckoLib =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fly", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(FLY_MOVE);
            }
            return state.setAndContinue(FLY_IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
