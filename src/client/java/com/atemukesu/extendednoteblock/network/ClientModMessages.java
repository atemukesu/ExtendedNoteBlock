package com.atemukesu.extendednoteblock.network;

import com.atemukesu.extendednoteblock.client.gui.screen.ConductorScreen;
import com.atemukesu.extendednoteblock.sound.ClientSoundManager;
import com.atemukesu.extendednoteblock.util.CurvePoint;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 负责在客户端注册所有 S2C (服务器到客户端) 数据包的接收器。
 * <p>
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
                    client.execute(() -> ClientSoundManager.playSound(pos, soundId, instrumentId, note, velocity,
                            initialVolume));
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

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.START_ADVANCED_SOUND_ID,
                (client, handler, buf, responseSender) -> {
                    var pos = buf.readBlockPos();
                    var soundId = buf.readUuid();
                    int instrumentId = buf.readInt();
                    int note = buf.readInt();

                    // 读取 t=0 状态
                    float vol = buf.readFloat();
                    float pitchMul = buf.readFloat();
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();

                    client.execute(() -> ClientSoundManager.playAdvancedSound(
                            pos, soundId, instrumentId, note, vol, pitchMul, x, y, z));
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

        // ============== Conductor's Wand ==============
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.SCAN_RESPONSE,
                (client, handler, buf, responseSender) -> {
                    BlockPos min = buf.readBlockPos();
                    BlockPos max = buf.readBlockPos();
                    int size = buf.readInt();

                    Map<String, Integer> counts = new HashMap<>();
                    Map<String, NbtCompound> samples = new HashMap<>();

                    for (int i = 0; i < size; i++) {
                        String id = buf.readString();
                        int count = buf.readInt();
                        NbtCompound nbt = buf.readNbt();
                        counts.put(id, count);
                        samples.put(id, nbt);
                    }

                    client.execute(() -> {
                        client.setScreen(new ConductorScreen(min, max, counts, samples));
                    });
                });

        // 注册平滑移动包
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.SMOOTH_MOVE_ID,
                (client, handler, buf, responseSender) -> {
                    double px = buf.readDouble();
                    double py = buf.readDouble();
                    double pz = buf.readDouble();
                    client.execute(() -> com.atemukesu.extendednoteblock.util.ClientSmoothMoveManager
                            .startMove(new Vec3d(px, py, pz)));
                });
    }

    // ============== Conductor's Wand Methods ==============
    public static class BulkUpdateEntry {
        public final String path;
        public final int mode;
        public final String value;

        public BulkUpdateEntry(String path, int mode, String value) {
            this.path = path;
            this.mode = mode;
            this.value = value;
        }
    }

    public static void sendSmartBulkUpdateToServer(BlockPos min, BlockPos max, String targetBlockId,
            List<BulkUpdateEntry> updates, NbtCompound advancedPatch) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(min);
        buf.writeBlockPos(max);
        buf.writeString(targetBlockId);

        // Write updates list
        buf.writeInt(updates.size());
        for (BulkUpdateEntry entry : updates) {
            buf.writeString(entry.path);
            buf.writeInt(entry.mode);
            buf.writeString(entry.value);
        }

        // Write Advanced Data Patch
        boolean hasAdvanced = advancedPatch != null && !advancedPatch.isEmpty();
        buf.writeBoolean(hasAdvanced);
        if (hasAdvanced) {
            buf.writeNbt(advancedPatch);
        }

        ClientPlayNetworking.send(ModMessages.BULK_UPDATE, buf);
    }

    public static void sendScanRequestToServer(BlockPos pos1, BlockPos pos2) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos1);
        buf.writeBlockPos(pos2);
        ClientPlayNetworking.send(ModMessages.SCAN_REQUEST, buf);
    }

    public static void sendClearSelectionToServer() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(0); // 0 = Clear
        // No BlockPos needed
        ClientPlayNetworking.send(ModMessages.SET_WAND_POS_ID, buf);
    }

    // ============== Advanced Settings v1.4.0 ==============
    public static void sendAdvancedSettingsToServer(net.minecraft.util.math.BlockPos pos, List<CurvePoint> volumePoints,
            List<CurvePoint> pitchBendPoints, List<Vec3d> soundPath,
            String storedExprX, String storedExprY, String storedExprZ) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);

        // 写入音量关键点
        buf.writeInt(volumePoints.size());
        for (CurvePoint p : volumePoints) {
            buf.writeFloat(p.time);
            buf.writeFloat(p.value);
        }

        // 写入弯音关键点
        buf.writeInt(pitchBendPoints.size());
        for (CurvePoint p : pitchBendPoints) {
            buf.writeFloat(p.time);
            buf.writeFloat(p.value);
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

}