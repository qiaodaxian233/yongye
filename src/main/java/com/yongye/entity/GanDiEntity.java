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
 * 「肝帝玩家」(m223,召唤师·癫狂请来的朋友):玩家模型的友军 NPC——是并肩作战的朋友,不是仆从。
 * 皮肤占位:assets/yongye/textures/entity/gandi.png(64×64 标准皮肤布局),
 * 作者发来正式皮肤后【直接覆盖该文件】即可,零代码改动。
 * AI:近战攻击敌对怪(ActiveTargetGoal 只锁 HostileEntity,不打玩家/友军);
 * 离朋友(召唤者)>12 格时优先跑回其身边;寿命到点自散(灵魂粒子)。
 * 属性从配置读(gandiHealth/gandiAttack/gandiSpeed),注册期读取——改配置后需重启生效。
 */
public class GanDiEntity extends PathAwareEntity {

    /** m224:肝帝变体(0岛风/1晚安/2不爱肝/3迷人)。DataTracker 同步给客户端选皮肤。
     *  待编译验证:initDataTracker(DataTracker.Builder) 签名与 TrackedDataHandlerRegistry.INTEGER(1.20.5+ 标准写法)。 */
    private static final TrackedData<Integer> VARIANT =
            DataTracker.registerData(GanDiEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final String[] VARIANT_NAMES = {"岛风", "晚安", "不爱肝", "迷人", "芥末"};

    /** m227:台词改从配置读(gandiTalk* 字段,| 分句;Debug 配置页可查改),类别:0登场/1战斗/2闲聊/3告别/4阵亡。 */
    private String[] pool(int cat) {
        YongyeConfig c = YongyeConfig.get();
        String[][] all = {
                {c.gandiTalkDaofengSpawn, c.gandiTalkDaofengCombat, c.gandiTalkDaofengIdle, c.gandiTalkDaofengBye, c.gandiTalkDaofengDeath},
                {c.gandiTalkWananSpawn,   c.gandiTalkWananCombat,   c.gandiTalkWananIdle,   c.gandiTalkWananBye,   c.gandiTalkWananDeath},
                {c.gandiTalkBuganSpawn,   c.gandiTalkBuganCombat,   c.gandiTalkBuganIdle,   c.gandiTalkBuganBye,   c.gandiTalkBuganDeath},
                {c.gandiTalkMirenSpawn,   c.gandiTalkMirenCombat,   c.gandiTalkMirenIdle,   c.gandiTalkMirenBye,   c.gandiTalkMirenDeath},
                {c.gandiTalkJiemoSpawn,   c.gandiTalkJiemoCombat,   c.gandiTalkJiemoIdle,   c.gandiTalkJiemoBye,   c.gandiTalkJiemoDeath}};
        String raw = all[getVariant()][cat];
        if (raw == null || raw.isBlank()) return new String[0];
        return java.util.Arrays.stream(raw.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    /** 从某类池随机说一句(池空=沉默)。 */
    private void speakFrom(int cat) {
        String[] p = pool(cat);
        if (p.length > 0) speak(p[this.random.nextInt(p.length)]);
    }

    private UUID owner;
    private int lifeTicks;
    private long nextTalkAt;   // 台词节流:战斗白/闲聊共用冷却

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
    public void setVariant(int v) { this.dataTracker.set(VARIANT, Math.max(0, Math.min(4, v))); }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, true));
    }

    /** 台词直达朋友(召唤者)聊天栏:【彩色名字】台词(可用 gandiChatEnabled 关闭)。 */
    private void speak(String line) {
        if (!YongyeConfig.get().gandiChatEnabled || owner == null) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        net.minecraft.entity.player.PlayerEntity o = sw.getPlayerByUuid(owner);
        if (o == null) return;
        net.minecraft.util.Formatting[] colors = { net.minecraft.util.Formatting.AQUA, net.minecraft.util.Formatting.YELLOW,
                net.minecraft.util.Formatting.GREEN, net.minecraft.util.Formatting.LIGHT_PURPLE, net.minecraft.util.Formatting.DARK_GREEN };
        o.sendMessage(net.minecraft.text.Text.literal("【" + VARIANT_NAMES[getVariant()] + "】")
                .formatted(colors[getVariant()])
                .append(net.minecraft.text.Text.literal(line).formatted(net.minecraft.util.Formatting.WHITE)), false);
    }

    @Override
    public void onDeath(net.minecraft.entity.damage.DamageSource damageSource) {
        speakFrom(4);   // 阵亡白(区别于寿终的告别白)
        super.onDeath(damageSource);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        this.setPersistent();
    }

    /** m300:击杀归属用。 */
    public UUID getOwner() {
        return owner;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        int lifeMax = Math.max(100, YongyeConfig.get().gandiLifeSec * 20);
        ++lifeTicks;
        // 登场白:错峰开口(变体 0/1/2/3 分别在第 5/25/45/65 tick),不刷屏
        if (lifeTicks == 5 + getVariant() * 20) speakFrom(0);
        // 离场预警:剩 10 秒时由岛风代表全队说一句(只说一次,避免 4 连报)
        if (lifeTicks == lifeMax - 200 && getVariant() == 0) speak("时间不多了,收尾吧!(天团 10 秒后离场)");
        // 寿命:到点化作灵魂散去,各自留一句告别白
        if (lifeTicks >= lifeMax) {
            speakFrom(3);
            sw.spawnParticles(ParticleTypes.SOUL, getX(), getBodyY(0.5), getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            this.discard();
            return;
        }
        // 战斗白/闲聊:共用 12 秒节流;有目标时 35% 战斗白,无目标时每 15 秒 25% 闲聊
        long now = sw.getTime();
        if (now >= nextTalkAt) {
            if (this.getTarget() != null && this.random.nextFloat() < 0.35f) {
                speakFrom(1); nextTalkAt = now + 240;
            } else if (this.getTarget() == null && lifeTicks % 300 == 0 && this.random.nextFloat() < 0.25f) {
                speakFrom(2); nextTalkAt = now + 240;
            }
        }
        // 分工光环(m224/m230,每 3 秒一轮):岛风=傀儡恢复+抗性 | 晚安=修傀儡+缩朋友CD |
        // 不爱肝=傀儡生命上限+强抗 | 迷人=傀儡力量+速度 | 芥末·爆肝节奏=给朋友急迫II+恢复I(劳模光环,人设待作者补充抖音方向后可调)
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
            if (getVariant() == 1) com.yongye.system.ClassUltimateManager.reduceCooldown(owner, 40); // 晚安:每 3 秒帮朋友缩 2 秒 CD
            if (getVariant() == 4) {   // 芥末:朋友光环(急迫II+恢复I),肝就完事了
                net.minecraft.entity.player.PlayerEntity o = sw.getPlayerByUuid(owner);
                if (o != null) {
                    o.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 100, 1, true, false, true));
                    o.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, false, true));
                }
            }
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER, getX(), getBodyY(0.8), getZ(), 3, 0.3, 0.4, 0.3, 0.0);
        }

        // 跟随朋友:无仇恨且离得太远时跑回其身边(每 10 tick 判一次省性能)
        if (lifeTicks % 10 == 0 && this.getTarget() == null && owner != null) {
            PlayerEntity o = sw.getPlayerByUuid(owner);
            if (o != null && this.squaredDistanceTo(o) > 144.0) {
                this.getNavigation().startMovingTo(o, 1.15);
            }
        }
    }
}
