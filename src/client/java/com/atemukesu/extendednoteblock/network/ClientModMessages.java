package com.atemukesu.extendednoteblock.network;

import com.atemukesu.extendednoteblock.sound.ClientSoundManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

/**
 * 负责在客户端注册所有 S2C (服务器到客户端) 数据包的接收器。
 *
 * 这个类处理来自服务器的指令，例如播放或停止音符。
 */
public class ClientModMessages {
    /**
     * 在客户端注册所有 S2C 数据包的接收逻辑。
     * 这个方法应该在模组的客户端初始化阶段被调用。
     */
    public static void registerS2CPackets() {
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.START_SOUND_ID,
                (client, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    UUID soundId = buf.readUuid();
                    int instrumentId = buf.readInt();
                    int note = buf.readInt();
                    int velocity = buf.readInt();
                    float initialVolume = buf.readFloat(); // 获取初始音量
                    client.execute(() -> ClientSoundManager.playSound(pos, soundId, instrumentId, note, velocity, initialVolume));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.UPDATE_VOLUME_ID,
                (client, handler, buf, responseSender) -> {
                    UUID soundId = buf.readUuid();
                    float volume = buf.readFloat();
                    client.execute(() -> ClientSoundManager.updateVolume(soundId, volume));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.STOP_SOUND_ID,
                (client, handler, buf, responseSender) -> {
                    UUID soundId = buf.readUuid();
                    client.execute(() -> ClientSoundManager.stopSound(soundId));
                });

        // ============== Advanced Features v1.4.0 ==============
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.ADVANCED_UPDATE_ID,
                (client, handler, buf, responseSender) -> {
                    UUID soundId = buf.readUuid();
                    float vol = buf.readFloat();
                    float pitchMul = buf.readFloat();
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    
                    client.execute(() -> ClientSoundManager.updateAdvanced(soundId, vol, pitchMul, x, y, z));
                });
    }
    
    // ============== Advanced Settings v1.4.0 ==============
    public static void sendAdvancedSettingsToServer(net.minecraft.util.math.BlockPos pos, List<Float> volumeCurve, List<Float> pitchBendCurve, List<Vec3d> soundPath,
                                                   String storedExprX, String storedExprY, String storedExprZ) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        
        // 写入音量曲线数据
        buf.writeInt(volumeCurve.size());
        for (Float value : volumeCurve) {
            buf.writeFloat(value);
        }
        
        // 写入弯音曲线数据
        buf.writeInt(pitchBendCurve.size());
        for (Float value : pitchBendCurve) {
            buf.writeFloat(value);
        }
        
        // 写入声源移动路径数据
        buf.writeInt(soundPath.size());
        for (Vec3d vec : soundPath) {
            buf.writeDouble(vec.x);
            buf.writeDouble(vec.y);
            buf.writeDouble(vec.z);
        }
        
        // 写入存储的表达式字符串
        buf.writeString(storedExprX);
        buf.writeString(storedExprY);
        buf.writeString(storedExprZ);
        
        ClientPlayNetworking.send(ModMessages.ADVANCED_SETTINGS_ID, buf);
    }
    
    // 保留旧的方法用于兼容性
    public static void sendAdvancedSettingsToServer(net.minecraft.util.math.BlockPos pos, List<Float> volumeCurve, List<Float> pitchBendCurve, List<Vec3d> soundPath) {
        sendAdvancedSettingsToServer(pos, volumeCurve, pitchBendCurve, soundPath, "", "", "");
    }
}