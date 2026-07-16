package com.atemukesu.extendednoteblock.util;

import com.atemukesu.extendednoteblock.block.ReceiverBlock;
import com.atemukesu.extendednoteblock.block.TransmitterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class RedstoneManager {

    /*
     * Persistent State Implementation
     */
    public static class RedstoneData extends PersistentState {
        // 所有已知发射器位置及其最后一次已知的供电状态
        // 即使发射器所在区块未加载，也据此判断全局是否有电
        private final Set<BlockPos> activeTransmitters = new HashSet<>();
        // 所有接收器位置
        private final Set<BlockPos> receivers = new HashSet<>();

        public static RedstoneData readNbt(NbtCompound nbt) {
            RedstoneData data = new RedstoneData();

            // 兼容旧格式：activeTransmitters 整数
            if (nbt.contains("activeTransmitters", NbtElement.INT_TYPE)) {
                // 旧格式没有位置信息，无法恢复；让后续 onBlockAdded 重新注册
                // 这里不恢复任何激活状态，避免误触发
            }

            // 兼容旧格式：transmitters 位置列表
            if (nbt.contains("transmitters", NbtElement.LIST_TYPE)) {
                NbtList list = nbt.getList("transmitters", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    BlockPos pos = getBlockPos(list.getCompound(i));
                    if (pos != null) data.activeTransmitters.add(pos);
                }
            }

            // 新格式：明确记录激活的发射器位置
            data.activeTransmitters.clear();
            if (nbt.contains("activeTransmittersList", NbtElement.LIST_TYPE)) {
                NbtList list = nbt.getList("activeTransmittersList", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    BlockPos pos = getBlockPos(list.getCompound(i));
                    if (pos != null) data.activeTransmitters.add(pos);
                }
            }

            // 读取接收器
            data.receivers.clear();
            if (nbt.contains("receivers", NbtElement.LIST_TYPE)) {
                NbtList list = nbt.getList("receivers", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    BlockPos pos = getBlockPos(list.getCompound(i));
                    if (pos != null) data.receivers.add(pos);
                }
            }

            return data;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            // 新格式：激活的发射器位置
            NbtList txList = new NbtList();
            for (BlockPos pos : activeTransmitters) {
                txList.add(putBlockPos(pos));
            }
            nbt.put("activeTransmittersList", txList);

            // 接收器位置
            NbtList rxList = new NbtList();
            for (BlockPos pos : receivers) {
                rxList.add(putBlockPos(pos));
            }
            nbt.put("receivers", rxList);

            return nbt;
        }

        public static final PersistentState.Type<RedstoneData> TYPE = new PersistentState.Type<>(
                RedstoneData::new,
                (nbt, lookup) -> RedstoneData.readNbt(nbt),
                null);

        public static RedstoneData get(ServerWorld world) {
            return world.getPersistentStateManager().getOrCreate(TYPE, "extendednoteblock_redstone");
        }

        public boolean isGlobalPowered() {
            return !activeTransmitters.isEmpty();
        }

        public void updateReceivers(ServerWorld world) {
            boolean powered = isGlobalPowered();

            var iterator = receivers.iterator();
            while (iterator.hasNext()) {
                BlockPos pos = iterator.next();
                if (!world.isChunkLoaded(pos)) {
                    continue;
                }
                try {
                    BlockState state = world.getBlockState(pos);
                    if (state.getBlock() instanceof ReceiverBlock) {
                        boolean oldPowered = state.get(ReceiverBlock.POWERED);
                        if (oldPowered != powered) {
                            world.setBlockState(pos, state.with(ReceiverBlock.POWERED, powered), 3);
                            world.updateNeighbors(pos, state.getBlock());
                        }
                    } else {
                        // 位置已不再是接收器，清理残留
                        iterator.remove();
                        markDirty();
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    // --- Public API ---

    /**
     * 通知指定发射器状态变化。
     */
    public static void transmitterChanged(World world, BlockPos pos, boolean powered) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld))
            return;

        RedstoneData data = RedstoneData.get(serverWorld);
        boolean changed;
        if (powered) {
            changed = data.activeTransmitters.add(pos);
        } else {
            changed = data.activeTransmitters.remove(pos);
        }

        if (changed) {
            data.markDirty();
            data.updateReceivers(serverWorld);
        }
    }

    /**
     * 注册发射器位置并同步当前状态（方块放置时调用）。
     */
    public static void addTransmitter(World world, BlockPos pos) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld))
            return;

        RedstoneData data = RedstoneData.get(serverWorld);
        BlockState state = world.getBlockState(pos);
        boolean powered = state.getBlock() instanceof TransmitterBlock && state.get(TransmitterBlock.POWERED);

        if (powered) {
            if (data.activeTransmitters.add(pos)) {
                data.markDirty();
                data.updateReceivers(serverWorld);
            }
        } else {
            // 即使没电也确保旧残留被清理
            if (data.activeTransmitters.remove(pos)) {
                data.markDirty();
                data.updateReceivers(serverWorld);
            }
        }
    }

    /**
     * 移除发射器（方块被破坏时调用）。
     */
    public static void removeTransmitter(World world, BlockPos pos) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld))
            return;

        RedstoneData data = RedstoneData.get(serverWorld);
        if (data.activeTransmitters.remove(pos)) {
            data.markDirty();
            data.updateReceivers(serverWorld);
        }
    }

    public static boolean isGlobalPowered(World world) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld))
            return false;
        return RedstoneData.get(serverWorld).isGlobalPowered();
    }

    public static void addReceiver(World world, BlockPos pos) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld))
            return;
        RedstoneData data = RedstoneData.get(serverWorld);
        if (data.receivers.add(pos)) {
            data.markDirty();
        }
        // 放置后立即同步当前状态
        boolean globalPower = data.isGlobalPowered();
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ReceiverBlock && state.get(ReceiverBlock.POWERED) != globalPower) {
            world.setBlockState(pos, state.with(ReceiverBlock.POWERED, globalPower), 3);
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

    /**
     * 存档重载后调用：延迟几 tick，等区块加载完毕后再同步所有接收器。
     */
    public static void syncOnWorldLoad(World world) {
        if (world instanceof ServerWorld serverWorld) {
            try {
                // 跨 1 tick 延迟执行，确保玩家所在区块已加载
                serverWorld.getServer().execute(() -> {
                    serverWorld.getServer().execute(() -> {
                        RedstoneData data = RedstoneData.get(serverWorld);
                        data.updateReceivers(serverWorld);
                    });
                });
            } catch (Exception ignored) {
            }
        }
    }

    // ---- BlockPos 序列化辅助 ----

    // 自己实现 BlockPos 序列化/反序列化，避免 NbtHelper.fromBlockPos 使用大写 keys 导致不兼容
    private static NbtCompound putBlockPos(BlockPos pos) {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("x", pos.getX());
        nbt.putInt("y", pos.getY());
        nbt.putInt("z", pos.getZ());
        return nbt;
    }

    private static BlockPos getBlockPos(NbtCompound nbt) {
        if (nbt == null) return null;
        // 优先读小写 x,y,z（自己写的格式）
        if (nbt.contains("x", NbtElement.INT_TYPE) && nbt.contains("y", NbtElement.INT_TYPE)
                && nbt.contains("z", NbtElement.INT_TYPE)) {
            return new BlockPos(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z"));
        }
        // 兼容 NbtHelper.fromBlockPos 生成的大写 X,Y,Z
        if (nbt.contains("X", NbtElement.INT_TYPE) && nbt.contains("Y", NbtElement.INT_TYPE)
                && nbt.contains("Z", NbtElement.INT_TYPE)) {
            return new BlockPos(nbt.getInt("X"), nbt.getInt("Y"), nbt.getInt("Z"));
        }
        return null;
    }
}
