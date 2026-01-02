package com.atemukesu.extendednoteblock.util;

import com.atemukesu.extendednoteblock.block.ReceiverBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.HashSet;
import java.util.Set;

public class RedstoneManager {
    private static int activeTransmitters = 0;
    private static final Set<BlockPos> receivers = new HashSet<>();

    // 发送块状态改变时调用
    public static void transmitterChanged(World world, boolean powered) {
        if (powered) {
            activeTransmitters++;
        } else {
            activeTransmitters = Math.max(0, activeTransmitters - 1);
        }
        updateAllReceivers(world);
    }

    public static boolean isGlobalPowered() {
        return activeTransmitters > 0;
    }

    public static void addReceiver(BlockPos pos) {
        receivers.add(pos);
    }

    public static void removeReceiver(BlockPos pos) {
        receivers.remove(pos);
    }

    private static void updateAllReceivers(World world) {
        for (BlockPos pos : receivers) {
            // 只有在区块已加载时更新
            if (world.isChunkLoaded(pos)) {
                world.updateNeighbors(pos, world.getBlockState(pos).getBlock());
                // 触发方块状态更新
                world.setBlockState(pos, world.getBlockState(pos).with(ReceiverBlock.POWERED, isGlobalPowered()), 3);
            }
        }
    }
}