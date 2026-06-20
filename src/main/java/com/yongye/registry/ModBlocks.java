package com.yongye.registry;

import com.yongye.Yongye;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 方块注册。
 * 灾厄核心:会在世界生成、持续刷精英的危险方块,摧毁后掉裂界残片等。逻辑见 CatastropheCoreManager。
 */
public final class ModBlocks {
    private ModBlocks() {}

    public static final Block CATASTROPHE_CORE = register("catastrophe_core",
            new Block(AbstractBlock.Settings.create()
                    .strength(6.0f)              // 有点硬,摧毁需要时间(配合周围精英形成压力)
                    .luminance(s -> 12)          // 自发光,夜里可见
                    .dropsNothing()));           // 不掉自身,奖励由管理器发放

    private static Block register(String name, Block block) {
        Block b = Registry.register(Registries.BLOCK, Identifier.of(Yongye.MOD_ID, name), block);
        Registry.register(Registries.ITEM, Identifier.of(Yongye.MOD_ID, name),
                new BlockItem(b, new Item.Settings()));
        return b;
    }

    public static void init() {
        Yongye.LOGGER.info("[亡途荒夜] 方块已注册");
    }
}
