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

    /** BOSS 血条(m179 补:此前自建龙一直没有血条;凋灵同款挂法,与凤凰/阿努比斯逐字一致,已编译通过)。 */
    private final ServerBossBar bossBar = new ServerBossBar(
            this.getType().getName().copy().formatted(Formatting.LIGHT_PURPLE),
            BossBar.Color.PURPLE, BossBar.Style.PROGRESS);

    /** 血条百分比刷新计数器。 */
    private int barRefreshTicker = 0;

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

    /** m263:出场演出只在本次加载的第一个 tick 播一次(age 不持久化,区块重载重演=有意)。 */
    private boolean entrancePlayed = false;

    // ===== m268 技能状态 =====
    private int breathCooldown = 100;    // 龙息射线
    private int diveCooldown = 160;      // 俯冲冲撞
    private int diveTicks = 0;           // >0 = 俯冲中(命中或超时结算)
    private boolean gravityUsed = false; // 重力撕裂只来一次

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && !this.entrancePlayed) {
            this.entrancePlayed = true;
            if (this.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)
                com.yongye.system.BossEntranceFx.play(sw, this, this.getType().getName(), Formatting.LIGHT_PURPLE);
        }
        if (!this.getWorld().isClient && ++this.barRefreshTicker >= 10) {
            this.barRefreshTicker = 0;
            float max = this.getMaxHealth();
            // m187:血量数字嵌入血条名(‖当前/最大)→ 客户端解析显示
            this.bossBar.setName(this.getType().getName().copy().formatted(Formatting.LIGHT_PURPLE)
                    .append(Text.literal("\u2016" + String.format(java.util.Locale.ROOT, "%.0f", (double) this.getHealth()) + "/" + String.format(java.util.Locale.ROOT, "%.0f", (double) max) + com.yongye.system.BossGuardHandler.barSuffix(this))));  // m304 格挡段
            this.bossBar.setPercent(max > 0 ? Math.max(0f, Math.min(1f, this.getHealth() / max)) : 0f);
        }
        if (!this.getWorld().isClient && this.isAlive()) BossNavAssist.tick(this); // m267 防转圈
        if (!this.getWorld().isClient && this.isAlive()) this.tickSkills();          // m268 技能
    }

    // ===== m268:技能(全服务端:粒子+音效+伤害,零新实体) =====

    private void tickSkills() {
        if (!(this.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return;
        com.yongye.YongyeConfig cfg = com.yongye.YongyeConfig.get();
        net.minecraft.entity.LivingEntity t = this.getTarget();

        // —— 俯冲冲撞:冲刺中(靠近即撞,超时收招) ——
        if (this.diveTicks > 0) {
            this.diveTicks--;
            if (t != null) {
                // 持续朝目标压过去(FlightMoveControl 高速档)
                this.getMoveControl().moveTo(t.getX(), t.getY(), t.getZ(), 3.0);
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.DRAGON_BREATH,
                        this.getX(), this.getY() + 1.5, this.getZ(), 4, 1.0, 1.0, 1.0, 0.02);
                if (this.distanceTo(t) <= 5.0) {
                    this.diveTicks = 0;
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,
                            t.getX(), t.getY() + 0.5, t.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                    sw.playSound(null, t.getX(), t.getY(), t.getZ(),
                            net.minecraft.sound.SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE,
                            net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 0.8f);
                    for (net.minecraft.entity.player.PlayerEntity pl : sw.getEntitiesByClass(
                            net.minecraft.entity.player.PlayerEntity.class,
                            net.minecraft.util.math.Box.of(t.getPos(), 10.0, 6.0, 10.0),
                            e -> e.squaredDistanceTo(t.getX(), t.getY(), t.getZ()) <= 25.0)) {
                        pl.damage(sw.getDamageSources().magic(), (float) cfg.toroDiveDamage);
                        double dx = pl.getX() - this.getX(), dz = pl.getZ() - this.getZ();
                        pl.takeKnockback(2.0, -dx, -dz);
                        if (pl instanceof ServerPlayerEntity spx)
                            spx.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(pl));
                    }
                }
            } else this.diveTicks = 0;
        }

        // —— 俯冲冲撞:起手 ——
        if (this.diveCooldown > 0) this.diveCooldown--;
        else if (this.diveTicks == 0 && t != null) {
            double d = this.distanceTo(t);
            if (d >= 6.0 && d <= 30.0) {
                this.diveTicks = 40;
                sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                        net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 1.1f);
                this.diveCooldown = cfg.toroDiveCooldownTicks;
            }
        }

        // —— 龙息射线:直线弹幕 ——
        if (this.breathCooldown > 0) this.breathCooldown--;
        else if (this.diveTicks == 0 && t != null && this.distanceTo(t) <= 28.0) {
            net.minecraft.util.math.Vec3d from = this.getPos().add(0, this.getHeight() * 0.7, 0);
            net.minecraft.util.math.Vec3d dir = t.getPos().add(0, t.getHeight() * 0.5, 0).subtract(from).normalize();
            java.util.Set<net.minecraft.entity.player.PlayerEntity> hit = new java.util.HashSet<>();
            for (int i = 1; i <= 36; i++) {
                net.minecraft.util.math.Vec3d pt = from.add(dir.multiply(i * 0.8));
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.DRAGON_BREATH, pt.x, pt.y, pt.z, 3, 0.2, 0.2, 0.2, 0.01);
                for (net.minecraft.entity.player.PlayerEntity pl : sw.getEntitiesByClass(
                        net.minecraft.entity.player.PlayerEntity.class,
                        net.minecraft.util.math.Box.of(pt, 3.6, 3.6, 3.6),
                        e -> e.getPos().add(0, e.getHeight() * 0.5, 0).squaredDistanceTo(pt) <= 3.3)) {
                    if (hit.add(pl)) {
                        pl.damage(sw.getDamageSources().magic(), (float) cfg.toroBreathDamage);
                        pl.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.SLOWNESS, 60, 1));
                    }
                }
            }
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_SHOOT,
                    net.minecraft.sound.SoundCategory.HOSTILE, 1.8f, 0.9f);
            this.breathCooldown = cfg.toroBreathCooldownTicks;
        }

        // —— 重力撕裂(一次性):龙威掀翻重力,范围玩家被抛上天 ——
        if (!this.gravityUsed && this.getHealth() < this.getMaxHealth() * (float) cfg.toroGravityHealthThreshold) {
            this.gravityUsed = true;
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                    net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 0.5f);
            for (net.minecraft.entity.player.PlayerEntity pl : sw.getEntitiesByClass(
                    net.minecraft.entity.player.PlayerEntity.class,
                    net.minecraft.util.math.Box.of(this.getPos(), 48.0, 24.0, 48.0),
                    e -> e.squaredDistanceTo(this) <= 576.0)) {
                pl.damage(sw.getDamageSources().magic(), 20.0f);
                pl.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.LEVITATION, 60, 2, false, false, true));
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.DRAGON_BREATH,
                        pl.getX(), pl.getY() + 0.5, pl.getZ(), 30, 0.6, 1.0, 0.6, 0.05);
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                        pl.getX(), pl.getY() + 1.0, pl.getZ(), 20, 0.4, 0.8, 0.4, 0.1);
            }
            sw.getServer().getPlayerManager().broadcast(net.minecraft.text.Text.literal(
                    "[末影龙] 龙威撕裂了重力——大地留不住你们！").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        }
    }

    public ToroEnderDragonEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        // 飞行移动控制(第三参 true = 无重力);并显式置无重力,确保不会坠落。
        this.moveControl = new FlightMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    /** BOSS 基础属性(数值先给个能打的起点,后续平衡再调)。 */
    public static DefaultAttributeContainer.Builder createDragonAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, com.yongye.YongyeConfig.get().toroDragonBaseHealth) // m263:出场血量可配(改配置需重启生效)
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
        // 近战追击走飞行导航,在 3D 空间里追玩家并俯冲攻击;攻击距离用 DragonAttackGoal 拉远(可配)。
        this.goalSelector.add(1, new DragonAttackGoal(this, 1.0, true,
                com.yongye.YongyeConfig.get().dragonAttackReach));
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
