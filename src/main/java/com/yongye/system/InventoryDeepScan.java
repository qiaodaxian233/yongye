package com.yongye.system;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 背包深层扫描(m271):遍历玩家背包里的每一栈物品,并深入一层「容器物品」内部
 * (潜影盒、以及任何用原版 minecraft:container 数据组件存内容的模组收纳品)。
 * 供保护卷被动消耗 / 自动强化卷找材料 / 自动吃书卷找书共用。
 *
 * <p>关于「精妙背包(SophisticatedBackpacks)」的查证结论(m276 需求,已 clone 其 1.21.x 分支核实):
 * ① 它是 NeoForge 端模组,与本 Fabric 模组根本无法同装;② 其背包内容不存在物品本体上,
 * 而是物品只带一个 contentsUuid、内容存于世界数据(BackpackStorage),即使跨加载器也读不到。
 * 因此不做也无法做它的专门支持;本类的原版容器组件路径已覆盖潜影盒与同机制的 Fabric 收纳模组。
 *
 * <p>写回说明:容器内容经 ContainerComponent 重建写回,空位可能被压缩(内容物前移),
 * 不丢物品、只影响摆放,属可接受副作用。
 */
public final class InventoryDeepScan {
    private InventoryDeepScan() {}

    /** 访问器:对每一栈(含容器内的栈)返回要扣除的数量;0=不动。返回 true 则中止后续扫描。 */
    public interface StackVisitor {
        /** @return 从该栈扣除的数量(0=不动,负数按 0 处理) */
        int visit(ItemStack stack);
        /** 扫描是否应当就此打住(如「只吃一本书」)。默认扫完全部。 */
        default boolean done() { return false; }
    }

    /**
     * 扫玩家背包(全部槽位)+ 深入一层容器物品。对每栈调用访问器并按其返回值扣数,
     * 容器内有扣减时重建组件写回。
     */
    public static void scan(ServerPlayerEntity p, StackVisitor visitor) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;

            // ① 本体栈
            int take = Math.max(0, visitor.visit(s));
            if (take > 0) s.decrement(Math.min(take, s.getCount()));
            if (visitor.done()) return;

            // ② 容器物品内部(潜影盒等;待编译验证:ContainerComponent 三符号,见类注释)
            ContainerComponent cc = s.get(DataComponentTypes.CONTAINER);
            if (cc == null) continue;
            List<ItemStack> inner = new ArrayList<>();
            cc.iterateNonEmpty().forEach(st -> inner.add(st.copy()));
            boolean changed = false;
            for (ItemStack in : inner) {
                if (in.isEmpty()) continue;
                int t = Math.max(0, visitor.visit(in));
                if (t > 0) { in.decrement(Math.min(t, in.getCount())); changed = true; }
                if (visitor.done()) break;
            }
            if (changed) s.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(inner));
            if (visitor.done()) return;
        }
    }
}
