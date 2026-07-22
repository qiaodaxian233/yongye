package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 召唤师系统(m223):「召唤」5 座铁傀儡 +「强化」×2 血攻 + 傀儡寿命管理。
 * m232 起「召唤」由小技能键(默认 C)触发,走独立冷却,不再占用大招 CD;大招键专职「癫狂」。
 * - 傀儡=原版 IronGolemEntity + setPlayerCreated(true)(只打怪不打玩家,原版友军逻辑白嫖);
 *   「强化」= MAX_HEALTH/ATTACK_DAMAGE 各挂 ADD_MULTIPLIED_TOTAL 修饰符(值=summonerGolemBoostMult,
 *   默认 1.0=翻一倍),照 MobEnhancementHandler 的修饰符写法。
 * - 再次召唤先散掉上一批(防傀儡海);寿命到点自散(POOF 粒子)。
 * - 追踪用内存表 + 命令 tag "yongye_summon" 双保险:服务器重启后内存表丢失,
 *   ENTITY_LOAD 见到带 tag 却不在表内的傀儡直接清掉(防遗留傀儡永驻世界)。
 */
public final class SummonerHandler {
    private SummonerHandler() {}

    public static final String TAG = "yongye_summon";
    private static final Identifier BOOST_ID = Identifier.of(Yongye.MOD_ID, "summon_boost");
    private static final Identifier OWNER_HP_ID = Identifier.of(Yongye.MOD_ID, "summon_owner_hp");
    private static final Identifier OWNER_ATK_ID = Identifier.of(Yongye.MOD_ID, "summon_owner_atk");

    private record Tracked(IronGolemEntity golem, long expireAt) {}
    private static final Map<UUID, List<Tracked>> byOwner = new HashMap<>();
    /** m226:肝帝天团按召唤者跟踪——重复施放先散上一批,修「连按癫狂叠好几队」。 */
    private static final Map<UUID, List<com.yongye.entity.GanDiEntity>> gandiByOwner = new HashMap<>();

    public static void register() {
        // 寿命管理:每 20 tick 扫一轮
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0 || byOwner.isEmpty()) return;
            long now = server.getOverworld().getTime();
            for (Iterator<Map.Entry<UUID, List<Tracked>>> it = byOwner.entrySet().iterator(); it.hasNext(); ) {
                List<Tracked> list = it.next().getValue();
                list.removeIf(tr -> {
                    IronGolemEntity g = tr.golem();
                    if (g.isRemoved() || !g.isAlive()) return true;
                    if (now >= tr.expireAt()) { dismiss(g); return true; }
                    return false;
                });
                if (list.isEmpty()) it.remove();
            }
        });
        // 重启遗留清理:带 tag 却不在内存表内的傀儡 = 上个会话的残留
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof IronGolemEntity g && g.getCommandTags().contains(TAG) && !isTracked(g)) {
                g.discard();
            }
        });
        Yongye.LOGGER.info("[夜蚀] 召唤师系统已挂载(傀儡召唤/强化/寿命)");
    }

    private static boolean isTracked(IronGolemEntity g) {
        for (List<Tracked> list : byOwner.values())
            for (Tracked tr : list) if (tr.golem() == g) return true;
        return false;
    }

    private static void dismiss(IronGolemEntity g) {
        if (g.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.POOF, g.getX(), g.getBodyY(0.5), g.getZ(), 15, 0.5, 0.6, 0.5, 0.02);
        }
        g.discard();
    }

    /** 「召唤」:在玩家身边召出 N 座强化铁傀儡(先散掉上一批)。返回实际召出数。 */
    public static int summonGolems(ServerPlayerEntity p) {
        YongyeConfig cfg = YongyeConfig.get();
        ServerWorld sw = (ServerWorld) p.getWorld();
        // 先散上一批
        List<Tracked> old = byOwner.remove(p.getUuid());
        if (old != null) for (Tracked tr : old) if (tr.golem().isAlive()) dismiss(tr.golem());

        // m229:持「鹰扬」且本职业生效 → 强化倍率额外 +staffExtraBoost
        double boostMult = cfg.summonerGolemBoostMult;
        net.minecraft.item.Item staff = com.yongye.registry.ModItems.getClassWeapon(com.yongye.item.PlayerClass.SUMMONER);
        if (staff != null && p.getMainHandStack().isOf(staff)
                && ClassManager.isActive(p, com.yongye.item.PlayerClass.SUMMONER)) {
            boostMult += Math.max(0, cfg.summonerStaffExtraBoost);
        }
        // m229:召唤物随召唤者属性成长——附加 朋友(你)血量×比例 / 攻击×比例(ADD_VALUE)
        double ownerHp  = p.getMaxHealth() * Math.max(0, cfg.summonerOwnerHpRatio);
        double ownerAtk = p.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE)
                * Math.max(0, cfg.summonerOwnerAtkRatio);

        int count = Math.max(1, cfg.ultSummonerGolemCount);
        List<Tracked> list = new ArrayList<>(count);
        long expire = sw.getTime() + Math.max(100L, cfg.ultSummonerGolemLifeSec * 20L);
        int done = 0;
        for (int i = 0; i < count; i++) {
            IronGolemEntity g = EntityType.IRON_GOLEM.create(sw);
            if (g == null) break;
            double ang = Math.PI * 2 * i / count;
            BlockPos pos = p.getBlockPos().add((int) Math.round(Math.cos(ang) * 2.5), 0, (int) Math.round(Math.sin(ang) * 2.5));
            g.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYaw(), 0);
            g.setPlayerCreated(true);   // 原版友军逻辑:主动打怪、不打玩家
            g.setPersistent();
            g.addCommandTag(TAG);
            // 「强化」:血/攻 ×(1+倍率),默认翻一倍
            boost(g, EntityAttributes.GENERIC_MAX_HEALTH, boostMult);
            boost(g, EntityAttributes.GENERIC_ATTACK_DAMAGE, boostMult);
            addFlat(g, EntityAttributes.GENERIC_MAX_HEALTH, OWNER_HP_ID, ownerHp);
            addFlat(g, EntityAttributes.GENERIC_ATTACK_DAMAGE, OWNER_ATK_ID, ownerAtk);
            g.setHealth(g.getMaxHealth());
            if (sw.spawnEntity(g)) {
                list.add(new Tracked(g, expire));
                done++;
            }
        }
        if (!list.isEmpty()) byOwner.put(p.getUuid(), list);
        return done;
    }

    private static void boost(IronGolemEntity g, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr, double mult) {
        if (mult <= 0) return;
        EntityAttributeInstance inst = g.getAttributeInstance(attr);
        if (inst == null || inst.getModifier(BOOST_ID) != null) return;
        inst.addPersistentModifier(new EntityAttributeModifier(BOOST_ID, mult,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** 平加成(ADD_VALUE):召唤物随召唤者属性成长用。 */
    private static void addFlat(net.minecraft.entity.LivingEntity e,
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
            Identifier id, double value) {
        if (value <= 0) return;
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null || inst.getModifier(id) != null) return;
        inst.addPersistentModifier(new EntityAttributeModifier(id, value, EntityAttributeModifier.Operation.ADD_VALUE));
    }

    /** 「癫狂」的召唤物(m224):肝帝天团四人齐上——0岛风/1晚安/2不爱肝/3迷人,
     *  名牌常显;不爱肝+100%血(主坦),迷人+50%攻+20%速(输出),芥末+30%速(m230 劳模,给朋友挂急迫)。返回实际召出数。 */
    public static int summonGanDi(ServerPlayerEntity p) {
        ServerWorld sw = (ServerWorld) p.getWorld();
        net.minecraft.util.Formatting[] colors = {
                net.minecraft.util.Formatting.AQUA, net.minecraft.util.Formatting.YELLOW,
                net.minecraft.util.Formatting.GREEN, net.minecraft.util.Formatting.LIGHT_PURPLE,
                net.minecraft.util.Formatting.DARK_GREEN };
        // 上一批还在?先礼貌散场(POOF),防连按叠队
        List<com.yongye.entity.GanDiEntity> old = gandiByOwner.remove(p.getUuid());
        if (old != null) for (com.yongye.entity.GanDiEntity g : old) {
            if (g.isAlive()) {
                sw.spawnParticles(ParticleTypes.POOF, g.getX(), g.getBodyY(0.5), g.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
                g.discard();
            }
        }
        List<com.yongye.entity.GanDiEntity> squad = new ArrayList<>(5);
        int done = 0;
        for (int v = 0; v < 5; v++) {
            com.yongye.entity.GanDiEntity e = ModEntities.GANDI.create(sw);
            if (e == null) break;
            double ang = Math.PI * 2 * v / 5 + Math.PI / 4;
            e.refreshPositionAndAngles(p.getX() + Math.cos(ang) * 1.8, p.getY(), p.getZ() + Math.sin(ang) * 1.8, p.getYaw(), 0);
            e.setOwner(p.getUuid());
            e.setVariant(v);
            e.setCustomName(net.minecraft.text.Text.literal(com.yongye.entity.GanDiEntity.VARIANT_NAMES[v]).formatted(colors[v]));
            e.setCustomNameVisible(true);
            // m229:肝帝随召唤者属性成长(同傀儡比例)
            addFlat(e, EntityAttributes.GENERIC_MAX_HEALTH,
                    OWNER_HP_ID, p.getMaxHealth() * Math.max(0, YongyeConfig.get().summonerOwnerHpRatio));
            addFlat(e, EntityAttributes.GENERIC_ATTACK_DAMAGE, OWNER_ATK_ID,
                    p.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE)
                            * Math.max(0, YongyeConfig.get().summonerOwnerAtkRatio));
            if (v == 2) boostEntity(e, EntityAttributes.GENERIC_MAX_HEALTH, 1.0);      // 不爱肝:主坦
            if (v == 3) { boostEntity(e, EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.5); // 迷人:输出
                          boostEntity(e, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2); }
            if (v == 4) boostEntity(e, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3);   // 芥末:劳模腿快
            e.setHealth(e.getMaxHealth());
            if (sw.spawnEntity(e)) { squad.add(e); done++; }
        }
        if (!squad.isEmpty()) gandiByOwner.put(p.getUuid(), squad);
        return done;
    }

    private static void boostEntity(net.minecraft.entity.LivingEntity e,
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr, double mult) {
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null || inst.getModifier(BOOST_ID) != null) return;
        inst.addPersistentModifier(new EntityAttributeModifier(BOOST_ID, mult,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** 该玩家当前存活的铁傀儡列表(肝帝光环用)。 */
    public static java.util.List<IronGolemEntity> golemsOf(UUID owner) {
        List<Tracked> list = byOwner.get(owner);
        if (list == null) return java.util.List.of();
        java.util.List<IronGolemEntity> out = new ArrayList<>(list.size());
        for (Tracked tr : list) if (tr.golem().isAlive()) out.add(tr.golem());
        return out;
    }

    /** 召唤师资源条:存活傀儡数 / 上限(HUD 用)。 */
    public static float aliveGolemRatio(ServerPlayerEntity p) {
        List<Tracked> list = byOwner.get(p.getUuid());
        if (list == null || list.isEmpty()) return 0f;
        int alive = 0;
        for (Tracked tr : list) if (tr.golem().isAlive()) alive++;
        return Math.min(1f, alive / (float) Math.max(1, YongyeConfig.get().ultSummonerGolemCount));
    }
}
