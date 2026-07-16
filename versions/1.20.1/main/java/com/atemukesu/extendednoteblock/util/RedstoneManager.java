package com.atemukesu.extendednoteblock.util;

import com.atemukesu.extendednoteblock.block.ReceiverBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class RedstoneManager {

    /*
     * Persistent State Implementation
     */
    public static class RedstoneData extends PersistentState {
        private int activeTransmitters = 0;
        private final Set<BlockPos> receivers = new HashSet<>();

        public static RedstoneData readNbt(NbtCompound nbt) {
            RedstoneData data = new RedstoneData();
            data.activeTransmitters = nbt.getInt("activeTransmitters");

            NbtList list = nbt.getList("receivers", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                data.receivers.add(NbtHelper.toBlockPos(list.getCompound(i)));
            }
            return data;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            nbt.putInt("activeTransmitters", activeTransmitters);

            NbtList list = new NbtList();
            for (BlockPos pos : receivers) {
                list.add(NbtHelper.fromBlockPos(pos));
            }
            nbt.put("receivers", list);
            return nbt;
        }

        public static RedstoneData get(ServerWorld world) {
            return world.getPersistentStateManager().getOrCreate(
                    RedstoneData::readNbt,
                    RedstoneData::new,
                    "extendednoteblock_redstone");
        }
    }

    // --- Public API ---

    public static void transmitterChanged(World world, boolean powered) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld))
            return;

        RedstoneData data = RedstoneData.get(serverWorld);
        if (powered) {
            data.activeTransmitters++;
        } else {
            data.activeTransmitters = Math.max(0, data.activeTransmitters - 1);
        }
        data.markDirty();
        updateAllReceivers(serverWorld, data);
    }

    public static boolean isGlobalPowered(World world) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld))
            return false;
        return RedstoneData.get(serverWorld).activeTransmitters > 0;
    }

    public static void addReceiver(World world, BlockPos pos) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld))
            return;
        RedstoneData data = RedstoneData.get(serverWorld);
        if (data.receivers.add(pos)) {
            data.markDirty();
        }
    }

    public static void removeReceiver(World world, BlockPos pos) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld))
            return;
        RedstoneData data = RedstoneData.get(serverWorld);
        if (data.receivers.remove(pos)) {
            data.markDirty();
        }
    }

    @SuppressWarnings("deprecation")
    private static void updateAllReceivers(ServerWorld world, RedstoneData data) {
        boolean powered = data.activeTransmitters > 0;
        for (BlockPos pos : data.receivers) {
            // Check if chunk is loaded to prevent loading chunks
            if (world.isChunkLoaded(pos)) {
                try {
                    net.minecraft.block.BlockState state = world.getBlockState(pos);
                    if (state.getBlock() instanceof ReceiverBlock) {
                        // Only update if state is different to avoid infinite loops or unnecessary
                        // updates
                        if (state.get(ReceiverBlock.POWERED) != powered) {
                            world.setBlockState(pos, state.with(ReceiverBlock.POWERED, powered), 3);
                            world.updateNeighbors(pos, state.getBlock());
                        }
                    } else {
                        // Cleanup invalid positions?
                        // Maybe unsafe directly inside loop, but sets can handle removal if using
                        // iterator.
                        // For now keep simple. Listener 'removeReceiver' logic handles block break.
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}