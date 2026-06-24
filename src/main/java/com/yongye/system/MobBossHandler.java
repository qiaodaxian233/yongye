package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 普通怪 BOSS 版(在"精英"之上的更高一档)——应需求新增。
 *  - 第 {@code mobBossStartDay}(默认 10)天起,普通敌对怪按 {@code mobBossChance} 低概率"BOSS化"。
 *  - 做法 = 给普通怪打上 {@code IS_BOSS} 标记 + 大幅属性 + 体型放大 + 红色 Boss 血条 + 名牌。
 *  - **关键**:一旦带 IS_BOSS,即自动继承全项目既有的"Boss 级"待遇,无需在此重写:
 *      · BossAbilityHandler 的全套能力(持续减伤 / 狂暴 / 召唤援军 / 冲击波 / 锁定)
 *      · BossHandler 的死亡掉落(翻倍大奖励)
 *      · PursuitHandler 的 Boss 档挖墙(可挖黑曜石级硬度)
 *      · HighHpCounterHandler 的 Boss 档高血量反制
 *      · LootHandler 跳过普通掉落表
 *  - 另用独立 {@code IS_MOB_BOSS} 标记区分"原版 Boss"与"普通怪 BOSS 版":仅后者挂这条自定义血条。
 *
 * 纯服务端、事件 + tick 驱动,不依赖 mixin。
 */
public final class MobBossHandler {
    private MobBossHandler() {}

    private static final Identifier ID_HEALTH = Identifier.of(Yongye.MOD_ID, "mobboss_health");
    private static final Identifier ID_ATTACK = Identifier.of(Yongye.MOD_ID, "mobboss_attack");
    private static final Identifier ID_SPEED = Identifier.of(Yongye.MOD_ID, "mobboss_speed");
    private static final Identifier ID_KB = Identifier.of(Yongye.MOD_ID, "mobboss_knockback");
    private static final Identifier ID_SCALE = Identifier.of(Yongye.MOD_ID, "mobboss_scale");

    // 每个怪物BOSS对应一条血条(WeakHashMap:怪被 GC 后自动清理)
    private static final Map<MobEntity, ServerBossBar> BARS = new WeakHashMap<>();

    public static void register() {
        // —— 生成时按概率 BOSS 化(第 mobBossStartDay 天起)——
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableMobBoss) return;
            if (!(entity instanceof MobEntity mob) || !(entity instanceof Monster)) return;
            if (BossHandler.isBoss(mob)) return;                              // 原版 Boss 不碰
            if (mob.getAttachedOrElse(ModAttachments.IS_PAIN, false)) return;
            if (mob.getAttachedOrElse(ModAttachments.IS_HIM, false)) return;

            // 已是怪物BOSS(重载):补回 IS_BOSS + 恢复血条追踪即可,不重新 roll
            if (mob.getAttachedOrElse(ModAttachments.IS_MOB_BOSS, false)) {
                mob.setAttached(ModAttachments.IS_BOSS, true);
                attachBar(mob);
                return;
            }
            if (ProgressionManager.gameDay(mob.getWorld()) < cfg.mobBossStartDay) return; // 早于设定天数不刷
            if (mob.getRandom().nextDouble() >= cfg.mobBossChance) return;

            makeMobBoss(mob, cfg);
        });

        // —— 每 tick 更新血条(血量% + 附近玩家可见),死亡/移除时清理 ——
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (BARS.isEmpty()) return;
            Iterator<Map.Entry<MobEntity, ServerBossBar>> it = BARS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<MobEntity, ServerBossBar> en = it.next();
                MobEntity mob = en.getKey();
                ServerBossBar bar = en.getValue();
                if (mob == null || !mob.isAlive() || mob.isRemoved()) {
                    bar.clearPlayers(); // 怪没了,血条从所有玩家屏幕移除
                    it.remove();
                    continue;
                }
                float max = mob.getMaxHealth();
                bar.setPercent(max > 0 ? Math.max(0f, Math.min(1f, mob.getHealth() / max)) : 0f);
                syncBarViewers(mob, bar);
            }
        });

        Yongye.LOGGER.info("[永夜] 普通怪 BOSS 版系统已挂载");
    }

    /** 测试用:把玩家附近 radius 格内、尚未BOSS化的普通敌对怪就地 BOSS 化。返回数量。 */
    public static int makeNearbyMobBoss(ServerPlayerEntity p, double radius) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return 0;
        YongyeConfig cfg = YongyeConfig.get();
        Box box = p.getBoundingBox().expand(radius);
        List<MobEntity> mobs = sw.getEntitiesByClass(MobEntity.class, box,
                m -> m.isAlive() && m instanceof Monster
                        && !BossHandler.isBoss(m)
                        && !m.getAttachedOrElse(ModAttachments.IS_PAIN, false)
                        && !m.getAttachedOrElse(ModAttachments.IS_HIM, false)
                        && !m.getAttachedOrElse(ModAttachments.IS_MOB_BOSS, false));
        int count = 0;
        for (MobEntity mob : mobs) {
            makeMobBoss(mob, cfg);
            count++;
        }
        return count;
    }

    private static void makeMobBoss(MobEntity mob, YongyeConfig cfg) {
        mob.setAttached(ModAttachments.IS_MOB_BOSS, true);
        mob.setAttached(ModAttachments.IS_BOSS, true); // 继承全套 Boss 机制(能力/掉落/挖墙/反制)

        addMultiplier(mob, EntityAttributes.GENERIC_MAX_HEALTH, ID_HEALTH, cfg.mobBossHealthMultiplier);
        addMultiplier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_ATTACK, cfg.mobBossAttackMultiplier);
        addMultiplier(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, ID_SPEED, cfg.mobBossSpeedMultiplier);
        addFlat(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, ID_KB, cfg.mobBossKnockbackResistanceAdd);
        // 体型放大,更像 Boss。GENERIC_SCALE 为 1.21.1 Yarn 名(1.21.2 起改名裸 SCALE)——【待编译验证】:
        // 若 build 报 "cannot find symbol GENERIC_SCALE",把这一行的属性换成 EntityAttributes.SCALE 即可(其余不动)。
        addMultiplier(mob, EntityAttributes.GENERIC_SCALE, ID_SCALE, cfg.mobBossScaleMultiplier);
        mob.setHealth(mob.getMaxHealth());

        // 动态对位:BOSS 版按更高的「期望击杀次数」拔高,确保对高攻玩家也是块硬骨头(它跳过了普通怪缩放)
        DynamicScaling.scaleToNearestPlayer(mob,
                cfg.dynamicMobBossTargetHits, cfg.dynamicMobBossSurviveHits, cfg.dynamicMobScanRadius);

        Text name = Text.literal("【BOSS】 ").formatted(Formatting.DARK_RED, Formatting.BOLD)
                .append(mob.getType().getName());
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);

        attachBar(mob);
    }

    /** 给怪物挂一条红色 Boss 血条并登记到 BARS。重载时也走这里恢复。 */
    private static void attachBar(MobEntity mob) {
        Text name = mob.getCustomName() != null ? mob.getCustomName()
                : Text.literal("【BOSS】 ").append(mob.getType().getName());
        ServerBossBar bar = new ServerBossBar(name, BossBar.Color.RED, BossBar.Style.PROGRESS);
        float max = mob.getMaxHealth();
        bar.setPercent(max > 0 ? Math.max(0f, Math.min(1f, mob.getHealth() / max)) : 1f);
        BARS.put(mob, bar);
    }

    /** 让半径内玩家看到血条,半径外/异世界的移除。 */
    private static void syncBarViewers(MobEntity mob, ServerBossBar bar) {
        if (!(mob.getWorld() instanceof ServerWorld sw)) return;
        double r = YongyeConfig.get().mobBossBarRadius;
        double r2 = r * r;
        for (ServerPlayerEntity p : new ArrayList<>(bar.getPlayers())) {
            if (p.getWorld() != sw || p.squaredDistanceTo(mob) > r2) bar.removePlayer(p);
        }
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.squaredDistanceTo(mob) <= r2 && !bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
    }

    private static void addMultiplier(LivingEntity e, RegistryEntry<EntityAttribute> attr, Identifier id, double multiplier) {
        if (multiplier == 1.0) return;
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null) return; // 该实体没有此属性(如个别怪无 SCALE)则跳过,不报错
        inst.removeModifier(id);
        inst.addPersistentModifier(new EntityAttributeModifier(
                id, multiplier - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void addFlat(LivingEntity e, RegistryEntry<EntityAttribute> attr, Identifier id, double value) {
        if (value == 0.0) return;
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPersistentModifier(new EntityAttributeModifier(
                id, value, EntityAttributeModifier.Operation.ADD_VALUE));
    }
}
