package com.yongye.item;

import com.yongye.network.OpenClassSelectPayload;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

/**
 * 职业选择书:右键打开「本命职业选择」界面(全职业卡图,ClassSelectScreen),点选即定本命职业、出生即生效。
 * 取代旧的「登录强制弹窗」——现在玩家可以先熟悉环境,准备好了再用这本书选职业。
 *
 * 流程:右键 → (未选过本命才)服务端发 S2C {@link OpenClassSelectPayload} → 客户端开界面
 *      → 点击 → C2S ChooseClassPayload → 服务端 ClassManager.chooseStartingClass(并消耗本书)。
 */
public class ClassSelectBookItem extends Item {
    public ClassSelectBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack book = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(book, true);
        if (user instanceof ServerPlayerEntity sp) {
            // 已选过本命职业(或已有职业):本书失效,不再开界面
            if (sp.getAttachedOrElse(ModAttachments.STARTING_CLASS_CHOSEN, false)
                    || !com.yongye.system.ClassManager.learnedList(sp).isEmpty()) {
                sp.sendMessage(Text.literal("你已选择本命职业,选择书已失效。").formatted(Formatting.GRAY), true);
                return TypedActionResult.fail(book);
            }
            ServerPlayNetworking.send(sp, new OpenClassSelectPayload());
        }
        return TypedActionResult.success(book, false);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.literal("职业选择书").formatted(Formatting.AQUA);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("右键:打开本命职业选择").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("选定后出生即生效、不可更改;第二职业日后用职业书习得").formatted(Formatting.DARK_GRAY));
    }
}
