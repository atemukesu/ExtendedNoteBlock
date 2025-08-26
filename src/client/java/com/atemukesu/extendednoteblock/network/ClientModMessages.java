package com.atemukesu.extendednoteblock.network;

import com.atemukesu.extendednoteblock.sound.ClientSoundManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.BlockPos;
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
    }
}