package com.atemukesu.extendednoteblock.block;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;
import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;
import com.atemukesu.extendednoteblock.sound.ServerSoundManager;
import com.atemukesu.extendednoteblock.util.NotePitch;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.state.StateManager;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import com.mojang.serialization.MapCodec;

public class ExtendedNoteBlockBlock extends BlockWithEntity {

    public static final EnumProperty<NotePitch> PITCH = EnumProperty.of("pitch", NotePitch.class);

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ExtendedNoteBlockDelayScheduler");
        thread.setDaemon(true);
        return thread;
    });

    public ExtendedNoteBlockBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(Properties.POWERED, false)
                .with(PITCH, NotePitch.C));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.POWERED);
        builder.add(PITCH);
    }

    private void triggerNote(World world, BlockPos pos) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        // 生成粒子效果
        if (world.getBlockEntity(pos) instanceof ExtendedNoteBlockEntity blockEntity) {
            double particleColor = (blockEntity.getNote() % 25) / 24.0D;
            serverWorld.spawnParticles(
                    ParticleTypes.NOTE,
                    pos.getX() + 0.5D,
                    pos.getY() + 1.2D,
                    pos.getZ() + 0.5D,
                    0, particleColor, 0.0D, 0.0D, 1.0D);

            // ============== Advanced Features v1.4.0 ==============
            // 检查是否启用了高级模式
            if (blockEntity.isAdvancedModeEnabled()) {
                // 使用新的高级播放方法
                ServerSoundManager.playAdvancedSound(
                        serverWorld,
                        pos,
                        blockEntity.getInstrumentId(),
                        blockEntity.getNote(),
                        blockEntity.getVelocity(),
                        blockEntity.getSustain(),
                        blockEntity.getFadeInTime(),
                        blockEntity.getFadeOutTime(),
                        blockEntity.getPitchBendPoints(),
                        blockEntity.getVolumePoints(),
                        blockEntity.getSoundPath());
            } else {
                // 使用传统方法
                ServerSoundManager.playSound(
                        serverWorld,
                        pos,
                        blockEntity.getInstrumentId(),
                        blockEntity.getNote(),
                        blockEntity.getVelocity(),
                        blockEntity.getSustain(),
                        blockEntity.getFadeInTime(),
                        blockEntity.getFadeOutTime());
            }
        }
    }

    public void previewNote(World world, BlockPos pos) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        if (world.getBlockEntity(pos) instanceof ExtendedNoteBlockEntity blockEntity) {
            // 粒子
            double particleColor = (blockEntity.getNote() % 25) / 24.0D;
            serverWorld.spawnParticles(
                    ParticleTypes.NOTE,
                    pos.getX() + 0.5D,
                    pos.getY() + 1.2D,
                    pos.getZ() + 0.5D,
                    0, particleColor, 0.0D, 0.0D, 1.0D);
            // 预览：短暂的持续时间，没有淡入淡出
            ServerSoundManager.playSound(serverWorld, pos, blockEntity.getInstrumentId(), blockEntity.getNote(),
                    blockEntity.getVelocity(), 20, 0, 3);
        }
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos,
            net.minecraft.util.math.random.Random random) {
    }

    private void stopNote(World world, BlockPos pos) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        // [修改] 在停止声音之前，先取消任何计划中的任务
        if (world.getBlockEntity(pos) instanceof ExtendedNoteBlockEntity blockEntity) {
            blockEntity.cancelScheduledSound();
        }
        ServerSoundManager.stopSound(serverWorld, pos);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
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
        boolean wasPowered = state.get(Properties.POWERED);

        if (world.getBlockEntity(pos) instanceof ExtendedNoteBlockEntity blockEntity) {
            if (isPowered != wasPowered) {
                if (isPowered) { // 上升沿
                    // 1. 立即更新 POWERED 和 PITCH
                    NotePitch correctPitch = NotePitch.fromMidiNote(blockEntity.getNote());
                    BlockState newState = state.with(Properties.POWERED, true).with(PITCH, correctPitch);
                    world.setBlockState(pos, newState, Block.NOTIFY_ALL);

                    // 2. 取消之前的延迟任务（如果有）
                    blockEntity.cancelScheduledSound();

                    // 3. 提交新的延迟发声任务（仅发声，不再设置 POWERED）
                    int delay = blockEntity.getDelayedPlayingTime();
                    if (delay > 0) {
                        ScheduledFuture<?> future = scheduler.schedule(() -> {
                            world.getServer().execute(() -> {
                                // 再次检查信号是否仍然为高，防止误触发
                                if (world.isReceivingRedstonePower(pos) &&
                                        world.getBlockState(pos).get(Properties.POWERED)) {
                                    triggerNote(world, pos);
                                }
                            });
                        }, delay, TimeUnit.MILLISECONDS);
                        blockEntity.setScheduledFuture(future);
                    } else {
                        // 无延迟则立即发声
                        triggerNote(world, pos);
                    }
                } else { // 下降沿
                    blockEntity.cancelScheduledSound();
                    stopNote(world, pos);
                    world.setBlockState(pos, state.with(Properties.POWERED, false), Block.NOTIFY_ALL);
                }
            }
        } else {
            // 如果没有方块实体，仅更新 POWERED 状态
            if (isPowered != wasPowered) {
                world.setBlockState(pos, state.with(Properties.POWERED, isPowered), Block.NOTIFY_ALL);
            }
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

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient()) {
            if (world.getBlockEntity(pos) instanceof ExtendedNoteBlockEntity entity) {
                NotePitch correctPitch = NotePitch.fromMidiNote(entity.getNote());
                world.setBlockState(pos, state.with(PITCH, correctPitch), 2);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ExtendedNoteBlockEntity(pos, state);
    }

    @Override
    public MapCodec<ExtendedNoteBlockBlock> getCodec() {
        return createCodec(ExtendedNoteBlockBlock::new);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}