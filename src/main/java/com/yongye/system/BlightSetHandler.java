package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.BlightArmorItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 夜蚀共鸣(m272,实机反馈「夜蚀装备属性太低」):
 * 穿戴夜蚀盔甲的每一件都给百分比加成——生命 +blightSetHpPct、攻击 +blightSetAtkPct(乘基础值,
 * 跟着玩家成长曲线走,后期不缩水);集齐 4 件额外 +blightSetSpeedPct 移速。
 * 每秒重算一次,属性走 addTemporaryModifier(不持久化,脱下即消),写法照抄 ArtifactManager。
 */
public final class BlightSetHandler {
    private BlightSetHandler() {}

    private static final Identifier ID_HP    = Identifier.of(Yongye.MOD_ID, "blight_set_hp");
    private static final Identifier ID_ATK   = Identifier.of(Yongye.MOD_ID, "blight_set_atk");
    private static final Identifier ID_SPEED = Identifier.of(Yongye.MOD_ID, "blight_set_speed");
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            YongyeConfig c = YongyeConfig.get();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                int pieces = 0;
                for (EquipmentSlot s : ARMOR) {
                    if (p.getEquippedStack(s).getItem() instanceof BlightArmorItem) pieces++;
                }
                apply(p, EntityAttributes.GENERIC_MAX_HEALTH, ID_HP, pieces * c.blightSetHpPct);
                apply(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_ATK, pieces * c.blightSetAtkPct);
                apply(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, ID_SPEED,
                        pieces >= 4 ? c.blightSetSpeedPct : 0);
            }
        });
        Yongye.LOGGER.info("[夜蚀] 夜蚀共鸣(套装加成)已挂载");
    }

    private static void apply(ServerPlayerEntity p,
                              net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                              Identifier id, double pct) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        EntityAttributeModifier old = inst.getModifier(id);
        if (old != null && Math.abs(old.value() - pct) < 1e-9) return; // 值没变,不重挂(生命上限重挂会闪血条)
        inst.removeModifier(id);
        if (pct > 0) {
            inst.addTemporaryModifier(new EntityAttributeModifier(
                    id, pct, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}
