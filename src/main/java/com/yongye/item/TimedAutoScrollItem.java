package com.yongye.item;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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
 * 限时自动卷轴(m276):右键激活一段时间的自动化效果,战斗中不用停手开背包。
 *   AUTO_ENHANCE(自动强化卷):生效期间每隔几秒,自动吞掉背包(含潜影盒)里的全部强化材料,
 *     强化当前主手武器(没拿则强化等级最低的一件身上盔甲);保护卷(m271 被动)照常兜底碎裂。
 *   AUTO_BOOK(自动吃书卷):生效期间每隔一两秒,自动研读背包(含潜影盒)里的一本
 *     技能书/血量书(满级的跳过)。
 * 时长 autoScrollDurationTicks(默 60 秒),重复使用叠时长、封顶 5 分钟;
 * 到期由 AutoScrollHandler 播报。杀怪低概率掉落 + 任务奖励可出(见 ProtectScrollHandler / QuestManager)。
 */
public class TimedAutoScrollItem extends Item {

    public enum Kind { AUTO_ENHANCE, AUTO_BOOK }

    /** 叠加时长封顶(tick,6000=5 分钟)。 */
    public static final int MAX_STACK_TICKS = 6000;

    private final Kind kind;

    public TimedAutoScrollItem(Kind kind, Settings settings) {
        super(settings);
        this.kind = kind;
    }

    public Kind getKind() { return kind; }

    private AttachmentType<Long> attach() {
        return kind == Kind.AUTO_ENHANCE ? ModAttachments.AUTO_ENHANCE_UNTIL : ModAttachments.AUTO_BOOK_UNTIL;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack, true);
        if (user instanceof ServerPlayerEntity sp) {
            long now = world.getTime();
            long cur = sp.getAttachedOrElse(attach(), 0L);
            long until = Math.min(now + MAX_STACK_TICKS,
                    Math.max(now, cur) + YongyeConfig.get().autoScrollDurationTicks);
            sp.setAttached(attach(), until);
            stack.decrement(1);
            sp.getWorld().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.9f, 1.2f);
            long secs = (until - now) / 20;
            sp.sendMessage(Text.literal((kind == Kind.AUTO_ENHANCE ? "自动强化" : "自动研读")
                            + "已启动:" + secs + " 秒内全自动进行,专心战斗吧!")
                    .formatted(Formatting.GOLD), true);
        }
        return TypedActionResult.success(stack, false);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.literal(kind == Kind.AUTO_ENHANCE ? "自动强化卷" : "自动吃书卷")
                .formatted(Formatting.GOLD);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int secs = YongyeConfig.get().autoScrollDurationTicks / 20;
        if (kind == Kind.AUTO_ENHANCE) {
            tooltip.add(Text.literal("使用后 " + secs + " 秒内:自动吞掉背包里的强化材料,")
                    .formatted(Formatting.GRAY));
            tooltip.add(Text.literal("强化主手武器(空手则强化最低级的身上盔甲)。")
                    .formatted(Formatting.GRAY));
        } else {
            tooltip.add(Text.literal("使用后 " + secs + " 秒内:自动研读背包里的技能书/血量书。")
                    .formatted(Formatting.GRAY));
        }
        tooltip.add(Text.literal("潜影盒里的材料/书也会被搜到 · 重复使用叠时长(封顶 5 分钟)")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("怪物低概率掉落 / 任务奖励").formatted(Formatting.DARK_GRAY));
    }
}
