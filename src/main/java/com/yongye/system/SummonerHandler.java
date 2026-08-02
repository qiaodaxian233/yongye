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

    /** m413 干弟第 v 槽的显示名:配置 summonGanDiNames 逗号分槽,空槽回默认 VARIANT_NAMES。 */
    public static String gandiNameFor(int v) {
        String raw = YongyeConfig.get().summonGanDiNames;
        if (raw != null && !raw.isBlank()) {
            String[] parts = raw.split(",", -1);
            if (v < parts.length && !parts[v].isBlank()) return parts[v].trim();
        }
        return com.yongye.entity.GanDiEntity.VARIANT_NAMES[Math.max(0, Math.min(4, v))];
    }

    /** m413 给在场小队即时套用当前配置名(/yongye puppet 改名后调);返回改了几只。 */
    public static int applyGanDiNames(net.minecraft.server.network.ServerPlayerEntity p) {
        List<com.yongye.entity.GanDiEntity> squad = gandiByOwner.get(p.getUuid());
        if (squad == null) return 0;
        net.minecraft.util.Formatting[] colors = {net.minecraft.util.Formatting.AQUA, net.minecraft.util.Formatting.YELLOW,
                net.minecraft.util.Formatting.GREEN, net.minecraft.util.Formatting.LIGHT_PURPLE, net.minecraft.util.Formatting.DARK_GREEN}; // 与 summonGanDi 处同序
        int n = 0;
        for (var e : squad) {
            if (e == null || !e.isAlive()) continue;
            int v = Math.max(0, Math.min(4, e.getVariant()));
            e.setCustomName(net.minecraft.text.Text.literal(gandiNameFor(v)).formatted(colors[v]));
            n++;
        }
        return n;
    }
    private static final Identifier BOOST_ID = Identifier.of(Yongye.MOD_ID, "summon_boost");
    private static final Identifier OWNER_HP_ID = Identifier.of(Yongye.MOD_ID, "summon_owner_hp");
    private static final Identifier OWNER_ATK_ID = Identifier.of(Yongye.MOD_ID, "summon_owner_atk");

    private record Tracked(IronGolemEntity golem, long expireAt) {}
    private static final Map<UUID, List<Tracked>> byOwner = new HashMap<>();
    /** m226:肝帝天团按召唤者跟踪——重复施放先散上一批,修「连按癫狂叠好几队」。 */
    private static final Map<UUID, List<com.yongye.entity.GanDiEntity>> gandiByOwner = new HashMap<>();

    /**
     * m300 击杀归属(作者:「召唤物击杀也算这个人击杀,要么就没意思了」):
     * 攻击者是玩家 → 本人;是己方召唤物(傀儡 tag/肝帝/暗影分身)→ 折算到主人(主人在线才算)。
     * 全库六处「玩家击杀」口径统一走这里:看板计数/随机掉落门+动态爆率/保护卷/贪婪经验/击杀任务/蚀域掉落。
     * 关 summonKillsCreditOwner 回「只认亲手」。
     */
    public static ServerPlayerEntity creditedKiller(net.minecraft.entity.damage.DamageSource source) {
        net.minecraft.entity.Entity a = source.getAttacker();
        if (a instanceof ServerPlayerEntity p) return p;
        if (a == null || !YongyeConfig.get().summonKillsCreditOwner) return null;
        UUID owner = null;
        if (a instanceof com.yongye.entity.GanDiEntity g) owner = g.getOwner();
        else if (a instanceof com.yongye.entity.WarlockCloneEntity w) owner = w.getOwner();
        else if (a instanceof IronGolemEntity ig && ig.getCommandTags().contains(TAG)) owner = ownerOf(ig);
        if (owner == null || !(a.getWorld() instanceof ServerWorld sw)) return null;
        return sw.getPlayerByUuid(owner) instanceof ServerPlayerEntity sp ? sp : null;
    }

    /** m300:反查傀儡主人(内存表,量小直扫)。m320 改 public:协同集火(SummonAssistHandler)复用。 */
    public static UUID ownerOf(IronGolemEntity g) {
        for (Map.Entry<UUID, List<Tracked>> e : byOwner.entrySet()) {
            for (Tracked t : e.getValue()) {
                if (t.golem() == g) return e.getKey();
            }
        }
        return null;
    }

    public static void register() {
        // 寿命管理:每 20 tick 扫一轮(m233 顺带做傀儡回血+统御被动)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0 || (byOwner.isEmpty() && gandiByOwner.isEmpty())) return;
            YongyeConfig cfg = YongyeConfig.get();
            long now = server.getOverworld().getTime();
            for (Iterator<Map.Entry<UUID, List<Tracked>>> it = byOwner.entrySet().iterator(); it.hasNext(); ) {
                List<Tracked> list = it.next().getValue();
                list.removeIf(tr -> {
                    IronGolemEntity g = tr.golem();
                    if (g.isRemoved() || !g.isAlive()) return true;
                    if (now >= tr.expireAt()) { dismiss(g); return true; }
                    // m233:傀儡持续回血(「召唤/强化」加强——三技能职业,傀儡要经打)
                    if (cfg.summonerGolemRegenPerSec > 0 && g.getHealth() < g.getMaxHealth()) {
                        g.heal((float) cfg.summonerGolemRegenPerSec);
                    }
                    return false;
                });
                if (list.isEmpty()) it.remove();
            }
            // m233:统御被动——场上每有自己的召唤物(傀儡/朋友)存活,召唤者获得抗性I;
            // 召唤物 ≥ summonerGuardAuraBigCount(默认5)时升抗性II。全走 proven API(getPlayerList/状态效果)。
            if (cfg.enableSummonerGuardAura) {
                for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList()) {
                    int n = 0;
                    List<Tracked> gl = byOwner.get(o.getUuid());
                    if (gl != null) for (Tracked tr : gl) if (tr.golem().isAlive()) n++;
                    List<com.yongye.entity.GanDiEntity> fl = gandiByOwner.get(o.getUuid());
                    if (fl != null) for (com.yongye.entity.GanDiEntity g : fl) if (g.isAlive()) n++;
                    if (n > 0) {
                        int amp = n >= Math.max(1, cfg.summonerGuardAuraBigCount) ? 1 : 0;
                        o.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.RESISTANCE, 45, amp, true, false, true));
                    }
                }
            }
        });
        // 重启遗留清理:带 tag 却不在内存表内的傀儡 = 上个会话的残留
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof IronGolemEntity g && g.getCommandTags().contains(TAG) && !isTracked(g)) {
                g.discard();
                Yongye.LOGGER.info("[夜蚀] 清理上个会话残留的召唤傀儡 @ {},{},{}", g.getBlockX(), g.getBlockY(), g.getBlockZ());
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
        // 【m235 修出生即死】ENTITY_LOAD 对新生成实体也会同步触发(不只 chunk 加载):
        // 必须在 spawnEntity 之前就把傀儡登进追踪表,否则「清残留」钩子看到带 tag 又
        // 查无此籍,当场 discard——m223 起傀儡一直被自己人秒杀,消息却照报成功。
        byOwner.put(p.getUuid(), list);
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
            Tracked tr = new Tracked(g, expire);
            list.add(tr);               // 先登记再生成(见上方注释)
            if (sw.spawnEntity(g)) {
                done++;
            } else {
                list.remove(tr);        // 生成失败才摘除
            }
        }
        if (list.isEmpty()) byOwner.remove(p.getUuid());   // m235:表已提前放入,空了才摘
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
            e.setCustomName(net.minecraft.text.Text.literal(gandiNameFor(v)).formatted(colors[v])); // m413 自定义名(空槽=默认)
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
