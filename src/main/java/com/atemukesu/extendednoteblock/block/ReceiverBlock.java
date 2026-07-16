package com.atemukesu.extendednoteblock.block;

import com.atemukesu.extendednoteblock.util.RedstoneManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class ReceiverBlock extends Block {
    public static final BooleanProperty POWERED = Properties.POWERED;

    public ReceiverBlock(net.minecraft.block.AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    // 处理红石输出
    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED) ? 15 : 0;
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClient) {
            RedstoneManager.addReceiver(world, pos);
            // 放置时立即检查全局状态
            boolean globalPower = RedstoneManager.isGlobalPowered(world);
            if (state.get(POWERED) != globalPower) {
                world.setBlockState(pos, state.with(POWERED, globalPower), 3);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (!world.isClient) {
                RedstoneManager.removeReceiver(world, pos);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
