package com.atemukesu.extendednoteblock.block;

import com.atemukesu.extendednoteblock.util.RedstoneManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TransmitterBlock extends Block {
    public static final BooleanProperty POWERED = Properties.POWERED;

    public TransmitterBlock(net.minecraft.block.AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos,
            boolean notify) {
        if (world.isClient)
            return;

        boolean isBeingPowered = world.isReceivingRedstonePower(pos);
        boolean wasPowered = state.get(POWERED);

        if (isBeingPowered != wasPowered) {
            world.setBlockState(pos, state.with(POWERED, isBeingPowered), 3);
            RedstoneManager.transmitterChanged(world, pos, isBeingPowered);
        }
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClient) {
            RedstoneManager.addTransmitter(world, pos);
            // 检查当前供电状态并同步
            boolean isBeingPowered = world.isReceivingRedstonePower(pos);
            if (isBeingPowered != state.get(POWERED)) {
                world.setBlockState(pos, state.with(POWERED, isBeingPowered), 3);
                RedstoneManager.transmitterChanged(world, pos, isBeingPowered);
            }
        }
    }

    @Deprecated
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (state.get(POWERED)) {
                RedstoneManager.transmitterChanged(world, pos, false);
            }
            RedstoneManager.removeTransmitter(world, pos);
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
