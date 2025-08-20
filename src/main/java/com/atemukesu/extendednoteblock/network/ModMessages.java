package com.atemukesu.extendednoteblock.network;

import java.util.UUID;

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

    public static final Identifier START_SOUND_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "start_sound");
    public static final Identifier UPDATE_VOLUME_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "update_volume");
    public static final Identifier STOP_SOUND_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "stop_sound");

    /**
     * 在服务器端注册所有 C2S (客户端到服务器) 数据包的接收器。
     * 这个方法应该在模组的服务器端初始化阶段被调用。
     */
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_NOTE_BLOCK_ID, UpdateNoteBlockPacket::receive);
    }

    public static void sendStartSoundToClients(ServerWorld world, BlockPos pos, UUID soundId, int instrumentId,
            int note, int velocity) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeUuid(soundId);
        buf.writeInt(instrumentId);
        buf.writeInt(note);
        buf.writeInt(velocity);
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, START_SOUND_ID, buf);
        }
    }

    public static void sendUpdateVolumeToClients(ServerWorld world, BlockPos pos, UUID soundId, float volume) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(soundId);
        buf.writeFloat(volume); // 使用 writeFloat
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, UPDATE_VOLUME_ID, buf);
        }
    }

    public static void sendStopSoundToClients(ServerWorld world, BlockPos pos, UUID soundId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(soundId);
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, STOP_SOUND_ID, buf);
        }
    }
}