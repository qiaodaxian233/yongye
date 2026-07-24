package com.yongye.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;
/**
 * 强化石(m294,作者供图十档"眼球"渐变,一图一档):
 * 面值 = 10^(档-1):1 / 10 / 100 / 1000 / 1万 / 10万 / 100万 / 1000万 / 1亿 / 10亿。
 * 一颗 = 直接 +面值 强化等级——**必得、不走失败/碎裂 RNG**(口径同工作台 addLevels 与
 * 强化继承 m236 的先例:"等级本就是挣来的,不再赌一次");传统材料(碎片/结晶/核心/血核)
 * 仍走逐级 RNG 老规矩,两条线并存。
 * 封顶 10 档 = 10 亿:等级存 int(上限 21.4 亿,m293 已加固),两颗 10 亿石多一点即摸顶,
 * 是"整颗都有效"的最大档——不做 100 亿(会有七成八被 int 顶蒸发)。
 */
public class EnhanceStoneItem extends Item {

    /** 档位 1~10。 */
    public final int tier;

    public EnhanceStoneItem(int tier, Settings settings) {
        super(settings);
        this.tier = tier;
    }

    /** 面值:10^(tier-1)。long 计算后钳 int(10 档 = 10 亿,本身在 int 内,防御性钳)。 */
    public static int valueOf(int tier) {
        long v = 1L;
        for (int i = 1; i < tier; i++) v *= 10L;
        return (int) Math.min(Integer.MAX_VALUE, v);
    }

    /** 中文单位紧凑显示:整万显示 N万、整亿显示 N亿,其余原样。 */
    public static String cn(long v) {
        if (v >= 100_000_000L && v % 100_000_000L == 0) return (v / 100_000_000L) + "亿";
        if (v >= 10_000L && v % 10_000L == 0) return (v / 10_000L) + "万";
        return String.valueOf(v);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("强化材料:一颗 = +" + cn(valueOf(tier)) + " 级(必得,不碎)")
                .formatted(Formatting.GRAY));
        if (tier < 10) {
            tooltip.add(Text.literal("右键:整叠向上并——每 10 颗合成 1 颗上一档").formatted(Formatting.DARK_GRAY));
        }
        tooltip.add(Text.literal("第 " + tier + "/10 档").formatted(Formatting.DARK_GRAY));
    }

    /**
     * m295 十合一向上合成:右键把手上整叠一次并完——每 10 颗换 1 颗上一档,余数留在手里
     * (面值 10 颗=上一档 1 颗,严格等值,小石头永远不废、顺手治爆背包)。10 档到顶不可再并。
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack, true);
        }
        if (user instanceof ServerPlayerEntity player) {
            if (tier >= 10) {
                player.sendMessage(Text.literal("已是最高档强化石,不可再合成").formatted(Formatting.YELLOW), true);
                return TypedActionResult.pass(stack);
            }
            int count = stack.getCount();
            int up = count / 10;
            if (up <= 0) {
                player.sendMessage(Text.literal("需要 10 颗同档强化石才能合成上一档(当前 " + count + " 颗)")
                        .formatted(Formatting.YELLOW), true);
                return TypedActionResult.pass(stack);
            }
            int remain = count - up * 10;
            stack.setCount(remain); // 余数留在手里(0 = 清空)
            ItemStack made = new ItemStack(com.yongye.registry.ModItems.enhanceStone(tier + 1), up);
            Text madeName = made.getName();
            if (!player.getInventory().insertStack(made) && !made.isEmpty()) {
                player.dropItem(made, false); // 背包满则掉脚下(灵魂绑定同款兜底)
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.7f, 1.5f);
            player.sendMessage(Text.literal("强化石合成:" + (up * 10) + " 颗 → " + up + " 颗 ")
                    .formatted(Formatting.GOLD).append(madeName.copy().formatted(Formatting.LIGHT_PURPLE)), true);
            return TypedActionResult.success(stack, false);
        }
        return TypedActionResult.pass(stack);
    }
}
