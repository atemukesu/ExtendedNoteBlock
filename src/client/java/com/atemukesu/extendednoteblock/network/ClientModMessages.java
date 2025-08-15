package com.atemukesu.extendednoteblock.network;

import com.atemukesu.extendednoteblock.sound.ClientSoundManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.BlockPos;

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
        // 注册 PLAY_NOTE_S2C 数据包的接收器
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.PLAY_NOTE_S2C_ID,
                (client, handler, buf, responseSender) -> {
                    // 从缓冲区读取数据
                    BlockPos pos = buf.readBlockPos();
                    int instrumentId = buf.readInt();
                    int note = buf.readInt();
                    int velocity = buf.readInt();
                    int sustainTicks = buf.readInt();
                    // 将声音播放逻辑调度到主渲染线程执行，以确保线程安全
                    client.execute(() -> ClientSoundManager.playSound(pos, instrumentId, note, velocity, sustainTicks));
                });

        // 注册 STOP_NOTE_S2C 数据包的接收器
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.STOP_NOTE_S2C_ID,
                (client, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    // 同样，将停止声音的逻辑调度到主线程
                    client.execute(() -> ClientSoundManager.stopSound(pos));
                });
    }
}