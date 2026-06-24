package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.ClassWeaponItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家上层维护(每 tick / 每 5 tick 的轻量循环),负责两件事:
 *
 * 1、重生满血兜底:重生瞬间各种「生命上限」加成(职业/武僧吃材料/神器/强化护甲/携带武器)还没全部重新应用,
 *    此时 setHealth(maxHealth) 只会回到很低的临时上限(比如 60万血只回 200 多)。
 *    解决:重生后开一个 ~2 秒窗口,每 tick 把血量顶到当前 max——随着各系统陆续把生命上限拉满,血量也跟着补满。
 *
 * 2、携带即生效:职业武器的属性(攻击/生命/护甲等)原本用 MAINHAND 槽,只有「拿在主手」才生效,
 *    切到别的物品(挖矿/放方块)加成就没了。这里把「带在背包、但当前没拿主手」的职业武器的属性
 *    镜像到玩家身上(读该武器的 ATTRIBUTE_MODIFIERS 组件,含基础+强化);拿在主手时由原版生效、本镜像撤销,
 *    避免双倍。于是「武器只要在身上就有加成」。
 */
public final class PlayerUpkeepHandler {
    private PlayerUpkeepHandler() {}

    /** 重生满血窗口:uuid -> 剩余 tick。 */
    private static final Map<UUID, Integer> RESPAWN_HEAL = new HashMap<>();
    /** 携带加成已镜像的修饰记录:uuid -> (属性, 派生修饰id),用于下次刷新前撤销。 */
    private static final Map<UUID, List<ModRef>> CARRY_APPLIED = new HashMap<>();

    private record ModRef(RegistryEntry<EntityAttribute> attr, Identifier id) {}

    /** 重生时调用:开 ~2 秒满血窗口。 */
    public static void scheduleRespawnHeal(ServerPlayerEntity p) {
        RESPAWN_HEAL.put(p.getUuid(), 40);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // —— 重生满血窗口:每 tick 处理 —— 
            if (!RESPAWN_HEAL.isEmpty()) {
                var it = RESPAWN_HEAL.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Integer> en = it.next();
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
                    if (p == null) { it.remove(); continue; }
                    p.setHealth(p.getMaxHealth());  // 随 max 一起补满
                    int left = en.getValue() - 1;
                    if (left <= 0) it.remove(); else en.setValue(left);
                }
            }

            // —— 携带武器加成镜像:每 5 tick 处理(切手感知延迟 ≤0.25s,服务端开销低)——
            if (YongyeConfig.get().enableWeaponCarryBonus && (server.getTicks() % 5) == 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    updateCarry(p);
                }
            }
        });
        Yongye.LOGGER.info("[永夜] 玩家上层维护(重生满血 + 携带加成)已挂载");
    }

    /** 撤销上次为该玩家镜像的携带加成。 */
    private static void clearCarry(ServerPlayerEntity p) {
        List<ModRef> list = CARRY_APPLIED.remove(p.getUuid());
        if (list == null) return;
        for (ModRef r : list) {
            EntityAttributeInstance inst = p.getAttributeInstance(r.attr());
            if (inst != null && inst.getModifier(r.id()) != null) inst.removeModifier(r.id());
        }
    }

    private static void updateCarry(ServerPlayerEntity p) {
        clearCarry(p);  // 先撤销上次镜像,保证不重复叠加

        // 主手就是职业武器 → 原版已生效,无需镜像
        if (p.getMainHandStack().getItem() instanceof ClassWeaponItem) return;

        // 在背包里找一把职业武器(取第一把;玩家只有一个本命,且选职只发一把,不会刷叠加)
        ItemStack carried = ItemStack.EMPTY;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() instanceof ClassWeaponItem) { carried = s; break; }
        }
        if (carried.isEmpty()) return;

        AttributeModifiersComponent comp = carried.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (comp == null) return;

        List<ModRef> applied = new ArrayList<>();
        for (AttributeModifiersComponent.Entry e : comp.modifiers()) {
            // 只镜像「主手 / 手 / 任意」槽的修饰(穿戴槽不在此列)
            AttributeModifierSlot slot = e.slot();
            if (slot != AttributeModifierSlot.MAINHAND && slot != AttributeModifierSlot.HAND && slot != AttributeModifierSlot.ANY) continue;
            EntityAttributeInstance inst = p.getAttributeInstance(e.attribute());
            if (inst == null) continue;
            EntityAttributeModifier orig = e.modifier();
            // 派生唯一 id(与原版主手修饰 id 不同,避免拿在手里时双倍)
            Identifier carryId = Identifier.of(Yongye.MOD_ID,
                    "carry_" + orig.id().getNamespace() + "_" + orig.id().getPath());
            if (inst.getModifier(carryId) != null) inst.removeModifier(carryId);
            inst.addTemporaryModifier(new EntityAttributeModifier(carryId, orig.value(), orig.operation()));
            applied.add(new ModRef(e.attribute(), carryId));
        }
        if (!applied.isEmpty()) CARRY_APPLIED.put(p.getUuid(), applied);
    }
}
