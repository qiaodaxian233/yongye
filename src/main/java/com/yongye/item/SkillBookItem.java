package com.yongye.item;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModComponents;
import com.yongye.registry.ModItems;
import com.yongye.system.SkillEffectManager;
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
 * 通用技能书(护甲/恢复/闪避/反伤/抗性)。等级存 SKILL_LEVEL 组件,右键学习累加进玩家数据,
 * 同级合成在 SkillBookCombineRecipe。
 */
public class SkillBookItem extends Item {

    private final SkillType type;

    public SkillBookItem(SkillType type, Settings settings) {
        super(settings);
        this.type = type;
    }

    public SkillType getType() {
        return type;
    }

    public static ItemStack create(SkillType type, int level) {
        int clamped = Math.max(1, Math.min(YongyeConfig.get().skillBookMaxLevel, level));
        ItemStack stack = new ItemStack(ModItems.getSkillBook(type));
        stack.set(ModComponents.SKILL_LEVEL, clamped);
        return stack;
    }

    public static int getLevel(ItemStack stack) {
        Integer lvl = stack.get(ModComponents.SKILL_LEVEL);
        return lvl == null ? 1 : lvl;
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable("item.yongye.skill_book." + type.id)
                .append(Text.literal(" V" + getLevel(stack)).formatted(Formatting.AQUA));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack, true);
        }
        if (user instanceof ServerPlayerEntity player) {
            int level = getLevel(stack);
            SkillEffectManager.LearnResult result = SkillEffectManager.learn(player, type, level);
            if (result == SkillEffectManager.LearnResult.CAPPED) {
                player.sendMessage(Text.literal("该技能已达上限 V" + YongyeConfig.get().skillBookMaxLevel)
                        .formatted(Formatting.GOLD), true);
                return TypedActionResult.fail(stack);
            }
            stack.decrement(1);
            int total = SkillEffectManager.getLearnedLevel(player, type);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
            player.sendMessage(Text.translatable("item.yongye.skill_book." + type.id)
                    .append(Text.literal(" 已提升至 V" + total).formatted(Formatting.AQUA)), true);
            return TypedActionResult.success(stack, false);
        }
        return TypedActionResult.pass(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType ttype) {
        tooltip.add(Text.translatable("item.yongye.skill_book." + type.id + ".desc").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("item.yongye.health_skill_book.tooltip.use").formatted(Formatting.DARK_GRAY));
    }
}
