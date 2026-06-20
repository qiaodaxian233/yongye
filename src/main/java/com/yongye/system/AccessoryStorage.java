package com.yongye.system;

import com.yongye.registry.ModAttachments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;

/** 饰品栏存取:SimpleInventory(4) 与玩家 NBT 附件互转;并提供效果扫描用的列表。 */
public final class AccessoryStorage {
    private AccessoryStorage() {}

    public static final int SIZE = 10;

    /** 从玩家附件载入饰品到一个新的 SimpleInventory(开界面时用)。 */
    public static SimpleInventory load(PlayerEntity p) {
        SimpleInventory inv = new SimpleInventory(SIZE);
        DefaultedList<ItemStack> list = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
        NbtCompound nbt = p.getAttachedOrElse(ModAttachments.ACCESSORIES, new NbtCompound());
        Inventories.readNbt(nbt, list, p.getRegistryManager());
        for (int i = 0; i < SIZE; i++) inv.setStack(i, list.get(i));
        return inv;
    }

    /** 把饰品容器写回玩家附件(关界面时用)。 */
    public static void save(PlayerEntity p, Inventory inv) {
        DefaultedList<ItemStack> list = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) list.set(i, inv.getStack(i));
        NbtCompound nbt = new NbtCompound();
        Inventories.writeNbt(nbt, list, p.getRegistryManager());
        p.setAttached(ModAttachments.ACCESSORIES, nbt);
    }

    /** 效果扫描:返回玩家饰品栏中的物品列表(不开界面,直接读附件)。 */
    public static DefaultedList<ItemStack> stacks(PlayerEntity p) {
        DefaultedList<ItemStack> list = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
        NbtCompound nbt = p.getAttachedOrElse(ModAttachments.ACCESSORIES, new NbtCompound());
        Inventories.readNbt(nbt, list, p.getRegistryManager());
        return list;
    }
}
