package com.yongye.block;

import com.yongye.system.CandleDimension;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * m305 烛焰之门(维度门内芯):烛块搭地狱门形状 + 打火石点燃生成(CandleDimension.tryLightPortal),
 * 玩家走进去传送 烛之维度 ↔ 主世界(CandleDimension.requestTeleport,带 4 秒冷却)。
 * 无碰撞薄板(照下界门口径),自发光;邻居更新时若贴着的支撑不再是烛块/门块则自行熄灭(连锁塌门)。
 * 非玩家实体不传送(维度是刷怪猎场,怪涌回主世界会炸档,有意为之)。
 */
public class CandlePortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;

    private static final VoxelShape X_SHAPE = Block.createCuboidShape(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape Z_SHAPE = Block.createCuboidShape(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    public CandlePortalBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getStateManager().getDefaultState().with(AXIS, Direction.Axis.X));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty(); // 可穿行
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient && entity instanceof ServerPlayerEntity sp) {
            CandleDimension.requestTeleport(sp);
        }
    }

    /** 支撑校验:门面方向两侧 + 上下,必须仍是烛块或门块,否则本格熄灭(连锁塌整扇门)。 */
    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                net.minecraft.world.WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis axis = state.get(AXIS);
        boolean relevant = direction == Direction.UP || direction == Direction.DOWN
                || direction.getAxis() == axis;
        if (relevant && !neighborState.isOf(this) && !CandleDimension.isFrameBlock(neighborState)) {
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        return state;
    }
}
