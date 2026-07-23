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
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
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
