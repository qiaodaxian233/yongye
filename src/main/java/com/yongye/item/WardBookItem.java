package com.yongye.item;

import com.yongye.network.OpenWardPayload;
import com.yongye.registry.ModComponents;
import com.yongye.registry.ModItems;
import com.yongye.system.EquipmentEnhancer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
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
 * 守护附魔书:右键打开「守护」界面(WardScreen),界面自动列出背包里所有可守护装备
 * (武器/盔甲/盾牌,原版与永夜物品皆可),点击某件即为其附上「无法被夺取」(精英缴械会跳过),消耗 1 本书。
 *
 * 流程:右键 → 服务端发 S2C {@link OpenWardPayload} → 客户端开界面 → 点击 → C2S
 * {@code WardApplyPayload(槽位)} → 服务端 {@link #applyWard} 校验并施加。
 */
public class WardBookItem extends Item {
    public WardBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack book = user.getStackInHand(hand);
        // 客户端只负责挥手动画;真正开界面由服务端发 S2C 包(避免在通用代码里引用客户端类)
        if (world.isClient) return TypedActionResult.success(book, true);
        if (user instanceof ServerPlayerEntity sp) {
            ServerPlayNetworking.send(sp, new OpenWardPayload());
        }
        return TypedActionResult.success(book, false);
    }

    /**
     * 服务端:对玩家背包第 {@code slot} 槽的装备施加守护(由 WardApplyPayload 接收器调用)。
     * 重新做全部校验(界面是客户端的,不可信):槽位合法 + 是可守护装备 + 未守护 + 背包内有守护书。
     */
    public static void applyWard(ServerPlayerEntity player, int slot) {
        PlayerInventory inv = player.getInventory();
        if (slot < 0 || slot >= inv.size()) return;
        ItemStack target = inv.getStack(slot);
        if (target.isEmpty() || !EquipmentEnhancer.isWardable(target)) {
            player.sendMessage(Text.literal("这件不能附守护(仅武器/护甲/盾牌)").formatted(Formatting.GRAY), true);
            return;
        }
        if (target.getOrDefault(ModComponents.DISARM_PROOF, false)) {
            player.sendMessage(Text.literal("该装备已无法被夺取").formatted(Formatting.GRAY), true);
            return;
        }
        // 背包内找一本守护书扣掉(界面虽由手持书打开,但点击时玩家可能已切换,故按背包查找更稳)
        int bookSlot = findWardBook(inv);
        if (bookSlot < 0) {
            player.sendMessage(Text.literal("没有守护书了").formatted(Formatting.GRAY), true);
            return;
        }
        target.set(ModComponents.DISARM_PROOF, true);
        inv.getStack(bookSlot).decrement(1);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        player.sendMessage(Text.literal("已为「" + target.getName().getString() + "」附上守护:无法被精英夺取")
                .formatted(Formatting.LIGHT_PURPLE), true);
    }

    /** 返回背包内第一本守护书的槽位;没有返回 -1。 */
    private static int findWardBook(PlayerInventory inv) {
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).getItem() == ModItems.WARD_BOOK) return i;
        }
        return -1;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.yongye.ward_book.desc").formatted(Formatting.LIGHT_PURPLE));
    }
}
