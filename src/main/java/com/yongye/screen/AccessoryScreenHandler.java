package com.yongye.screen;

import com.yongye.item.ArtifactItem;
import com.yongye.registry.ModScreens;
import com.yongye.system.AccessoryStorage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/** 饰品栏容器:4 个只接受神器的饰品槽 + 玩家背包。 */
public class AccessoryScreenHandler extends ScreenHandler {
    private final Inventory acc;

    /** 客户端构造:空容器(随后由服务端同步)。 */
    public AccessoryScreenHandler(int syncId, PlayerInventory playerInv) {
        this(syncId, playerInv, new SimpleInventory(AccessoryStorage.SIZE));
    }

    public AccessoryScreenHandler(int syncId, PlayerInventory playerInv, Inventory acc) {
        super(ModScreens.ACCESSORY, syncId);
        checkSize(acc, AccessoryStorage.SIZE);
        this.acc = acc;
        acc.onOpen(playerInv.player);

        // 10 个饰品槽(2 行 5 列,只接受神器)
        for (int i = 0; i < 10; i++) {
            int col = i % 5;
            int row = i / 5;
            addSlot(new Slot(acc, i, 44 + col * 18, 18 + row * 20) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return stack.getItem() instanceof ArtifactItem;
                }
                @Override
                public int getMaxItemCount() {
                    return 1;
                }
            });
        }
        // 第 11 槽(index 10):鞘翅专用格(原版鞘翅),放右侧
        addSlot(new Slot(acc, 10, 152, 28) {
            @Override
            public boolean canInsert(ItemStack stack) {
                // 原版鞘翅(1.21.1 仍是 ElytraItem):isOf 兜底确保原版鞘翅一定认得,instanceof 兼容模组鞘翅
                return stack.isOf(net.minecraft.item.Items.ELYTRA)
                        || stack.getItem() instanceof net.minecraft.item.ElytraItem;
            }
            @Override
            public int getMaxItemCount() {
                return 1;
            }
        });
        // 玩家背包 3x9
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(playerInv, 9 + r * 9 + c, 8 + c * 18, 70 + r * 18));
            }
        }
        // 快捷栏
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(playerInv, c, 8 + c * 18, 132));
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack original = slot.getStack();
            newStack = original.copy();
            int accEnd = AccessoryStorage.SIZE;
            int invEnd = this.slots.size();
            if (index < accEnd) {
                // 饰品槽 → 背包
                if (!this.insertItem(original, accEnd, invEnd, true)) return ItemStack.EMPTY;
            } else {
                // 背包 → 饰品区:神器进神器槽(0..SIZE-2),鞘翅进鞘翅槽(SIZE-1)
                int wingSlot = AccessoryStorage.SIZE - 1;
                if (original.getItem() instanceof ArtifactItem) {
                    if (!this.insertItem(original, 0, wingSlot, false)) return ItemStack.EMPTY;
                } else if (isWing(original)) {
                    if (!this.insertItem(original, wingSlot, wingSlot + 1, false)) return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY;
                }
            }
            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }

    /** 是否鞘翅(原版鞘翅或模组鞘翅);与翼槽 canInsert 判定一致。 */
    private static boolean isWing(ItemStack stack) {
        return stack.isOf(net.minecraft.item.Items.ELYTRA)
                || stack.getItem() instanceof net.minecraft.item.ElytraItem;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        acc.onClose(player);
        if (!player.getWorld().isClient) {
            AccessoryStorage.save(player, acc);
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
