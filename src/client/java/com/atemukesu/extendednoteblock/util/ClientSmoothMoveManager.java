package com.atemukesu.extendednoteblock.util;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class ClientSmoothMoveManager {

    private static Vec3d targetPos = null;
    private static final double SMOOTHING_FACTOR = 0.55;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientSmoothMoveManager::tick);
    }

    /**
     * 收到服务端包时调用。
     * isStop=true: 停止移动，释放控制权
     * isStop=false: 更新目标位置
     */
    public static void updateMove(Vec3d position, boolean isStop) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null)
            return;

        if (isStop) {
            targetPos = null;
            // 最后一跳强制同步，确保位置准确
            if (position != null) {
                client.player.setPosition(position);
                client.player.setVelocity(Vec3d.ZERO);
            }
            return;
        }

        if (position == null)
            return;

        // 如果这是第一次收到，或者距离太远（比如传送），直接瞬移，不要平滑
        if (targetPos == null || position.squaredDistanceTo(client.player.getPos()) > 100) {
            client.player.setPosition(position);
        }

        // 更新目标点
        targetPos = position;
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || targetPos == null)
            return;

        Vec3d currentPos = client.player.getPos();

        if (currentPos.squaredDistanceTo(targetPos) < 0.0001) {
            client.player.setPosition(targetPos);
            return;
        }

        Vec3d newPos = currentPos.lerp(targetPos, SMOOTHING_FACTOR);

        client.player.setPosition(newPos);
        client.player.setVelocity(Vec3d.ZERO);
    }
}