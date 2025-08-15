package com.atemukesu.extendednoteblock.network;

import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;
import com.atemukesu.extendednoteblock.map.InstrumentMap;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.block.Block;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * 负责在服务器端处理从客户端GUI发来的 {@link ModMessages#UPDATE_NOTE_BLOCK_ID} 数据包。
 *
 * 当玩家在扩展音符盒GUI中修改设置并点击“完成”时，客户端会发送这个数据包。
 * 服务器接收后，会更新对应的 {@link ExtendedNoteBlockEntity} 的数据，
 * 并且可能会根据选择的乐器更换音符盒下方的方块。
 */
public class UpdateNoteBlockPacket {

    /**
     * C2S 数据包的接收逻辑。
     * 这个方法在网络线程上被调用，因此需要使用 {@code server.execute} 来确保代码在主服务器线程上执行，
     * 以安全地与世界和方块实体进行交互。
     *
     * @param server         Minecraft 服务器实例。
     * @param player         发送数据包的玩家。
     * @param handler        玩家的网络处理器。
     * @param buf            包含数据包数据的缓冲区。
     * @param responseSender 用于发送响应的发送器。
     */
    public static void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler,
            PacketByteBuf buf, PacketSender responseSender) {
        // 从数据包缓冲区中读取数据，并使用 MathHelper.clamp 确保数据在有效范围内
        BlockPos pos = buf.readBlockPos();
        int note = MathHelper.clamp(buf.readInt(), 0, 127);
        int velocity = MathHelper.clamp(buf.readInt(), 0, 127);
        int sustain = MathHelper.clamp(buf.readInt(), 0, 400);
        int instrumentId = buf.readInt();

        // 将逻辑切换到主线程执行
        server.execute(() -> {
            World world = player.getWorld();

            // 验证目标位置是否存在正确的方块实体
            if (world.getBlockEntity(pos) instanceof ExtendedNoteBlockEntity entity) {
                // 更新方块实体的数值
                entity.updateValues(note, velocity, sustain);

                // 根据选择的乐器ID，尝试更新音符盒下方的方块
                updateInstrumentBlock(player, world, pos, instrumentId);

            } else {
                System.err.println("在位置 " + pos + " 未找到 ExtendedNoteBlockEntity");
            }
        });
    }

    /**
     * 根据指定的乐器ID，更新音符盒下方的方块。
     *
     * @param player       执行操作的玩家，用于权限检查。
     * @param world        世界。
     * @param noteBlockPos 音符盒的位置。
     * @param instrumentId 目标乐器ID。
     */
    private static void updateInstrumentBlock(ServerPlayerEntity player, World world, BlockPos noteBlockPos,
            int instrumentId) {
        BlockPos belowPos = noteBlockPos.down();
        // 从映射中查找乐器ID对应的方块ID
        String targetBlockId = InstrumentMap.GM_INSTRUMENT_TO_BLOCK.get(instrumentId);

        if (targetBlockId != null) {
            try {
                Block targetBlock = Registries.BLOCK.get(new Identifier(targetBlockId));
                Block currentBlockBelow = world.getBlockState(belowPos).getBlock();

                // 只有在目标方块与当前方块不同时才进行更换
                if (targetBlock != null && targetBlock != currentBlockBelow) {
                    // 检查玩家是否有权限修改该位置
                    if (world.canPlayerModifyAt(player, belowPos)) {
                        world.setBlockState(belowPos, targetBlock.getDefaultState(), 3); // 3 = NOTIFY_LISTENERS |
                                                                                         // Block.NOTIFY_LISTENERS
                    } else {
                        // 如果没有权限，向玩家发送一条提示消息
                        player.sendMessage(Text.translatable("gui.extendednoteblock.error.no_permission"), true);
                    }
                }
            } catch (Exception e) {
                System.err.println("更换方块时出错: " + e.getMessage());
            }
        }
    }
}