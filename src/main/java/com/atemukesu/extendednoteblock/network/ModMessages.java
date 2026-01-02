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
 * <p>
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

    // ============== Advanced Features v1.4.0 ==============
    public static final Identifier ADVANCED_UPDATE_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "adv_update");

    // ============== Advanced Settings v1.4.0 ==============
    public static final Identifier ADVANCED_SETTINGS_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "advanced_settings");

    /**
     * 在服务器端注册所有 C2S (客户端到服务器) 数据包的接收器。
     * 这个方法应该在模组的服务器端初始化阶段被调用。
     */
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_NOTE_BLOCK_ID, UpdateNoteBlockPacket::receive);
        // ============== Advanced Settings v1.4.0 ==============
        ServerPlayNetworking.registerGlobalReceiver(ADVANCED_SETTINGS_ID, AdvancedSettingsPacket::receive);
    }

    public static void sendStartSoundToClients(ServerWorld world, BlockPos pos, UUID soundId, int instrumentId,
                                               int note, int velocity, float initialVolume) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeUuid(soundId);
        buf.writeInt(instrumentId);
        buf.writeInt(note);
        buf.writeInt(velocity);
        buf.writeFloat(initialVolume); // 初始音量
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

    // ============== Advanced Features v1.4.0 ==============
    public static void sendAdvancedUpdateToClients(ServerWorld world, BlockPos pos, UUID soundId, float vol, float pitchMul, double x, double y, double z) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(soundId);
        buf.writeFloat(vol);
        buf.writeFloat(pitchMul);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);

        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, ADVANCED_UPDATE_ID, buf);
        }
    }

    // 在 ModMessages.java 中添加
    public static void sendStartAdvancedSoundToClients(ServerWorld world, BlockPos pos, UUID soundId,
                                                       int instrumentId, int note, float volume,
                                                       float pitch, double x, double y, double z) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeUuid(soundId);
        buf.writeInt(instrumentId);
        buf.writeInt(note);
        buf.writeFloat(volume);
        buf.writeFloat(pitch); // 新增
        buf.writeDouble(x);    // 新增
        buf.writeDouble(y);    // 新增
        buf.writeDouble(z);    // 新增

        // 这里的 START_SOUND_ID 应该是你之前定义好的播放音符的 Identifier
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, START_SOUND_ID, buf);
        }
    }
}