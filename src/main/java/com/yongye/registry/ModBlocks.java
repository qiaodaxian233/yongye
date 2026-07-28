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

    /** 蚀矿(m264):只在夜蚀群系(被侵蚀的土地)生成/生长,钻石镐起挖,熔炼得蚀锭。
     *  微光=矿脉里的紫纹在发光;requiresTool=徒手/低级镐挖了不掉。 */
    public static final Block BLIGHT_ORE = register("blight_ore",
            new Block(AbstractBlock.Settings.create()
                    .strength(45.0f, 1200.0f)   // m338:比远古残骸(30)还硬,钻石镐(tag 已挂)慢慢啃;抗爆同残骸
                    .requiresTool()
                    .luminance(s -> 5)));

    /** m305 烛块(作者供图):搭地狱门形状,打火石点燃 → 烛之维度。自发光紫纹。 */
    public static final Block CANDLE_BLOCK = register("candle_block",
            new Block(AbstractBlock.Settings.create()
                    .strength(2.0f)
                    .luminance(s -> 9)));

    /** m305 烛焰之门(门内芯):不可获得、无碰撞、自发光;逻辑见 CandlePortalBlock/CandleDimension。 */
    public static final Block CANDLE_PORTAL = registerBlockOnly("candle_portal",
            new com.yongye.block.CandlePortalBlock(AbstractBlock.Settings.create()
                    .strength(-1.0f)
                    .luminance(s -> 11)
                    .dropsNothing()
                    .noCollision()
                    .nonOpaque()));

    /** 只注册方块不发 BlockItem(门芯这类不可获得方块用)。 */
    private static Block registerBlockOnly(String name, Block block) {
        return Registry.register(Registries.BLOCK, Identifier.of(Yongye.MOD_ID, name), block);
    }

    private static Block register(String name, Block block) {
        Block b = Registry.register(Registries.BLOCK, Identifier.of(Yongye.MOD_ID, name), block);
        Registry.register(Registries.ITEM, Identifier.of(Yongye.MOD_ID, name),
                new BlockItem(b, new Item.Settings()));
        return b;
    }

    public static void init() {
        Yongye.LOGGER.info("[夜蚀] 方块已注册");
    }
}
