package com.yongye.item;

import com.yongye.registry.ModAttachments;
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
 * 强化保护卷(m159):右键使用,使「下一次会令装备碎裂的强化失败」被抵挡(装备不会损坏)。
 * 标志挂在玩家身上(ENHANCE_PROTECTED),保护卷只在等级 ≥ enhanceBreakLevel 的失败碎裂判定时消耗。
 * 无法合成,仅靠怪物低概率掉落 + 杀怪自动兑换获取(见 ProtectScrollHandler)。
 */
public class EnhanceProtectScrollItem extends Item {
    public EnhanceProtectScrollItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        // m271:全被动化——不再需要右键激活,放在背包里即自动生效(碎裂瞬间自动扣一张抵挡)。
        // 右键只做说明,不消耗;旧存档里已手动激活的护盾(ENHANCE_PROTECTED)仍被强化器优先认。
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack, true);
        if (user instanceof ServerPlayerEntity sp) {
            sp.getWorld().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.6f, 1.5f);
            sp.sendMessage(Text.literal("保护卷是被动生效的:放在背包里即可,强化碎裂时会自动消耗抵挡。")
                    .formatted(Formatting.LIGHT_PURPLE), true);
        }
        return TypedActionResult.success(stack, false);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.literal("强化保护卷").formatted(Formatting.LIGHT_PURPLE);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("被动生效:放在背包里,强化碎裂时自动消耗抵挡,装备不会损坏。").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("稀有物品 · 无法合成,仅怪物掉落 / 杀怪兑换").formatted(Formatting.DARK_GRAY));
    }
}
