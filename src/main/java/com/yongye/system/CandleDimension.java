package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.block.CandlePortalBlock;
import com.yongye.registry.ModBlocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * m305 烛之维度(作者供图「烛块」):烛块搭地狱门形状(内空 2×3 ~ 21×21)+ 打火石点燃 →
 * 生成烛焰之门,走进去传送 主世界 ↔ yongye:candle(1:1 坐标)。维度=紫天猎场
 * (数据包三件套 dimension_type/dimension/biome,固定正午 + 紫色天空/雾),
 * 刷怪走 CandleSpawnHandler(百倍节奏 + 实体闸)。
 *
 * 到达侧若 24 格内没有现成门,自动搭一扇 4×5 烛块门并点好,保证能回来。
 * 非玩家实体不传送(有意:猎场怪涌回主世界会炸档)。
 */
public final class CandleDimension {
    private CandleDimension() {}

    public static final RegistryKey<World> WORLD_KEY =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of(Yongye.MOD_ID, "candle"));

    /** 传送冷却(防门内反复触发来回弹)。 */
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final long COOLDOWN_TICKS = 80;

    /** 门框材质判定(烛块)。 */
    public static boolean isFrameBlock(BlockState state) {
        return state.isOf(ModBlocks.CANDLE_BLOCK);
    }

    private static boolean isPortal(BlockState state) {
        return state.isOf(ModBlocks.CANDLE_PORTAL);
    }

    public static void register() {
        // 打火石右键烛块 → 尝试在点击面外侧的空位点门
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!YongyeConfig.get().enableCandleDimension) return ActionResult.PASS;
            if (!player.getStackInHand(hand).isOf(Items.FLINT_AND_STEEL)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
            if (!isFrameBlock(sw.getBlockState(hit.getBlockPos()))) return ActionResult.PASS;
            BlockPos inside = hit.getBlockPos().offset(hit.getSide());
            if (!sw.getBlockState(inside).isAir()) return ActionResult.PASS;
            if (tryLightPortal(sw, inside)) {
                sw.playSound(null, inside.getX(), inside.getY(), inside.getZ(),
                        SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0f, 0.8f);
                if (player instanceof ServerPlayerEntity sp) {
                    sp.sendMessage(Text.literal("烛焰之门被点燃了…").formatted(Formatting.LIGHT_PURPLE), true);
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        Yongye.LOGGER.info("[夜蚀] 烛之维度已挂载(烛块门/传送)");
    }

    /**
     * 从门内一格空气出发,按 X / Z 两个朝向各试一次矩形门框匹配:
     * 内空宽 2~21、高 3~21,四边全为烛块 → 内空整片填门块。
     */
    public static boolean tryLightPortal(ServerWorld world, BlockPos inside) {
        return tryAxis(world, inside, Direction.Axis.X) || tryAxis(world, inside, Direction.Axis.Z);
    }

    private static boolean tryAxis(ServerWorld world, BlockPos inside, Direction.Axis axis) {
        Direction left = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        Direction right = left.getOpposite();
        // 沉底:找内空最底行
        BlockPos bottom = inside;
        while (bottom.getY() > world.getBottomY() && world.getBlockState(bottom.down()).isAir()
                && inside.getY() - bottom.getY() < 21) {
            bottom = bottom.down();
        }
        if (!isFrameBlock(world.getBlockState(bottom.down()))) return false;
        // 找左边界
        BlockPos leftEdge = bottom;
        int guard = 0;
        while (guard++ < 21 && world.getBlockState(leftEdge.offset(left)).isAir()) {
            leftEdge = leftEdge.offset(left);
        }
        if (!isFrameBlock(world.getBlockState(leftEdge.offset(left)))) return false;
        // 量宽
        int width = 1;
        BlockPos scan = leftEdge;
        while (width <= 21 && world.getBlockState(scan.offset(right)).isAir()) {
            scan = scan.offset(right);
            width++;
        }
        if (width < 2 || width > 21) return false;
        if (!isFrameBlock(world.getBlockState(scan.offset(right)))) return false;
        // 量高(沿左边柱)
        int height = 1;
        BlockPos up = leftEdge;
        while (height <= 21 && world.getBlockState(up.up()).isAir()) {
            up = up.up();
            height++;
        }
        if (height < 3 || height > 21) return false;
        // 全量校验:内空全空气,四边全烛块
        for (int w = 0; w < width; w++) {
            BlockPos col = leftEdge.offset(right, w);
            if (!isFrameBlock(world.getBlockState(col.down()))) return false;                 // 底边
            if (!isFrameBlock(world.getBlockState(col.up(height)))) return false;             // 顶边
            for (int h = 0; h < height; h++) {
                if (!world.getBlockState(col.up(h)).isAir()) return false;
            }
        }
        for (int h = 0; h < height; h++) {
            if (!isFrameBlock(world.getBlockState(leftEdge.offset(left).up(h)))) return false;   // 左柱
            if (!isFrameBlock(world.getBlockState(leftEdge.offset(right, width).up(h)))) return false; // 右柱
        }
        // 点燃:内空整片填门
        BlockState portal = ModBlocks.CANDLE_PORTAL.getDefaultState().with(CandlePortalBlock.AXIS, axis);
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                world.setBlockState(leftEdge.offset(right, w).up(h), portal, 18); // 18=更新+不触发邻居连锁
            }
        }
        return true;
    }

    /** 门内碰撞入口:带冷却的双向传送。 */
    public static void requestTeleport(ServerPlayerEntity sp) {
        if (!YongyeConfig.get().enableCandleDimension) return;
        long now = sp.getServerWorld().getTime();
        Long until = COOLDOWN.get(sp.getUuid());
        if (until != null && now < until) return;
        ServerWorld from = sp.getServerWorld();
        ServerWorld dest = from.getRegistryKey() == WORLD_KEY
                ? sp.server.getOverworld()
                : sp.server.getWorld(WORLD_KEY); // sp.server=在树先例(YongyeNet),不用首用的 getServer()
        if (dest == null || dest == from) return;
        COOLDOWN.put(sp.getUuid(), now + COOLDOWN_TICKS);

        int x = sp.getBlockX();
        int z = sp.getBlockZ();
        BlockPos landing = findOrBuildArrival(dest, x, z);
        sp.teleport(dest, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5,
                sp.getYaw(), sp.getPitch()); // 待编译验证:ServerPlayerEntity.teleport(ServerWorld,...) 仓库首用
        sp.sendMessage(Text.literal(dest.getRegistryKey() == WORLD_KEY
                ? "你踏入了烛之维度…" : "你回到了主世界").formatted(Formatting.LIGHT_PURPLE), true);
    }

    /** 到达点:地表落脚;24 格内已有门就落门口,没有就在落点旁搭一扇 4×5 门并点好。 */
    private static BlockPos findOrBuildArrival(ServerWorld dest, int x, int z) {
        // m306 修「传送落在基岩层」:World.getTopY 对未加载区块有 isChunkLoaded 早退,
        // 不生成区块、直接返回世界底(-64)——新维度首次进入时目标区块必然未加载,
        // 落点被 Math.max 钳到 bottomY+2 = -62 正好是基岩层,回程门也跟着埋进石头里。
        // 修法:先强制把目标区块同步生成到 FULL,再从区块自身高度图采样(绕开早退)。
        var chunk = dest.getChunk(x >> 4, z >> 4); // 待编译验证:World.getChunk(int,int) 首用(标准API,同步生成)
        int y = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, x & 15, z & 15) + 1; // 待编译验证:Chunk.sampleHeightmap 首用(getTopY 内部同款调用)
        if (y <= dest.getBottomY() + 2) y = dest.getSeaLevel() + 1; // 兜底:全空气柱按海平面落
        BlockPos landing = new BlockPos(x, y, z);
        // 附近找现成门(±24 水平 / ±16 垂直,粗扫足够)
        for (BlockPos p : BlockPos.iterate(landing.add(-24, -16, -24), landing.add(24, 16, 24))) {
            if (isPortal(dest.getBlockState(p))) {
                return p.toImmutable().offset(Direction.NORTH); // 门口一格
            }
        }
        // 没有 → 搭 4×5 烛块门(X 朝向)+ 脚下垫台,点燃
        BlockPos base = landing;
        BlockState frame = ModBlocks.CANDLE_BLOCK.getDefaultState();
        for (int w = -1; w <= 2; w++) {
            for (int h = -1; h <= 3; h++) {
                boolean border = w == -1 || w == 2 || h == -1 || h == 3;
                BlockPos p = base.add(w, h, 0);
                dest.setBlockState(p, border ? frame
                        : ModBlocks.CANDLE_PORTAL.getDefaultState().with(CandlePortalBlock.AXIS, Direction.Axis.X), 18);
            }
            // 门前落脚平台 + 上方三格净空(斜坡/山体里到达时不至于把玩家埋进土里)
            dest.setBlockState(base.add(w, -1, 1), frame, 18);
            for (int h = 0; h <= 2; h++) {
                dest.setBlockState(base.add(w, h, 1), net.minecraft.block.Blocks.AIR.getDefaultState(), 18);
            }
        }
        return base.add(0, 0, 1);
    }
}
