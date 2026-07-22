package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.world.ServerWorld;
import com.yongye.mixin.EnderDragonFightAccessor;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 末地末影龙强化(m183,应需求):原版末地龙战的龙从"新手村看门龙"改成真正的终局 BOSS——
 * <ul>
 *   <li><b>10 亿血 + 高防</b>:ENTITY_LOAD 对末地维度的 EnderDragonEntity 挂持久属性修饰
 *       (照 MobEnhancementHandler 的 removeModifier+addPersistentModifier 幂等套路):
 *       生命 = endDragonHealth(默认 10 亿,m127 属性上限也是 10 亿正好装下)、
 *       护甲 +endDragonArmor(默认 40)/ 韧性 +endDragonToughness(默认 20,
 *       接近原版减伤公式 80% 上限)。首次强化才补满血(END_DRAGON_BUFFED 附件门,
 *       区块重载/存档重进不重复回满)。原版龙战机制(水晶回血/龙息/俯冲/传送门)全保留。</li>
 *   <li><b>三条命</b>:ALLOW_DEATH 拦死亡——已用命数 &lt; endDragonLives-1 时取消死亡、
 *       满血复活 + 全服播报剩余命数;最后一条命才走原版死亡演出/经验/传送门。
 *       命数存实体持久附件(DRAGON_LIVES_USED),水晶重召的新龙从 0 条开始。</li>
 *   <li><b>脱战回血</b>:每秒检查末地所有龙,连续 endDragonRegenDelaySeconds(默认 30)秒
 *       没掉血且未满血 → 每秒回 endDragonRegenPercent(默认 1)% 最大生命,逼玩家保持输出
 *       不能拉扯磨血。掉血判定用血量对比(阈值 0.5,10 亿量级 float 步进 ~64 足够触发),
 *       复活重置计时。</li>
 * </ul>
 *
 * <p>只作用末地维度的原版 EnderDragonEntity;自建龙(ToroEnderDragonEntity)是独立实体不受影响。
 * 注意:1% × 10 亿 = 每秒回 1000 万,30 秒脱战后玩家 DPS 低于这个数就永远磨不死——有意设计。
 */
public final class EndDragonHandler {
    private EndDragonHandler() {}

    private static final Identifier ID_HP = Identifier.of(Yongye.MOD_ID, "end_dragon_hp");
    private static final Identifier ID_ARMOR = Identifier.of(Yongye.MOD_ID, "end_dragon_armor");
    private static final Identifier ID_TOUGH = Identifier.of(Yongye.MOD_ID, "end_dragon_tough");

    /** 每龙"距上次掉血的秒数"与上一秒血量(运行时,重启重置无妨——重进 30 秒后才恢复回血)。 */
    private static final Map<UUID, Integer> QUIET_SECONDS = new HashMap<>();
    private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();

    private static int tick = 0;

    public static void register() {
        // ① 强化:末地的龙加载即挂属性(幂等,重载不叠)
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof EnderDragonEntity dragon)) return;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableEndDragonBuff) return;
            if (world.getRegistryKey() != World.END) return;
            applyBuff(dragon, cfg);
        });

        // ② 三条命:死亡拦截。ALLOW_DEATH 在生命归零、死亡处理前回调,
        //    返回 false 取消死亡(回调内必须把血抬回 >0)。
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof EnderDragonEntity dragon)) return true;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableEndDragonBuff || cfg.endDragonLives <= 1) return true;
            if (dragon.getWorld().getRegistryKey() != World.END) return true;

            int used = dragon.getAttachedOrElse(ModAttachments.DRAGON_LIVES_USED, 0);
            int left = cfg.endDragonLives - 1 - used; // 本次死亡后还剩几条
            if (left <= 0) return true; // 最后一条命,走原版死亡演出

            dragon.setAttached(ModAttachments.DRAGON_LIVES_USED, used + 1);
            dragon.setHealth(dragon.getMaxHealth());
            QUIET_SECONDS.put(dragon.getUuid(), 0);
            LAST_HEALTH.put(dragon.getUuid(), dragon.getMaxHealth());
            if (dragon.getWorld() instanceof ServerWorld sw) {
                sw.getServer().getPlayerManager().broadcast(Text.literal(
                                "§5§l[末影龙] §d末影龙燃尽了一条生命,以完好之躯再度苏醒……(剩余 " + left + " 命)")
                        .formatted(Formatting.DARK_PURPLE), false);
            }
            return false;
        });

        // ③ 脱战回血:每 20 tick(1 秒)检查末地的龙
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tick < 20) return;
            tick = 0;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableEndDragonBuff) return; // m188:regenPercent<=0 只关回血,血条数字照更
            ServerWorld end = server.getWorld(World.END);
            if (end == null) {
                if (!QUIET_SECONDS.isEmpty()) { QUIET_SECONDS.clear(); LAST_HEALTH.clear(); }
                return;
            }
            Set<UUID> seen = new HashSet<>();
            for (var e : end.iterateEntities()) {
                if (!(e instanceof EnderDragonEntity dragon) || !dragon.isAlive()) continue;
                UUID id = dragon.getUuid();
                seen.add(id);
                float cur = dragon.getHealth();
                Float prev = LAST_HEALTH.get(id);
                int quiet = QUIET_SECONDS.getOrDefault(id, 0);
                // 掉血(阈值 0.5;10 亿量级 float 最小步进 ~64,任何有效伤害都触发)→ 计时归零
                quiet = (prev != null && cur < prev - 0.5f) ? 0 : quiet + 1;
                if (cfg.endDragonRegenPercent > 0 && quiet >= Math.max(1, cfg.endDragonRegenDelaySeconds)) {
                    float max = dragon.getMaxHealth();
                    if (cur < max) {
                        cur = Math.min(max, cur + (float) (max * cfg.endDragonRegenPercent / 100.0));
                        dragon.setHealth(cur);
                    }
                }
                QUIET_SECONDS.put(id, quiet);
                LAST_HEALTH.put(id, cur);
                // m187:末地龙血量嵌入血条名(‖当前/最大),每秒刷新
                EnderDragonFight fight = end.getEnderDragonFight();
                if (fight != null) {
                    ServerBossBar dragonBar = ((EnderDragonFightAccessor) fight).yongye$getBossBar();
                    if (dragonBar != null) {
                        dragonBar.setName(dragon.getDisplayName().copy()
                                .append(Text.literal("\u2016" + String.format(java.util.Locale.ROOT, "%.0f", (double) cur) + "/" + String.format(java.util.Locale.ROOT, "%.0f", (double) dragon.getMaxHealth()))));
                    }
                }
            }
            // 清掉已死亡/消失的龙(避免旧条目残留)
            QUIET_SECONDS.keySet().retainAll(seen);
            LAST_HEALTH.keySet().retainAll(seen);
        });

        Yongye.LOGGER.info("[夜蚀] 末地末影龙强化已挂载(10亿血/三命/脱战回血)");
    }

    /** 挂属性修饰(幂等)+ 首次补满血。 */
    private static void applyBuff(EnderDragonEntity dragon, YongyeConfig cfg) {
        // 生命:ADD_VALUE 补到目标值(delta 基于基础值算,removeModifier 后幂等)
        EntityAttributeInstance hp = dragon.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null && cfg.endDragonHealth > 0) {
            hp.removeModifier(ID_HP);
            double delta = cfg.endDragonHealth - hp.getBaseValue();
            if (delta > 0) {
                hp.addPersistentModifier(new EntityAttributeModifier(
                        ID_HP, delta, EntityAttributeModifier.Operation.ADD_VALUE));
            }
        }
        addFlat(dragon, EntityAttributes.GENERIC_ARMOR, ID_ARMOR, cfg.endDragonArmor);
        addFlat(dragon, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, ID_TOUGH, cfg.endDragonToughness);

        if (!dragon.getAttachedOrElse(ModAttachments.END_DRAGON_BUFFED, false)) {
            dragon.setAttached(ModAttachments.END_DRAGON_BUFFED, true);
            dragon.setHealth(dragon.getMaxHealth());
        }
    }

    /** 与 MobEnhancementHandler.addFlat 逐字同款(那边是 private,本地复刻)。 */
    private static void addFlat(LivingEntity e,
                                net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                                Identifier id, double value) {
        if (value == 0.0) return;
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPersistentModifier(new EntityAttributeModifier(
                id, value, EntityAttributeModifier.Operation.ADD_VALUE));
    }
}
