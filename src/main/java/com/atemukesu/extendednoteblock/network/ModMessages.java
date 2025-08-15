package com.atemukesu.extendednoteblock.network;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 负责注册和发送所有网络数据包的中心类。
 *
 * 这个类定义了所有数据包的唯一标识符 (Identifier)，并提供了用于注册接收器和发送数据包的静态方法。
 * 它处理服务器与客户端之间的双向通信。
 */
public class ModMessages {
    /**
     * C2S (Client to Server) 数据包ID：用于从客户端GUI更新服务器上的扩展音符盒数据。
     */
    public static final Identifier UPDATE_NOTE_BLOCK_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "update_note_block");

    /**
     * S2C (Server to Client) 数据包ID：用于通知客户端在指定位置播放一个音符。
     */
    public static final Identifier PLAY_NOTE_S2C_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "play_note_s2c");

    /**
     * S2C (Server to Client) 数据包ID：用于通知客户端停止在指定位置播放的音符。
     */
    public static final Identifier STOP_NOTE_S2C_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "stop_note_s2c");

    /**
     * 在服务器端注册所有 C2S (客户端到服务器) 数据包的接收器。
     * 这个方法应该在模组的服务器端初始化阶段被调用。
     */
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_NOTE_BLOCK_ID, UpdateNoteBlockPacket::receive);
    }

    /**
     * 向正在追踪指定位置的客户端发送播放音符的指令。
     *
     * @param world        服务器世界。
     * @param pos          音符盒的位置。
     * @param instrumentId 要播放的乐器ID。
     * @param note         要播放的MIDI音高。
     * @param velocity     音符的力度（音量）。
     * @param sustainTicks 音符的持续时间（游戏刻）。
     */
    public static void sendPlayNoteToClients(ServerWorld world, BlockPos pos, int instrumentId, int note, int velocity,
            int sustainTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeInt(instrumentId);
        buf.writeInt(note);
        buf.writeInt(velocity);
        buf.writeInt(sustainTicks);

        // 使用 PlayerLookup 来高效地找到所有能看到该方块的玩家
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, PLAY_NOTE_S2C_ID, buf);
        }
    }

    /**
     * 向正在追踪指定位置的客户端发送停止音符的指令。
     *
     * @param world 服务器世界。
     * @param pos   音符盒的位置。
     */
    public static void sendStopNoteToClients(ServerWorld world, BlockPos pos) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, STOP_NOTE_S2C_ID, buf);
        }
    }
}