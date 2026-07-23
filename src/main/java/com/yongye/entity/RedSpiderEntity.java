package com.yongye.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

// GeckoLib 4.x —— 与 m162/m164/m165 已编过的实体同包(切勿改包路径)。
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * BOSS·红蜘蛛(GeckoLib 渲染基岩模型 + 29 条动画)。
 * 扩展原版 SpiderEntity → 白嫖爬墙 + 蜘蛛 AI;BOSS 级属性。
 * 动画:idle→waiting,移动→walking(模型还含 stab/swipe/roar/smash 等攻击动画,后续按 AI 触发再接)。
 */
public class RedSpiderEntity extends SpiderEntity implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("waiting");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walking");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** BOSS 血条(m180 补:红蜘蛛是 BOSS 却一直没血条;凋灵同款挂法,与凤凰/阿努比斯逐字一致,已编译通过)。 */
    private final ServerBossBar bossBar = new ServerBossBar(
            this.getType().getName().copy().formatted(Formatting.RED),
            BossBar.Color.RED, BossBar.Style.PROGRESS);

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
    private int webCooldown = 120;     // 蛛网陷阱
    private int pounceCooldown = 80;   // 猛扑
    private boolean pounceAir = false; // 扑击滞空中(落地结算)
    private int pounceGrace = 0;       // 起跳宽限(防止起跳当帧就判落地)
    private boolean broodUsed = false; // 蛛群咆哮只来一次

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && !this.entrancePlayed) {
            this.entrancePlayed = true;
            if (this.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)
                com.yongye.system.BossEntranceFx.play(sw, this, this.getType().getName(), Formatting.RED);
        }
        if (!this.getWorld().isClient && ++this.barRefreshTicker >= 10) {
            this.barRefreshTicker = 0;
            float max = this.getMaxHealth();
            // m187:血量数字嵌入血条名(‖当前/最大)→ 客户端解析显示
            this.bossBar.setName(this.getType().getName().copy().formatted(Formatting.RED)
                    .append(Text.literal("\u2016" + String.format(java.util.Locale.ROOT, "%.0f", (double) this.getHealth()) + "/" + String.format(java.util.Locale.ROOT, "%.0f", (double) max))));
            this.bossBar.setPercent(max > 0 ? Math.max(0f, Math.min(1f, this.getHealth() / max)) : 0f);
        }
        if (!this.getWorld().isClient && this.isAlive()) BossNavAssist.tick(this); // m267 防转圈
        if (!this.getWorld().isClient && this.isAlive()) this.tickSkills();          // m268 技能
    }

    // ===== m268:技能(全服务端;毒液蜘蛛召唤复用在树实体) =====

    private void tickSkills() {
        if (!(this.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return;
        com.yongye.YongyeConfig cfg = com.yongye.YongyeConfig.get();
        net.minecraft.entity.LivingEntity t = this.getTarget();

        // —— 猛扑:落地结算 ——
        if (this.pounceAir) {
            if (this.pounceGrace > 0) this.pounceGrace--;
            else if (this.isOnGround()) {
                this.pounceAir = false;
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
                        this.getX(), this.getY() + 0.3, this.getZ(), 30, 1.6, 0.2, 1.6, 0.05);
                sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sound.SoundEvents.ENTITY_RAVAGER_STEP,
                        net.minecraft.sound.SoundCategory.HOSTILE, 1.6f, 0.7f);
                for (net.minecraft.entity.player.PlayerEntity pl : sw.getEntitiesByClass(
                        net.minecraft.entity.player.PlayerEntity.class,
                        net.minecraft.util.math.Box.of(this.getPos(), 7.0, 4.0, 7.0),
                        e -> e.squaredDistanceTo(this) <= 12.25)) {
                    pl.damage(sw.getDamageSources().magic(), (float) cfg.spiderPounceDamage);
                    double dx = pl.getX() - this.getX(), dz = pl.getZ() - this.getZ();
                    pl.takeKnockback(1.2, -dx, -dz);
                    if (pl instanceof ServerPlayerEntity spx)
                        spx.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(pl));
                }
            }
        }

        // —— 猛扑:起跳(中距离一记飞扑,蜘蛛的招牌压迫) ——
        if (this.pounceCooldown > 0) this.pounceCooldown--;
        else if (!this.pounceAir && t != null && this.isOnGround()) {
            double d = this.distanceTo(t);
            if (d >= 4.0 && d <= 14.0) {
                net.minecraft.util.math.Vec3d dir = t.getPos().subtract(this.getPos()).normalize();
                this.addVelocity(dir.x * 1.5, 0.62, dir.z * 1.5);
                this.velocityModified = true;
                this.pounceAir = true;
                this.pounceGrace = 8;
                sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sound.SoundEvents.ENTITY_SPIDER_AMBIENT,
                        net.minecraft.sound.SoundCategory.HOSTILE, 1.6f, 0.5f);
                this.pounceCooldown = cfg.spiderPounceCooldownTicks;
            }
        }

        // —— 蛛网陷阱:目标脚下铺网+中毒 ——
        if (this.webCooldown > 0) this.webCooldown--;
        else if (t != null && this.distanceTo(t) <= 14.0) {
            net.minecraft.util.math.BlockPos base = t.getBlockPos();
            net.minecraft.util.math.BlockPos[] spots = {
                    base, base.north(), base.south(), base.east(), base.west() };
            int placed = 0;
            for (net.minecraft.util.math.BlockPos bp : spots) {
                if (sw.getBlockState(bp).isAir()) {
                    sw.setBlockState(bp, net.minecraft.block.Blocks.COBWEB.getDefaultState());
                    placed++;
                }
            }
            if (placed > 0) {
                t.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.POISON, 80, 0));
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.ITEM_SLIME,
                        base.getX() + 0.5, base.getY() + 0.8, base.getZ() + 0.5, 20, 1.0, 0.5, 1.0, 0.05);
                sw.playSound(null, base.getX(), base.getY(), base.getZ(),
                        net.minecraft.sound.SoundEvents.ENTITY_SPIDER_STEP,
                        net.minecraft.sound.SoundCategory.HOSTILE, 1.4f, 0.6f);
                this.webCooldown = cfg.spiderWebCooldownTicks;
            }
        }

        // —— 蛛群咆哮(一次性):怒吼 + 召唤毒液蜘蛛围攻 + 恐惧 ——
        if (!this.broodUsed && this.getHealth() < this.getMaxHealth() * (float) cfg.spiderBroodHealthThreshold) {
            this.broodUsed = true;
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_RAVAGER_ROAR,
                    net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 0.8f);
            int n = Math.max(1, cfg.spiderBroodCount);
            for (int i = 0; i < n; i++) {
                double ang = (Math.PI * 2.0 / n) * i;
                double dx = Math.cos(ang) * 3.0, dz = Math.sin(ang) * 3.0;
                VenomSpiderEntity vs = new VenomSpiderEntity(
                        com.yongye.registry.ModEntities.VENOM_SPIDER, sw);
                vs.refreshPositionAndAngles(this.getX() + dx, this.getY(), this.getZ() + dz, this.getYaw(), 0);
                if (t != null) vs.setTarget(t);
                sw.spawnEntity(vs);
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                        this.getX() + dx, this.getY() + 0.5, this.getZ() + dz, 10, 0.3, 0.4, 0.3, 0.05);
            }
            for (net.minecraft.entity.player.PlayerEntity pl : sw.getEntitiesByClass(
                    net.minecraft.entity.player.PlayerEntity.class,
                    net.minecraft.util.math.Box.of(this.getPos(), 20.0, 8.0, 20.0),
                    e -> e.squaredDistanceTo(this) <= 100.0)) {
                pl.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SLOWNESS, 60, 0));
                pl.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.DARKNESS, 60, 0));
            }
            sw.getServer().getPlayerManager().broadcast(net.minecraft.text.Text.literal(
                    "[红蜘蛛] 蛛后发出刺耳的咆哮——蛛群从阴影中涌出！").formatted(Formatting.RED, Formatting.BOLD), false);
        }
    }

    /** m268:毒刺——近战命中附带中毒 II(蜘蛛味)。 */
    @Override
    public boolean tryAttack(net.minecraft.entity.Entity target) {
        boolean ok = super.tryAttack(target);
        if (ok && target instanceof net.minecraft.entity.LivingEntity le) {
            le.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.POISON, 80, 1));
        }
        return ok;
    }

    public RedSpiderEntity(EntityType<? extends SpiderEntity> type, World world) {
        super(type, world);
    }

    /** BOSS 级属性(在原版蜘蛛基础上大幅拔高)。 */
    public static DefaultAttributeContainer.Builder createRedSpiderAttributes() {
        return SpiderEntity.createSpiderAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, com.yongye.YongyeConfig.get().redSpiderBaseHealth) // m263:出场血量可配(改配置需重启生效)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 18.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.34)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.6); // m267:宽 3.0 巨蛛直接跨 1 格坎
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
