package com.yongye.item;

import com.yongye.system.ClassManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** 职业书:精英掉落,右键学习对应职业(需达到等级)。 */
public class ClassBookItem extends Item {
    private final PlayerClass type;

    public ClassBookItem(PlayerClass type, Settings settings) {
        super(settings);
        this.type = type;
    }

    public PlayerClass getType() {
        return type;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack book = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(book, true);
        if (user instanceof ServerPlayerEntity sp && ClassManager.learn(sp, type)) {
            if (!sp.isCreative()) book.decrement(1);
            return TypedActionResult.success(book, false);
        }
        return TypedActionResult.fail(book);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.literal("职业书·" + type.cn).formatted(Formatting.AQUA);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType ttype) {
        tooltip.add(Text.translatable("item.yongye.class_book." + type.id + ".desc").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("右键学习(需达到对应等级,最多2职业)").formatted(Formatting.DARK_GRAY));
    }
}
