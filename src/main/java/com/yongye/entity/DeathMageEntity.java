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
import net.minecraft.entity.effect.StatusEffectInstance;
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
                .add(EntityAttributes.GENERIC_MAX_HEALTH, com.yongye.YongyeConfig.get().deathMageBaseHealth) // m263:出场血量可配(改配置需重启生效)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.1); // m267:免跳跨 1 格坎
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

    /** m263:出场演出只在本次加载的第一个 tick 播一次(age 不持久化,区块重载重演=有意)。 */
    private boolean entrancePlayed = false;

    // ===== m268 技能状态 =====
    private int strikeCooldown = 100;                          // 魂火锁定
    private int novaCooldown = 140;                            // 亡者音爆
    private int blinkCooldown = 0;                             // 虚影闪现
    private net.minecraft.util.math.BlockPos pendingStrike;    // 魂火落点(延迟爆燃)
    private int pendingStrikeTicks = 0;

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && !this.entrancePlayed) {
            this.entrancePlayed = true;
            if (this.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)
                com.yongye.system.BossEntranceFx.play(sw, this, this.getType().getName(), Formatting.DARK_PURPLE);
        }
        if (!this.getWorld().isClient && ++this.barRefreshTicker >= 10) {
            this.barRefreshTicker = 0;
            float max = this.getMaxHealth();
            // m187:血量数字嵌入血条名(‖当前/最大)→ 客户端解析显示
            this.bossBar.setName(this.getType().getName().copy().formatted(Formatting.DARK_PURPLE)
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

        // —— 魂火锁定:落点结算(标记 25t 后爆燃,给走位窗口) ——
        if (this.pendingStrikeTicks > 0 && this.pendingStrike != null) {
            double px = this.pendingStrike.getX() + 0.5, py = this.pendingStrike.getY() + 0.1, pz = this.pendingStrike.getZ() + 0.5;
            double a = this.pendingStrikeTicks * 0.6;
            for (int k = 0; k < 4; k++) {
                double ang = a + k * (Math.PI / 2);
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                        px + Math.cos(ang) * 1.6, py + 0.2, pz + Math.sin(ang) * 1.6, 1, 0.05, 0.05, 0.05, 0.0);
            }
            if (--this.pendingStrikeTicks == 0) {
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL, px, py + 0.5, pz, 40, 1.2, 0.8, 1.2, 0.05);
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION, px, py + 0.5, pz, 2, 0.4, 0.2, 0.4, 0.0);
                sw.playSound(null, px, py, pz, net.minecraft.sound.SoundEvents.ENTITY_WITHER_SHOOT,
                        net.minecraft.sound.SoundCategory.HOSTILE, 1.5f, 0.7f);
                for (net.minecraft.entity.player.PlayerEntity pl : sw.getEntitiesByClass(
                        net.minecraft.entity.player.PlayerEntity.class,
                        net.minecraft.util.math.Box.of(new net.minecraft.util.math.Vec3d(px, py, pz), 7.0, 5.0, 7.0),
                        e -> e.squaredDistanceTo(px, py, pz) <= 12.25)) {
                    pl.damage(sw.getDamageSources().magic(), (float) cfg.mageStrikeDamage);
                    pl.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.WITHER, 100, 1));
                }
                this.pendingStrike = null;
            }
        }

        // —— 魂火锁定:施放 ——
        if (this.strikeCooldown > 0) this.strikeCooldown--;
        else if (t != null && this.distanceTo(t) <= 20.0 && this.pendingStrike == null) {
            this.pendingStrike = t.getBlockPos();
            this.pendingStrikeTicks = 25;
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON,
                    net.minecraft.sound.SoundCategory.HOSTILE, 1.4f, 1.3f);
            this.strikeCooldown = cfg.mageStrikeCooldownTicks;
        }

        // —— 亡者音爆:近身范围击退 ——
        if (this.novaCooldown > 0) this.novaCooldown--;
        else if (t != null && this.distanceTo(t) <= 7.0) {
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.SONIC_BOOM,
                    this.getX(), this.getY() + 1.2, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                    net.minecraft.sound.SoundCategory.HOSTILE, 1.6f, 1.0f);
            for (net.minecraft.entity.player.PlayerEntity pl : sw.getEntitiesByClass(
                    net.minecraft.entity.player.PlayerEntity.class,
                    net.minecraft.util.math.Box.of(this.getPos(), 16.0, 8.0, 16.0),
                    e -> e.squaredDistanceTo(this) <= 64.0)) {
                pl.damage(sw.getDamageSources().magic(), (float) cfg.mageNovaDamage);
                pl.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOWNESS, 80, 1));
                double dx = pl.getX() - this.getX(), dz = pl.getZ() - this.getZ();
                pl.takeKnockback(1.8, -dx, -dz);
                if (pl instanceof ServerPlayerEntity spx)
                    spx.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(pl));
            }
            this.novaCooldown = cfg.mageNovaCooldownTicks;
        }

        // —— 虚影闪现:被贴脸挨打就闪到目标侧后方(法师不跟你贴身互殴) ——
        if (this.blinkCooldown > 0) this.blinkCooldown--;
        else if (t != null && this.hurtTime > 0 && this.distanceTo(t) <= 5.0) {
            double fx = this.getX(), fy = this.getY(), fz = this.getZ();
            double ang = this.getRandom().nextDouble() * Math.PI * 2;
            double nx = t.getX() + Math.cos(ang) * 7.0, nz = t.getZ() + Math.sin(ang) * 7.0;
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, fx, fy + 1.0, fz, 30, 0.4, 1.0, 0.4, 0.1);
            this.refreshPositionAndAngles(nx, t.getY(), nz, this.getYaw(), 0);
            this.getNavigation().stop();
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, nx, t.getY() + 1.0, nz, 30, 0.4, 1.0, 0.4, 0.1);
            sw.playSound(null, nx, t.getY(), nz, net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                    net.minecraft.sound.SoundCategory.HOSTILE, 1.4f, 0.8f);
            this.blinkCooldown = cfg.mageBlinkCooldownTicks;
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
