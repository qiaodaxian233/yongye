package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.network.ComboPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 连击计数器(m273,学 Epic Fight/动作游戏惯例):
 * 玩家每打中一只怪连击 +1(同一 tick 横扫/回旋命中多只只算 1),comboTimeoutTicks 没打中清零。
 * 每 5 连击为一档,按档给伤害/攻速百分比(ADD_MULTIPLIED_TOTAL,各自封顶),断连即撤。
 * 计数变化实时同步客户端(ComboPayload),HUD 在热栏右上画连击数。
 * 观察者口径同 CombatFxHandler:挂 ALLOW_DAMAGE 永远放行、只认玩家→非玩家,
 * 注册排在伤害过滤/格挡之后——被取消的伤害不涨连击。
 */
public final class ComboHandler {
    private ComboHandler() {}

    private static final Identifier ID_DMG = Identifier.of(Yongye.MOD_ID, "combo_dmg");
    private static final Identifier ID_SPD = Identifier.of(Yongye.MOD_ID, "combo_spd");

    private static final class State { int count; long lastHitTick; int tier; }
    private static final Map<UUID, State> COMBO = new HashMap<>();

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableCombo || amount <= 0) return true;
            if (!(source.getAttacker() instanceof ServerPlayerEntity p)) return true;
            if (entity instanceof PlayerEntity || entity == p) return true;

            long now = p.getWorld().getTime();
            State st = COMBO.computeIfAbsent(p.getUuid(), u -> new State());
            if (st.lastHitTick == now && st.count > 0) return true; // 同 tick 多目标只算 1 连
            st.count++;
            st.lastHitTick = now;
            applyTier(p, st, c);
            ServerPlayNetworking.send(p, new ComboPayload(st.count));
            return true;
        });

        // 断连检查:每 10 tick 扫一遍,超时清零撤加成
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 != 0 || COMBO.isEmpty()) return;
            YongyeConfig c = YongyeConfig.get();
            Iterator<Map.Entry<UUID, State>> it = COMBO.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, State> e = it.next();
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p == null) { it.remove(); continue; } // 下线即清
                State st = e.getValue();
                if (st.count > 0 && p.getWorld().getTime() - st.lastHitTick > c.comboTimeoutTicks) {
                    st.count = 0;
                    applyTier(p, st, c);
                    ServerPlayNetworking.send(p, new ComboPayload(0));
                    it.remove();
                }
            }
        });
        Yongye.LOGGER.info("[夜蚀] 连击计数器已挂载");
    }

    /** 每 5 连击一档;档位变化才重挂修饰符。 */
    private static void applyTier(ServerPlayerEntity p, State st, YongyeConfig c) {
        int tier = st.count / 5;
        if (tier == st.tier && st.count != 0) return;
        st.tier = tier;
        double dmg = Math.min(c.comboDamageCap, tier * c.comboDamagePerTier);
        double spd = Math.min(c.comboSpeedCap, tier * c.comboSpeedPerTier);
        set(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_DMG, dmg);
        set(p, EntityAttributes.GENERIC_ATTACK_SPEED, ID_SPD, spd);
    }

    private static void set(ServerPlayerEntity p,
                            net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                            Identifier id, double pct) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (pct > 0) {
            inst.addTemporaryModifier(new EntityAttributeModifier(
                    id, pct, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
