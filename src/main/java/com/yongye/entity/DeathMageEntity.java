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
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

// GeckoLib 4.x —— 与 m162/m165/m167 已编过的实体同包(切勿改包路径)。
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * BOSS·死亡法师(m170 Stage1:召唤 + 渲染 + 血条 + 基础动画)。
 *
 * <p>地面 BOSS,近战追击起步(m162 龙 Stage1 同款 AI 组);
 * 自带紫色 BOSS 血条(凋灵同款挂法,与浴火凤凰一致)。
 * 模型自带 cast/cast2/cast3、attack×3、attackmelee×2、shockwave1、death 等 15 条动画,
 * 施法/冲击波技能 Stage2 按 AI 触发再接。
 */
public class DeathMageEntity extends HostileEntity implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** BOSS 血条(紫色)。字段初始化在 super() 之后执行,此时 getType() 已可用。 */
    private final ServerBossBar bossBar = new ServerBossBar(
            this.getType().getName().copy().formatted(Formatting.DARK_PURPLE),
            BossBar.Color.PURPLE, BossBar.Style.PROGRESS);

    /** 血条百分比刷新计数器。 */
    private int barRefreshTicker = 0;

    public DeathMageEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    /** BOSS 基础属性(能打的起点,数值平衡后续再调)。 */
    public static DefaultAttributeContainer.Builder createDeathMageAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 500.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void initGoals() {
        // Stage1 近战追击(施法/冲击波留 Stage2);AI 组照 m162 龙 Stage1。
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    // ===== BOSS 血条(凋灵同款挂法)=====

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && ++this.barRefreshTicker >= 10) {
            this.barRefreshTicker = 0;
            float max = this.getMaxHealth();
            // m187:血量数字嵌入血条名(‖当前/最大)→ 客户端解析显示
            this.bossBar.setName(this.getType().getName().copy().formatted(Formatting.DARK_PURPLE)
                    .append(Text.literal("\u2016" + String.format(java.util.Locale.ROOT, "%.0f", (double) this.getHealth()) + "/" + String.format(java.util.Locale.ROOT, "%.0f", (double) max))));
            this.bossBar.setPercent(max > 0 ? Math.max(0f, Math.min(1f, this.getHealth() / max)) : 0f);
        }
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
