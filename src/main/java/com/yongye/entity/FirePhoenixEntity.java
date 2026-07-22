package com.yongye.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
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
 * BOSS·浴火凤凰(m169 Stage1:召唤 + 渲染 + 血条 + 基础动画)。
 *
 * <p>飞行 BOSS,套 m165 龙的飞行路子(FlightMoveControl + BirdNavigation + 无重力);
 * 自带金色 BOSS 血条(原版凋灵同款:被玩家开始/停止追踪时增删观众——despawn/换维度/区块卸载
 * 都会走「停止追踪」,不会留残条)。
 * 模型自带 beam / dive 三段 / firetornado / eggfold(浴火重生)等技能动画,Stage2 按 AI 触发再接。
 */
public class FirePhoenixEntity extends HostileEntity implements GeoEntity {

    /** 常驻扇翅(飞行生物没有站桩 idle,悬停/移动都扇翅;动画文件 flapping 1.5s)。 */
    private static final RawAnimation FLAPPING = RawAnimation.begin().thenLoop("flapping");

    /** 近战出手距离(格)。Stage1 先给常量,要可配再提为配置字段。 */
    private static final double ATTACK_REACH = 8.0;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** BOSS 血条(金色)。字段初始化在 super() 之后执行,此时 getType() 已可用(凋灵同款写法)。 */
    private final ServerBossBar bossBar = new ServerBossBar(
            this.getType().getName().copy().formatted(Formatting.GOLD),
            BossBar.Color.YELLOW, BossBar.Style.PROGRESS);

    /** 血条百分比刷新计数器(不依赖 Entity.age,零新接口)。 */
    private int barRefreshTicker = 0;

    public FirePhoenixEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        // 飞行移动控制(第三参 true = 无重力)+ 显式无重力双保险,同 m165 龙。
        this.moveControl = new FlightMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    /** BOSS 基础属性(能打的起点,数值平衡后续再调)。 */
    public static DefaultAttributeContainer.Builder createFirePhoenixAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 650.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 24.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.9)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    /** 飞行寻路(裸构造,不调可能随版本改名的 setter,同 m165 龙)。 */
    @Override
    protected EntityNavigation createNavigation(World world) {
        return new BirdNavigation(this, world);
    }

    @Override
    protected void initGoals() {
        // 近战追击走飞行导航(3D 追玩家俯冲);出手距离用 DragonAttackGoal(m166 建、m168 已修 canAttack)。
        this.goalSelector.add(1, new DragonAttackGoal(this, 1.0, true, ATTACK_REACH));
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
        // 服务端每 10 tick 刷一次血条百分比(钳制写法同 MobBossHandler)。
        if (!this.getWorld().isClient && ++this.barRefreshTicker >= 10) {
            this.barRefreshTicker = 0;
            float max = this.getMaxHealth();
            // m187:血量数字嵌入血条名(‖当前/最大)→ 客户端解析显示
            this.bossBar.setName(this.getType().getName().copy().formatted(Formatting.GOLD)
                    .append(Text.literal("\u2016" + (long)this.getHealth() + "/" + (long)max)));
            this.bossBar.setPercent(max > 0 ? Math.max(0f, Math.min(1f, this.getHealth() / max)) : 0f);
        }
    }

    // ===== GeckoLib =====

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 5,
                state -> state.setAndContinue(FLAPPING)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
