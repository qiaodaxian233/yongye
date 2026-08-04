package com.yongye.item;

import com.yongye.system.EquipmentEnhancer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.function.Supplier;

/**
 * m453 召唤师彻底移除 · 旧物自动转换壳:
 * 直接反注册 class_weapon_summoner / class_book_summoner 会让老档背包里的旧物
 * 在读档时变成空气——重强化的「鹰扬」可能带着玩家几千级的投入,不能silently蒸发。
 * 本壳占住旧 id,物品一进玩家背包(拾起/开箱取出/读档)服务端 inventoryTick 内
 * 立刻转换为剑客对应物:
 *  - 武器:新建「流光」,附魔与自定义名照搬,强化等级走 EquipmentEnhancer.withLevel
 *    按**目标武器自己的基础值**重算(m337 强化转移同口径;直接整组件拷贝会把鹰扬形状的
 *    属性错贴到剑上——踩坑清单第 3 条,基础值必须从 baseOf(目标) 起算);
 *  - 职业书:转剑客职业书(数量保留)。
 * 不进创造栏、不进任何掉落池、无合成配方——玩法层面已不存在,只作存档摆渡;
 * 待一两个版本后老档消化完,连壳带 legacy 贴图一并删净。
 */
public class LegacySummonerItem extends Item {
    private final Supplier<Item> target;
    private final boolean carryEnhance;

    public LegacySummonerItem(Supplier<Item> target, boolean carryEnhance, Settings settings) {
        super(settings);
        this.target = target;
        this.carryEnhance = carryEnhance;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof ServerPlayerEntity p)) return;   // BlightArmorItem 同款门(在树先例)
        Item to = target.get();
        if (to == null) return;   // 防御:目标未注册时按兵不动,下 tick 再试
        ItemStack fresh = new ItemStack(to, Math.max(1, stack.getCount()));
        ItemStack out = fresh;
        if (carryEnhance) {
            fresh.setCount(1);   // 武器不可堆叠
            var ench = stack.get(DataComponentTypes.ENCHANTMENTS);
            if (ench != null) fresh.set(DataComponentTypes.ENCHANTMENTS, ench);
            var name = stack.get(DataComponentTypes.CUSTOM_NAME);
            if (name != null) fresh.set(DataComponentTypes.CUSTOM_NAME, name);
            int lvl = EquipmentEnhancer.getLevel(stack);
            out = lvl > 0 ? EquipmentEnhancer.withLevel(fresh, lvl) : fresh;
        }
        p.getInventory().setStack(slot, out);
        p.sendMessage(Text.literal("召唤师职业已从模组移除,旧物已自动转换:" + out.getName().getString())
                .formatted(Formatting.GOLD), false);
    }
}
