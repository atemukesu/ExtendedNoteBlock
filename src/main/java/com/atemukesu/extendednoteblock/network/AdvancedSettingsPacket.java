package com.atemukesu.extendednoteblock.network;

import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;
import com.atemukesu.extendednoteblock.util.CurvePoint;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责在服务器端处理从客户端GUI发来的高级设置数据包。
 * 
 * 当玩家在高级设置GUI中修改曲线和表达式并保存时，客户端会发送这个数据包。
 * 服务器接收后，会更新对应的 {@link ExtendedNoteBlockEntity} 的高级功能数据。
 */
public class AdvancedSettingsPacket {

    public static final Identifier ADVANCED_SETTINGS_ID = new Identifier("extendednoteblock", "advanced_settings");

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
        
        BlockPos pos = buf.readBlockPos();
        
        // 读取音量关键点
        int volSize = buf.readInt();
        // 安全检查 (防止 DoS)
        if (volSize < 0 || volSize > 256) return; 
        List<CurvePoint> volumePoints = new ArrayList<>();
        for (int i = 0; i < volSize; i++) {
            float t = buf.readFloat();
            float v = buf.readFloat();
            volumePoints.add(new CurvePoint(t, v));
        }

        // 读取弯音关键点
        int pitchSize = buf.readInt();
        if (pitchSize < 0 || pitchSize > 256) return;
        List<CurvePoint> pitchPoints = new ArrayList<>();
        for (int i = 0; i < pitchSize; i++) {
            float t = buf.readFloat();
            float v = buf.readFloat();
            pitchPoints.add(new CurvePoint(t, v));
        }

        // 读取声源移动路径数据
        int soundPathSize = buf.readInt();
        List<Vec3d> soundPath = new ArrayList<>();
        for (int i = 0; i < soundPathSize; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            soundPath.add(new Vec3d(x, y, z));
        }
        
        // 读取存储的表达式字符串
        String storedExpressionX = buf.readString();
        String storedExpressionY = buf.readString();
        String storedExpressionZ = buf.readString();

        server.execute(() -> {
            World world = player.getWorld();
            if (world.getBlockEntity(pos) instanceof ExtendedNoteBlockEntity entity) {
                entity.setVolumePoints(volumePoints);
                entity.setPitchBendPoints(pitchPoints);
                entity.setSoundPath(soundPath);
                
                // 更新存储的表达式
                entity.setStoredExpressionX(storedExpressionX);
                entity.setStoredExpressionY(storedExpressionY);
                entity.setStoredExpressionZ(storedExpressionZ);
            }
        });
    }
}