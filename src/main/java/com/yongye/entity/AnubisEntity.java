package com.yongye.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModEntities;

// GeckoLib 4.x —— 与 m162~m173 已在树的实体同包(切勿改包路径)。
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * BOSS·阿努比斯(m174 Stage2:狂怒系统 + 法术 AoE + 恶灵召唤 + 更丰富动画状态机)。
 *
 * <p><b>动画状态机(纯同步数据推断,无 DataTracker):</b><br>
 * 满血且静止 → 坐姿(生成时雕像感);<br>
 * 死亡 → death(thenPlayAndHold 最后帧定格);<br>
 * 狂怒(HP&lt;50%)且静止 → rage 循环;<br>
 * 移动时恒为 run(大型 BOSS 奔跑感压迫);<br>
 * 其余 → idle。<br>
 * Stage3 可用 DataTracker byte 加入 melee1-3/spell1-2 精确逐招动画同步。
 *
 * <p><b>Stage2 新增服务端机制:</b><br>
 * ① 狂怒(HP首降到阈值):速度/攻击属性提升 + AoE 击退+失明 + 血条改红 + 全服播报;<br>
 * ② 法术(每 N tick 若有目标):魔法 AoE 伤害 + 地裂粒子(狂怒后冷却减半);<br>
 * ③ 恶灵召唤(HP<75% 且冷却结束):最多同场 anubisMaxWraiths 只,环绕出生,继承攻击目标。
 *
 * <p><b>待编译验证:</b> {@code bossBar.setColor(BossBar.Color.RED)}(仓库首次调用 setColor,
 * API 存在于 ServerBossBar/Bossbar 标准类,把握高);其余 import 与覆盖点全与
 * m169~m173 已在树的代码逐字一致,静态自检通过。
 */
public class AnubisEntity extends HostileEntity implements GeoEntity {

    // ===== 动画静态常量 =====
    private static final RawAnimation IDLE    = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUN     = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation RAGE    = RawAnimation.begin().thenLoop("rage");
    private static final RawAnimation DEATH   = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation SITTING = RawAnimation.begin().thenLoop("sitting");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** BOSS 血条(蓝条 + 金字;狂怒后改红)。字段初始化在 super() 之后,getType() 已可用(凋灵同款写法)。 */
    private final ServerBossBar bossBar = new ServerBossBar(
            this.getType().getName().copy().formatted(Formatting.GOLD),
            BossBar.Color.BLUE, BossBar.Style.PROGRESS);

    /** 血条百分比刷新计数器。 */
    private int barRefreshTicker = 0;

    // ===== 服务端状态字段(服务端专用,不随实体数据同步到客户端) =====

    /** 是否已触发狂怒(HP<anubisRageHealthThreshold 时一次性触发)。 */
    private boolean rageTriggered = false;

    /**
     * 法术施放冷却(每 tick 递减,归 0 时施法)。
     * 初始给 100t 延迟,让 BOSS 先追一会儿再施法。
     */
    private int spellCooldown = 100;

    /** 恶灵召唤冷却。 */
    private int summonCooldown = 200;

    /** 法术轮换索引(spell1/spell2 交替)。 */
    private int spellIndex = 0;

    public AnubisEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    /** 狂怒后攻击基础值(= BASE_ATTACK × 1.5;与 createAnubisAttributes 保持同源)。 */
    private static final double BASE_ATTACK = 80.0;

    /**
     * 旗舰级 BOSS 属性(m175 大幅拔高:第 10 天起自然降临的顶级世界 BOSS,
     * 明显高于凤凰 650/24 和死亡法师 500/20 一个档位;
     * 出生还会再吃 MobEnhancementHandler 进度缩放 + DynamicScaling 玩家对位,只增不减)。
     * Stage2 狂怒后临时提升速度/攻击。
     */
    public static DefaultAttributeContainer.Builder createAnubisAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 8000.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, BASE_ATTACK)
                .add(EntityAttributes.GENERIC_ARMOR, 20.0)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 10.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initGoals() {
        // Stage1~2 近战追击 + 游荡;技能触发由 tick() 直接驱动,不走额外 Goal。
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    // ===== BOSS 血条(凋灵同款:onStarted/StoppedTrackingBy 增删观众,tick 刷进度) =====

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

    // ===== tick:血条刷新 + Stage2 服务端机制 =====

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        // — 血条百分比(每 10 tick) —
        if (++this.barRefreshTicker >= 10) {
            this.barRefreshTicker = 0;
            float max = this.getMaxHealth();
            // m187:血量数字嵌入血条名(‖当前/最大)→ 客户端解析显示
            this.bossBar.setName(this.getType().getName().copy().formatted(Formatting.GOLD)
                    .append(Text.literal("\u2016" + (long)this.getHealth() + "/" + (long)max)));
            this.bossBar.setPercent(max > 0 ? Math.max(0f, Math.min(1f, this.getHealth() / max)) : 0f);
        }

        if (!this.isAlive()) return;

        YongyeConfig cfg = YongyeConfig.get();

        // — 狂怒触发(一次性) —
        if (!this.rageTriggered
                && this.getHealth() / this.getMaxHealth() < (float) cfg.anubisRageHealthThreshold) {
            this.triggerRage();
        }

        // — 法术冷却 & 施法 —
        if (this.spellCooldown > 0) {
            this.spellCooldown--;
        } else if (this.getTarget() != null) {
            this.castSpell();
            int base = cfg.anubisSpellCooldownTicks;
            // 狂怒后冷却减半,最短 60 tick
            this.spellCooldown = this.rageTriggered ? Math.max(60, base / 2) : base;
        }

        // — 恶灵召唤冷却 & 召唤 —
        if (this.summonCooldown > 0) {
            this.summonCooldown--;
        } else if (this.getHealth() / this.getMaxHealth() < (float) cfg.anubisSummonHealthThreshold) {
            this.summonWraiths();
            this.summonCooldown = cfg.anubisSummonCooldownTicks;
        }
    }

    // ===== Stage2:狂怒 =====

    /**
     * 触发狂怒:速度/攻击提升 + AoE 击退+失明 + 血条改红 + 全服播报。
     * 一次性触发,rageTriggered 标记防重复。
     */
    private void triggerRage() {
        this.rageTriggered = true;

        // 属性提升(基于 m175 新基础值:速度 0.3→0.45,攻击 80→120;
        // setBaseValue 只改基础值,MobEnhancement/DynamicScaling 挂的修饰符不受影响照常叠乘)
        var speedAttr = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.45);
        var atkAttr = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (atkAttr != null) atkAttr.setBaseValue(BASE_ATTACK * 1.5);

        // 血条改红(待编译验证:仓库首次调用 setColor,标准 API)
        this.bossBar.setColor(BossBar.Color.RED);

        // AoE 击退 + 失明
        World world = this.getWorld();
        Box box = Box.of(this.getPos(), 14.0, 8.0, 14.0);
        for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, box, e -> true)) {
            double dx = p.getX() - this.getX();
            double dz = p.getZ() - this.getZ();
            p.takeKnockback(2.0, -dx, -dz);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
            if (p instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
            }
        }

        // 粒子效果
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 3.0, this.getZ(), 40, 2.0, 3.0, 2.0, 0.1);
            sw.spawnParticles(ParticleTypes.FLAME,
                    this.getX(), this.getY() + 2.0, this.getZ(), 20, 2.0, 2.0, 2.0, 0.05);
            // 全服播报
            sw.getServer().getPlayerManager().broadcast(
                    Text.literal("[阿努比斯] 永恒的裁判者陷入狂怒！").formatted(Formatting.RED, Formatting.BOLD),
                    false);
        }
    }

    // ===== Stage2:法术 AoE =====

    /**
     * 施放法术:对附近玩家造成魔法 AoE 伤害 + 地裂粒子。
     * spell1/spell2 轮流切换(spellIndex 每次自增)。
     * 注:当前只有服务端伤害+粒子效果;法术动画(spell1/spell2)需 DataTracker 同步,留 Stage3。
     */
    private void castSpell() {
        YongyeConfig cfg = YongyeConfig.get();
        double radius = cfg.anubisSpellRadius;
        float dmg = (float) cfg.anubisSpellDamage;
        World world = this.getWorld();

        // AoE 伤害
        Box box = Box.of(this.getPos(), radius * 2, radius, radius * 2);
        for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, box,
                e -> e.squaredDistanceTo(this) <= radius * radius)) {
            p.damage(world.getDamageSources().magic(), dmg);
        }

        // 地裂粒子
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.EXPLOSION,
                    this.getX(), this.getY(), this.getZ(), 4, 1.5, 0.3, 1.5, 0.1);
            sw.spawnParticles(ParticleTypes.CRIT,
                    this.getX(), this.getY() + 1.0, this.getZ(), 20, 2.0, 1.0, 2.0, 0.2);
        }

        this.spellIndex = (this.spellIndex + 1) % 2;
    }

    // ===== Stage2:恶灵召唤 =====

    /**
     * 召唤阿努比斯恶灵(上限由 anubisMaxWraiths 控制)。
     * 先统计附近已有恶灵数,补足到上限;新恶灵继承攻击目标。
     */
    private void summonWraiths() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        YongyeConfig cfg = YongyeConfig.get();

        int existing = sw.getEntitiesByClass(AnubisWraithEntity.class,
                Box.of(this.getPos(), 32.0, 16.0, 32.0), e -> true).size();
        int toSpawn = Math.max(0, cfg.anubisMaxWraiths - existing);
        if (toSpawn == 0) return;

        LivingEntity target = this.getTarget();
        for (int i = 0; i < toSpawn; i++) {
            double angle = (Math.PI * 2.0 / toSpawn) * i;
            double dx = Math.cos(angle) * 3.5;
            double dz = Math.sin(angle) * 3.5;
            AnubisWraithEntity wraith = new AnubisWraithEntity(ModEntities.ANUBIS_WRAITH, sw);
            wraith.refreshPositionAndAngles(
                    this.getX() + dx, this.getY(), this.getZ() + dz, this.getYaw(), 0);
            if (target != null) wraith.setTarget(target);
            sw.spawnEntity(wraith);
            sw.spawnParticles(ParticleTypes.PORTAL,
                    this.getX() + dx, this.getY() + 1.0, this.getZ() + dz, 12, 0.3, 0.5, 0.3, 0.05);
        }
    }

    // ===== GeckoLib =====

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            AnubisEntity anubis = state.getAnimatable();

            // 死亡:定格在 death 最后一帧
            if (!anubis.isAlive()) {
                return state.setAndContinue(DEATH);
            }

            float hpRatio = anubis.getHealth() / anubis.getMaxHealth();

            // 移动时恒播 run(大型 BOSS 奔跑感压迫,无论是否狂怒)
            if (state.isMoving()) {
                return state.setAndContinue(RUN);
            }

            // 满血且静止:坐姿(生成后尚未被触发的雕像感)
            if (hpRatio >= 0.99f) {
                return state.setAndContinue(SITTING);
            }

            // 狂怒待机(HP < 50%)
            if (hpRatio < 0.5f) {
                return state.setAndContinue(RAGE);
            }

            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
