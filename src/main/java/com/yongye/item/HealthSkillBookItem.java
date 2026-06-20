package com.yongye.item;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModComponents;
import com.yongye.system.PlayerSkillManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * 血量强化技能书。
 *  - 等级存放在 ItemStack 的 SKILL_LEVEL 组件中(V1 ~ V65535)。
 *  - 右键"学习",将本书等级累加进玩家持久数据,并消耗一本。
 *  - 同级合成升级在 HealthBookCombineRecipe 中实现。
 */
public class HealthSkillBookItem extends Item {

    public HealthSkillBookItem(Settings settings) {
        super(settings);
    }

    /** 生成一本指定等级的技能书。 */
    public static ItemStack create(int level) {
        int clamped = Math.max(1, Math.min(YongyeConfig.get().skillBookMaxLevel, level));
        ItemStack stack = new ItemStack(com.yongye.registry.ModItems.HEALTH_SKILL_BOOK);
        stack.set(ModComponents.SKILL_LEVEL, clamped);
        return stack;
    }

    /** 读取书的等级,缺省视为 1。 */
    public static int getLevel(ItemStack stack) {
        Integer lvl = stack.get(ModComponents.SKILL_LEVEL);
        return lvl == null ? 1 : lvl;
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable("item.yongye.health_skill_book")
                .append(Text.literal(" V" + getLevel(stack)).formatted(Formatting.RED));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack, true);
        }
        if (user instanceof ServerPlayerEntity player) {
            int level = getLevel(stack);
            PlayerSkillManager.LearnResult result = PlayerSkillManager.learnHealth(player, level);
            if (result == PlayerSkillManager.LearnResult.CAPPED) {
                player.sendMessage(Text.translatable("message.yongye.health_capped")
                        .formatted(Formatting.GOLD), true);
                return TypedActionResult.fail(stack);
            }
            stack.decrement(1);
            int total = PlayerSkillManager.getLearnedHealth(player);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
            player.sendMessage(Text.translatable("message.yongye.learned_health",
                    level, total, total * 10L).formatted(Formatting.RED), true);
            return TypedActionResult.success(stack, false);
        }
        return TypedActionResult.pass(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int level = getLevel(stack);
        MutableText effect = Text.translatable("item.yongye.health_skill_book.tooltip.effect", level * 10)
                .formatted(Formatting.RED);
        tooltip.add(effect);
        tooltip.add(Text.translatable("item.yongye.health_skill_book.tooltip.use")
                .formatted(Formatting.DARK_GRAY));
    }
}
