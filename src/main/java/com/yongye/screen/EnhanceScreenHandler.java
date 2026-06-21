package com.yongye.screen;

import com.yongye.registry.ModScreens;
import com.yongye.system.EquipmentEnhancer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * 武器强化窗口:1 个装备槽(槽 0) + 1 个材料槽(槽 1) + 玩家背包。
 * 点「升级」后:升级级数 = 材料数量 × 该材料单值(碎片×1 / 结晶×10 / 核心×100 / 血核×1000),
 * 即「放进去多少个生命碎片就升多少级」;材料整组消耗。装备槽接受任意可强化装备(武器/盔甲/攻防双修)。
 * 容器为临时容器(不持久化),关闭时槽内物品归还玩家,避免丢失。
 * 写法照搬 AccessoryScreenHandler(自定义槽 + canInsert + quickMove + onClosed 归还)。
 */
public class EnhanceScreenHandler extends ScreenHandler {
    public static final int EQUIP_SLOT = 0;
    public static final int MAT_SLOT = 1;
    private static final int INPUT_SIZE = 2;

    private final Inventory input = new SimpleInventory(INPUT_SIZE);

    public EnhanceScreenHandler(int syncId, PlayerInventory playerInv) {
        super(ModScreens.ENHANCE, syncId);

        // 装备槽:只收可强化装备,最多 1 件
        addSlot(new Slot(input, EQUIP_SLOT, 44, 37) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return EquipmentEnhancer.isEnhanceable(stack.getItem());
            }
            @Override
            public int getMaxItemCount() {
                return 1;
            }
        });
        // 材料槽:只收强化材料,可整组(默认上限 64)
        addSlot(new Slot(input, MAT_SLOT, 94, 37) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return EquipmentEnhancer.isMaterial(stack.getItem());
            }
        });

        // 玩家背包 3x9
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(playerInv, 9 + r * 9 + c, 8 + c * 18, 84 + r * 18));
            }
        }
        // 快捷栏
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(playerInv, c, 8 + c * 18, 142));
        }
    }

    /** 预览用:当前材料能升多少级(0 表示当前不可升)。客户端/服务端通用。 */
    public int previewLevels() {
        ItemStack equip = input.getStack(EQUIP_SLOT);
        ItemStack mat = input.getStack(MAT_SLOT);
        if (equip.isEmpty() || !EquipmentEnhancer.isEnhanceable(equip.getItem())) return 0;
        if (mat.isEmpty() || !EquipmentEnhancer.isMaterial(mat.getItem())) return 0;
        return mat.getCount() * EquipmentEnhancer.materialValue(mat.getItem());
    }

    /** 预览用:装备槽里装备的当前强化等级。 */
    public int currentLevel() {
        return EquipmentEnhancer.getLevel(input.getStack(EQUIP_SLOT));
    }

    /** 服务端执行升级:消耗全部材料,按 数量×单值 给装备加等级。 */
    public void applyUpgrade(PlayerEntity player) {
        if (player.getWorld().isClient) return;
        ItemStack equip = input.getStack(EQUIP_SLOT);
        ItemStack mat = input.getStack(MAT_SLOT);
        if (equip.isEmpty() || !EquipmentEnhancer.isEnhanceable(equip.getItem())) return;
        if (mat.isEmpty() || !EquipmentEnhancer.isMaterial(mat.getItem())) return;
        int add = mat.getCount() * EquipmentEnhancer.materialValue(mat.getItem());
        if (add <= 0) return;
        ItemStack upgraded = EquipmentEnhancer.addLevels(equip, add);
        input.setStack(EQUIP_SLOT, upgraded);
        input.setStack(MAT_SLOT, ItemStack.EMPTY); // 整组材料全部消耗
        sendContentUpdates();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack original = slot.getStack();
            newStack = original.copy();
            int invEnd = this.slots.size();
            if (index < INPUT_SIZE) {
                // 输入槽 → 背包
                if (!this.insertItem(original, INPUT_SIZE, invEnd, true)) return ItemStack.EMPTY;
            } else {
                // 背包 → 对应输入槽
                if (EquipmentEnhancer.isEnhanceable(original.getItem())) {
                    if (!this.insertItem(original, EQUIP_SLOT, EQUIP_SLOT + 1, false)) return ItemStack.EMPTY;
                } else if (EquipmentEnhancer.isMaterial(original.getItem())) {
                    if (!this.insertItem(original, MAT_SLOT, MAT_SLOT + 1, false)) return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY;
                }
            }
            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!player.getWorld().isClient) {
            // 临时容器:关闭时把装备槽/材料槽里的东西还给玩家(塞背包,满则掉地)
            for (int i = 0; i < input.size(); i++) {
                ItemStack s = input.getStack(i);
                if (!s.isEmpty()) {
                    player.getInventory().offerOrDrop(s);
                    input.setStack(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
