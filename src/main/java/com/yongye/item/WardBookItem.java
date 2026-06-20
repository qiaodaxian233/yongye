package com.yongye.item;

import com.yongye.registry.ModComponents;
import com.yongye.system.EquipmentEnhancer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * 守护附魔书:一只手拿武器、另一只手拿本书右键,为该武器附上「无法被夺取」(精英缴械会跳过)。
 * 推荐:守护书放主手、武器放副手,右键即可(避免武器自身右键吞掉操作)。
 */
public class WardBookItem extends Item {
    public WardBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack book = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(book, true);

        Hand other = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack weapon = user.getStackInHand(other);
        if (weapon.isEmpty() || !EquipmentEnhancer.isWeapon(weapon)) {
            user.sendMessage(Text.literal("请另一只手拿着要保护的武器,再右键守护书").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(book);
        }
        if (weapon.getOrDefault(ModComponents.DISARM_PROOF, false)) {
            user.sendMessage(Text.literal("该武器已无法被夺取").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(book);
        }
        weapon.set(ModComponents.DISARM_PROOF, true);
        book.decrement(1);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        user.sendMessage(Text.literal("已为武器附上守护:无法被精英夺取").formatted(Formatting.LIGHT_PURPLE), true);
        return TypedActionResult.success(book, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.yongye.ward_book.desc").formatted(Formatting.LIGHT_PURPLE));
    }
}
