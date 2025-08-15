package com.atemukesu.extendednoteblock.block;

import org.jetbrains.annotations.Nullable;
import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;
import com.atemukesu.extendednoteblock.network.ModMessages;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.state.StateManager;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;

public class ExtendedNoteBlockBlock extends BlockWithEntity {
    public ExtendedNoteBlockBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(Properties.POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.POWERED);
    }

    private void triggerNote(World world, BlockPos pos) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        if (world.getBlockEntity(pos) instanceof ExtendedNoteBlockEntity blockEntity) {
            double particleColor = (blockEntity.getNote() % 25) / 24.0D;
            serverWorld.spawnParticles(
                    ParticleTypes.NOTE,
                    pos.getX() + 0.5D,
                    pos.getY() + 1.2D,
                    pos.getZ() + 0.5D,
                    0, particleColor, 0.0D, 0.0D, 1.0D);
            ModMessages.sendPlayNoteToClients(serverWorld, pos,
                    blockEntity.getInstrumentId(),
                    blockEntity.getNote(),
                    blockEntity.getVelocity(),
                    blockEntity.getSustain());
        }
    }

    public void previewNote(World world, BlockPos pos) {
        if (world.isClient()) {
            return;
        }
        this.triggerNote(world, pos);
        world.scheduleBlockTick(pos, this, 20);
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos,
            net.minecraft.util.math.random.Random random) {
        this.stopNote(world, pos);
    }

    private void stopNote(World world, BlockPos pos) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        ModMessages.sendStopNoteToClients(serverWorld, pos);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
            BlockHitResult hit) {
        if (!world.isClient) {
            NamedScreenHandlerFactory screenHandlerFactory = state.createScreenHandlerFactory(world, pos);
            if (screenHandlerFactory != null) {
                player.openHandledScreen(screenHandlerFactory);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos,
            boolean notify) {
        if (world.isClient) {
            return;
        }
        boolean isPowered = world.isReceivingRedstonePower(pos);
        if (isPowered != state.get(Properties.POWERED)) {
            if (isPowered) {
                this.triggerNote(world, pos);
            } else {
                this.stopNote(world, pos);
            }
            world.setBlockState(pos, state.with(Properties.POWERED, isPowered), 3);
        }
    }

    @Override
    public void onBroken(WorldAccess world, BlockPos pos, BlockState state) {
        if (state.get(Properties.POWERED)) {
            if (!world.isClient() && world instanceof ServerWorld serverWorld) {
                this.stopNote(serverWorld, pos);
            }
        }
        super.onBroken(world, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (state.get(Properties.POWERED)) {
                if (!world.isClient) {
                    this.stopNote(world, pos);
                }
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ExtendedNoteBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}