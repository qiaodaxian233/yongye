package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 套装血量加成(文档 12.1)。
 * 穿齐同材质整套盔甲(头/胸/腿/脚)时给予额外最大生命;混搭不触发。
 * 每 10 tick 校验一次,数值变化时才更新修饰符,避免反复刷新导致血量抖动。
 */
public final class ArmorHealthHandler {
    private ArmorHealthHandler() {}

    private static final Identifier ID_ARMOR_HEALTH = Identifier.of(Yongye.MOD_ID, "armor_health");
    private static final Map<UUID, Double> LAST_BONUS = new WeakHashMap<>();
    private static int tickCounter = 0;

    private static final Set<Item> LEATHER = Set.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
    private static final Set<Item> CHAIN = Set.of(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
    private static final Set<Item> IRON = Set.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
    private static final Set<Item> GOLD = Set.of(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
    private static final Set<Item> DIAMOND = Set.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
    private static final Set<Item> NETHERITE = Set.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!YongyeConfig.get().enableArmorHealthBonus) return;
            if (++tickCounter < 10) return;
            tickCounter = 0;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                updatePlayer(player);
            }
        });
        Yongye.LOGGER.info("[永夜] 套装血量系统已挂载");
    }

    private static void updatePlayer(ServerPlayerEntity player) {
        double bonus = computeSetBonus(player);
        Double last = LAST_BONUS.get(player.getUuid());
        if (last != null && last == bonus) return; // 无变化

        EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (inst == null) return;
        inst.removeModifier(ID_ARMOR_HEALTH);
        if (bonus > 0) {
            inst.addTemporaryModifier(new EntityAttributeModifier(
                    ID_ARMOR_HEALTH, bonus, EntityAttributeModifier.Operation.ADD_VALUE));
        }
        LAST_BONUS.put(player.getUuid(), bonus);
    }

    private static double computeSetBonus(ServerPlayerEntity player) {
        Item head = player.getEquippedStack(EquipmentSlot.HEAD).getItem();
        Item chest = player.getEquippedStack(EquipmentSlot.CHEST).getItem();
        Item legs = player.getEquippedStack(EquipmentSlot.LEGS).getItem();
        Item feet = player.getEquippedStack(EquipmentSlot.FEET).getItem();
        YongyeConfig cfg = YongyeConfig.get();

        if (fullSet(LEATHER, head, chest, legs, feet)) return cfg.setBonusLeather;
        if (fullSet(CHAIN, head, chest, legs, feet)) return cfg.setBonusChain;
        if (fullSet(IRON, head, chest, legs, feet)) return cfg.setBonusIron;
        if (fullSet(GOLD, head, chest, legs, feet)) return cfg.setBonusGold;
        if (fullSet(DIAMOND, head, chest, legs, feet)) return cfg.setBonusDiamond;
        if (fullSet(NETHERITE, head, chest, legs, feet)) return cfg.setBonusNetherite;
        return 0;
    }

    private static boolean fullSet(Set<Item> set, Item head, Item chest, Item legs, Item feet) {
        return set.contains(head) && set.contains(chest) && set.contains(legs) && set.contains(feet);
    }
}
