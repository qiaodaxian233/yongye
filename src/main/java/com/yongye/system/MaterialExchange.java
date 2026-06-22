package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 材料兑换:10 生命碎片 → 1 生命结晶,10 结晶 → 1 核心,10 核心 → 1 灾变血核。
 * 比例固定 10:1,与四种材料的强化等值(碎片+1 / 结晶+10 / 核心+100 / 血核+1000)一致——
 * 兑换前后等值,不产生强度溢出。直接扫描玩家背包扣料、给产物(背包满则掉落)。
 */
public final class MaterialExchange {
    private MaterialExchange() {}

    /** 固定兑换比例(与材料强化等值绑定,不做成可配以免破坏等值)。 */
    public static final int RATIO = 10;

    private static Item from(int tier) {
        return switch (tier) {
            case 0 -> ModItems.LIFE_SHARD;
            case 1 -> ModItems.LIFE_CRYSTAL;
            case 2 -> ModItems.LIFE_CORE;
            default -> null;
        };
    }

    private static Item to(int tier) {
        return switch (tier) {
            case 0 -> ModItems.LIFE_CRYSTAL;
            case 1 -> ModItems.LIFE_CORE;
            case 2 -> ModItems.CATASTROPHE_BLOOD_CORE;
            default -> null;
        };
    }

    private static String fromName(int tier) {
        return switch (tier) { case 0 -> "生命碎片"; case 1 -> "生命结晶"; case 2 -> "生命核心"; default -> ""; };
    }

    private static String toName(int tier) {
        return switch (tier) { case 0 -> "生命结晶"; case 1 -> "生命核心"; case 2 -> "灾变血核"; default -> ""; };
    }

    /** 执行兑换。all=false 只换一组(10→1);all=true 把能换的全换掉。 */
    public static void exchange(ServerPlayerEntity p, int tier, boolean all) {
        if (!YongyeConfig.get().enableMaterialExchange) return;
        Item fromItem = from(tier);
        Item toItem = to(tier);
        if (fromItem == null || toItem == null) return;

        int have = count(p.getInventory(), fromItem);
        int batches = have / RATIO;
        if (!all) batches = Math.min(batches, 1);

        if (batches <= 0) {
            p.sendMessage(Text.literal(fromName(tier) + "不足:换 1 个" + toName(tier) + "需 " + RATIO
                    + " 个" + fromName(tier) + "(当前 " + have + " 个)").formatted(Formatting.RED), false);
            return;
        }

        remove(p.getInventory(), fromItem, batches * RATIO);
        p.getInventory().offerOrDrop(new ItemStack(toItem, batches));
        p.sendMessage(Text.literal("兑换成功:" + (batches * RATIO) + " " + fromName(tier)
                + " → " + batches + " " + toName(tier)).formatted(Formatting.GREEN), false);
    }

    /** 统计背包内某材料总数。 */
    public static int count(PlayerInventory inv, Item item) {
        int c = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() == item) c += s.getCount();
        }
        return c;
    }

    /** 从背包扣除指定数量的某材料(从前往后逐栈扣)。 */
    private static void remove(PlayerInventory inv, Item item, int amount) {
        int left = amount;
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() == item) {
                int take = Math.min(left, s.getCount());
                s.decrement(take);
                left -= take;
            }
        }
    }
}
