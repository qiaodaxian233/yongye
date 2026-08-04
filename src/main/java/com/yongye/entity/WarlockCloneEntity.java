package com.yongye.entity;

import com.yongye.YongyeConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 术士·暗影分身(m262)——作者:「法师小技能改成召唤两个分身,50% 血量、100% 攻击」。
 * 结构照旧版肝帝实体极简化而来(该实体已随 m453 召唤师移除;友军近战锁敌对,无台词无变体):
 *  - 属性在召唤时按主人快照:血量基值=主人最大生命×minorWarlockCloneHpRatio、攻击=主人攻击×AtkRatio
 *    (setBaseValue 直写基值,与肝帝的 addFlat 成长互不相干,分身是一次性快照);
 *  - 寿命 minorWarlockCloneLifeTicks(默认 30 秒)到点魂火散场;主人的仇恨目标出生即继承;
 *  - 渲染=玩家模型+程序化暗紫剪影皮肤(warlock_clone.png,紫眼胸纹,一眼「影分身」)。
 */
public class WarlockCloneEntity extends PathAwareEntity {

    private UUID owner;
    private int lifeTicks;

    /** m300:击杀归属用。 */
    public UUID getOwner() {
        return owner;
    }

    public WarlockCloneEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    /** 注册用默认属性(实际数值在召唤时按主人快照覆盖)。 */
    public static DefaultAttributeContainer.Builder createCloneAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.25, true));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, true));
    }

    /** 召唤时调用:按主人快照拉属性(50% 血 / 100% 攻,均可配),满血登场。 */
    public void snapshotFrom(ServerPlayerEntity ownerPlayer) {
        YongyeConfig cfg = YongyeConfig.get();
        this.owner = ownerPlayer.getUuid();
        this.setPersistent();
        EntityAttributeInstance hp = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null) hp.setBaseValue(Math.max(1.0,
                ownerPlayer.getMaxHealth() * Math.max(0.05, cfg.minorWarlockCloneHpRatio)));
        EntityAttributeInstance atk = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(Math.max(1.0,
                ownerPlayer.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                        * Math.max(0.05, cfg.minorWarlockCloneAtkRatio)));
        this.setHealth(this.getMaxHealth());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        int lifeMax = Math.max(40, YongyeConfig.get().minorWarlockCloneLifeTicks);
        if (++lifeTicks >= lifeMax) {
            sw.spawnParticles(ParticleTypes.SOUL, this.getX(), this.getBodyY(0.6), this.getZ(),
                    14, 0.3, 0.5, 0.3, 0.05);
            this.discard();
            return;
        }
        // 无目标时跟着主人走(不追太远,分身贴身护卫感)
        if (lifeTicks % 20 == 0 && this.getTarget() == null && owner != null) {
            PlayerEntity o = sw.getPlayerByUuid(owner);
            if (o != null && this.squaredDistanceTo(o) > 12 * 12) {
                this.getNavigation().startMovingTo(o, 1.1);
            }
        }
    }
}
