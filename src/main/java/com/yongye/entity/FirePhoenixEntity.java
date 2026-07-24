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
                .add(EntityAttributes.GENERIC_MAX_HEALTH, com.yongye.YongyeConfig.get().phoenixBaseHealth) // m263:出场血量可配(改配置需重启生效)
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

    /** m263:出场演出只在本次加载的第一个 tick 播一次(age 不持久化,区块重载重演=有意)。 */
    private boolean entrancePlayed = false;

    // ===== m268 技能状态 =====
    private int beamCooldown = 100;      // 烈焰吐息(先追一会儿再开火)
    private int tornadoCooldown = 200;   // 火焰龙卷
    private boolean rebirthUsed = false; // 浴火重生只来一次
    private int rebirthTicks = 0;        // >0 = 正蜷在烈焰之卵里

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && !this.entrancePlayed) {
            this.entrancePlayed = true;
            if (this.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)
                com.yongye.system.BossEntranceFx.play(sw, this, this.getType().getName(), Formatting.GOLD);
        }
        // 服务端每 10 tick 刷一次血条百分比(钳制写法同 MobBossHandler)。
        if (!this.getWorld().isClient && ++this.barRefreshTicker >= 10) {
            this.barRefreshTicker = 0;
            float max = this.getMaxHealth();
            // m187:血量数字嵌入血条名(‖当前/最大)→ 客户端解析显示
            this.bossBar.setName(this.getType().getName().copy().formatted(Formatting.GOLD)
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

        // —— 浴火重生:蛋中(无敌收拢) ——
        if (this.rebirthTicks > 0) {
            this.getNavigation().stop();
            this.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
            // 火焰螺旋向蛋心收拢
            double prog = 1.0 - this.rebirthTicks / 100.0;
            double r = 4.0 * (1.0 - prog) + 0.5;
            double ang = this.rebirthTicks * 0.5;
            for (int k = 0; k < 3; k++) {
                double a = ang + k * (Math.PI * 2 / 3);
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                        this.getX() + Math.cos(a) * r, this.getY() + 1.5, this.getZ() + Math.sin(a) * r,
                        2, 0.05, 0.05, 0.05, 0.0);
            }
            if (--this.rebirthTicks == 0) {
                // 破壳:回血 + 爆炎 AoE + 点燃
                this.setInvulnerable(false);
                this.heal((float) (this.getMaxHealth() * cfg.phoenixRebirthHealRatio));
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,
                        this.getX(), this.getY() + 1.5, this.getZ(), 2, 0.5, 0.5, 0.5, 0.0);
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA,
                        this.getX(), this.getY() + 1.0, this.getZ(), 60, 3.0, 1.5, 3.0, 0.2);
                sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sound.SoundEvents.ENTITY_BLAZE_SHOOT,
                        net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 0.6f);
                net.minecraft.util.math.Box box = net.minecraft.util.math.Box.of(this.getPos(), 20.0, 10.0, 20.0);
                for (net.minecraft.entity.player.PlayerEntity pl : sw.getEntitiesByClass(
                        net.minecraft.entity.player.PlayerEntity.class, box,
                        e -> e.squaredDistanceTo(this) <= 100.0)) {
                    pl.damage(sw.getDamageSources().magic(), (float) cfg.phoenixTornadoDamage * 1.6f);
                    pl.setFireTicks(100);
                    double dx = pl.getX() - this.getX(), dz = pl.getZ() - this.getZ();
                    pl.takeKnockback(1.6, -dx, -dz);
                    if (pl instanceof net.minecraft.server.network.ServerPlayerEntity spx)
                        spx.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(pl));
                }
                sw.getServer().getPlayerManager().broadcast(net.minecraft.text.Text.literal(
                        "[浴火凤凰] 烈焰之卵碎裂——它浴火重生了！").formatted(Formatting.GOLD, Formatting.BOLD), false);
            }
            return; // 蛋中不放别的技能
        }

        // —— 浴火重生:触发 ——
        if (!this.rebirthUsed && this.getHealth() < this.getMaxHealth() * (float) cfg.phoenixRebirthThreshold) {
            this.rebirthUsed = true;
            this.rebirthTicks = 100;
            this.setInvulnerable(true);
            this.setTarget(null);
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON,
                    net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 0.5f);
            sw.getServer().getPlayerManager().broadcast(net.minecraft.text.Text.literal(
                    "[浴火凤凰] 它蜷入了烈焰之卵……趁现在也伤不到它分毫！").formatted(Formatting.RED, Formatting.BOLD), false);
            return;
        }

        net.minecraft.entity.LivingEntity t = this.getTarget();

        // —— 烈焰吐息:直线火舌 ——
        if (this.beamCooldown > 0) this.beamCooldown--;
        else if (t != null && this.distanceTo(t) <= 24.0) {
            net.minecraft.util.math.Vec3d from = this.getPos().add(0, this.getHeight() * 0.6, 0);
            net.minecraft.util.math.Vec3d dir = t.getPos().add(0, t.getHeight() * 0.5, 0).subtract(from).normalize();
            java.util.Set<net.minecraft.entity.player.PlayerEntity> hit = new java.util.HashSet<>();
            for (int i = 1; i <= 32; i++) {
                net.minecraft.util.math.Vec3d pt = from.add(dir.multiply(i * 0.75));
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME, pt.x, pt.y, pt.z, 3, 0.15, 0.15, 0.15, 0.01);
                if (i % 4 == 0) sw.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA, pt.x, pt.y, pt.z, 1, 0.1, 0.1, 0.1, 0.0);
                for (net.minecraft.entity.player.PlayerEntity pl : sw.getEntitiesByClass(
                        net.minecraft.entity.player.PlayerEntity.class,
                        net.minecraft.util.math.Box.of(pt, 3.2, 3.2, 3.2),
                        e -> e.getPos().add(0, e.getHeight() * 0.5, 0).squaredDistanceTo(pt) <= 2.6)) {
                    if (hit.add(pl)) {
                        pl.damage(sw.getDamageSources().magic(), (float) cfg.phoenixBeamDamage);
                        pl.setFireTicks(80);
                    }
                }
            }
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_BLAZE_SHOOT,
                    net.minecraft.sound.SoundCategory.HOSTILE, 1.6f, 0.8f);
            this.beamCooldown = cfg.phoenixBeamCooldownTicks;
        }

        // —— 火焰龙卷:目标脚下起火旋风 ——
        if (this.tornadoCooldown > 0) this.tornadoCooldown--;
        else if (t != null && this.distanceTo(t) <= 20.0) {
            net.minecraft.util.math.Vec3d base = t.getPos();
            for (int h = 0; h < 8; h++) {
                double rr = 1.8 - h * 0.12;
                for (int k = 0; k < 8; k++) {
                    double a = k * (Math.PI / 4) + h * 0.5;
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                            base.x + Math.cos(a) * rr, base.y + h * 0.6, base.z + Math.sin(a) * rr,
                            1, 0.05, 0.05, 0.05, 0.0);
                }
            }
            sw.playSound(null, base.x, base.y, base.z,
                    net.minecraft.sound.SoundEvents.ITEM_FIRECHARGE_USE,
                    net.minecraft.sound.SoundCategory.HOSTILE, 1.6f, 0.7f);
            for (net.minecraft.entity.player.PlayerEntity pl : sw.getEntitiesByClass(
                    net.minecraft.entity.player.PlayerEntity.class,
                    net.minecraft.util.math.Box.of(base, 6.0, 6.0, 6.0),
                    e -> e.squaredDistanceTo(t.getX(), t.getY(), t.getZ()) <= 9.0)) {
                pl.damage(sw.getDamageSources().magic(), (float) cfg.phoenixTornadoDamage);
                pl.setFireTicks(80);
                pl.addVelocity(0.0, 0.9, 0.0);
                if (pl instanceof net.minecraft.server.network.ServerPlayerEntity spx)
                    spx.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(pl));
            }
            this.tornadoCooldown = cfg.phoenixTornadoCooldownTicks;
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
