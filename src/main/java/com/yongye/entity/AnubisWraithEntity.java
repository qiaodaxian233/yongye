package com.yongye.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

// GeckoLib 4.x —— 与 m162~m170 已在树的实体同包(切勿改包路径)。
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 小怪·阿努比斯恶灵(m173 Stage1:召唤 + 渲染 + 基础动画)。
 *
 * <p>阿努比斯包里的木乃伊小怪,定位是<b>未来给阿努比斯 Stage2 当召唤物</b>,
 * 先按普通敌对怪注册,能单独召唤测试。
 *
 * <p><b>模型大坑(动过素材前必读)</b>:该模型几何体建在原点<b>下方约 2 格</b>
 * (y ∈ [-2.04, -0.06]),全靠每条动画里 torso 根骨 position +29~31 单位把身体抬回地面
 * (walk 的 29↔31 起伏就是悬浮效果)。所以:
 * ① 动画控制器必须<b>常驻</b>播 idle/walk,一旦没有动画在播模型就整只沉进地里;
 * ② 千万别去「修正」geo 的 Y 坐标,会和动画位移叠加飞到天上。
 *
 * <p>Stage2:attack1/attack2 按 AI 触发;恶灵出生动画(单独 animation 文件)当召唤登场演出。
 */
public class AnubisWraithEntity extends HostileEntity implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public AnubisWraithEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    /** 召唤物级属性(脆、快、成群;数值平衡后续再调)。 */
    public static DefaultAttributeContainer.Builder createAnubisWraithAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    // ===== GeckoLib =====

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 注意:必须常驻返回动画(见类注释的沉地坑),别加"空闲不播"分支。
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
