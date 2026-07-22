package com.yongye.entity;

import com.yongye.YongyeConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 「肝帝玩家」(m223,召唤师·癫狂的召唤物):玩家模型的友军 NPC。
 * 皮肤占位:assets/yongye/textures/entity/gandi.png(64×64 标准皮肤布局),
 * 作者发来正式皮肤后【直接覆盖该文件】即可,零代码改动。
 * AI:近战攻击敌对怪(ActiveTargetGoal 只锁 HostileEntity,不打玩家/友军);
 * 离主人 >12 格时优先跑回主人身边;寿命到点自散(灵魂粒子)。
 * 属性从配置读(gandiHealth/gandiAttack/gandiSpeed),注册期读取——改配置后需重启生效。
 */
public class GanDiEntity extends PathAwareEntity {

    /** m224:肝帝变体(0岛风/1晚安/2不爱肝/3迷人)。DataTracker 同步给客户端选皮肤。
     *  待编译验证:initDataTracker(DataTracker.Builder) 签名与 TrackedDataHandlerRegistry.INTEGER(1.20.5+ 标准写法)。 */
    private static final TrackedData<Integer> VARIANT =
            DataTracker.registerData(GanDiEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final String[] VARIANT_NAMES = {"岛风", "晚安", "不爱肝", "迷人"};

    private UUID owner;
    private int lifeTicks;

    public GanDiEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createGanDiAttributes() {
        YongyeConfig cfg = YongyeConfig.get();
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, cfg.gandiHealth)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, cfg.gandiAttack)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, cfg.gandiSpeed)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(VARIANT, 0);
    }

    public int getVariant() { return this.dataTracker.get(VARIANT); }
    public void setVariant(int v) { this.dataTracker.set(VARIANT, Math.max(0, Math.min(3, v))); }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, true));
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        this.setPersistent();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        // 寿命:到点化作灵魂散去
        if (++lifeTicks >= Math.max(100, YongyeConfig.get().gandiLifeSec * 20)) {
            sw.spawnParticles(ParticleTypes.SOUL, getX(), getBodyY(0.5), getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            this.discard();
            return;
        }
        // 分工光环(m224,每 3 秒一轮,作用于主人的全部铁傀儡):
        // 岛风·圆梦筑城=恢复+抗性 | 晚安·极限生电=直接修复+给主人缩大招CD | 不爱肝·百万方工程=生命上限+强抗 | 迷人·蒸汽武装=力量+速度
        if (lifeTicks % 60 == 0 && owner != null) {
            java.util.List<IronGolemEntity> golems = com.yongye.system.SummonerHandler.golemsOf(owner);
            for (IronGolemEntity g : golems) {
                switch (getVariant()) {
                    case 0 -> { g.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, false, false));
                                g.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0, true, false, false)); }
                    case 1 -> g.heal(4.0f);
                    case 2 -> { g.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, 100, 1, true, false, false));
                                g.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 1, true, false, false)); }
                    case 3 -> { g.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 100, 1, true, false, false));
                                g.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0, true, false, false)); }
                }
            }
            if (getVariant() == 1) com.yongye.system.ClassUltimateManager.reduceCooldown(owner, 40); // 晚安:每 3 秒帮主人缩 2 秒 CD
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER, getX(), getBodyY(0.8), getZ(), 3, 0.3, 0.4, 0.3, 0.0);
        }

        // 跟随主人:无仇恨且离主人太远时跑回去(每 10 tick 判一次省性能)
        if (lifeTicks % 10 == 0 && this.getTarget() == null && owner != null) {
            PlayerEntity o = sw.getPlayerByUuid(owner);
            if (o != null && this.squaredDistanceTo(o) > 144.0) {
                this.getNavigation().startMovingTo(o, 1.15);
            }
        }
    }
}
