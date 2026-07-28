package com.yongye.item;

import com.yongye.network.OpenQuestBookPayload;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** m328:任务书——右键打开主线任务界面(FTB Quests 风格内建版);进度存玩家身上,书丢了不丢进度。 */
public class QuestBookItem extends Item {
    public QuestBookItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity sp) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, new OpenQuestBookPayload());
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
        tooltip.add(Text.literal("永夜主线:从活过第一夜,到讨伐末影龙").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("右键打开(背包「任务」按钮同款)").formatted(Formatting.DARK_GRAY));
    }
}
