package com.atemukesu.extendednoteblock.util;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class ClientSmoothMoveManager {

    private static Vec3d targetPos = null;
    private static final double SMOOTHING_FACTOR = 0.55;

    // null = 未检测，true = 回放中，false = 正常
    private static Boolean replayCache = null;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientSmoothMoveManager::tick);
    }

    private static boolean isInReplay() {
        try {
            Class<?> replayModClass = Class.forName("com.replaymod.replay.ReplayModReplay");
            Object instance = replayModClass.getField("instance").get(null);
            if (instance == null) return false;
            java.lang.reflect.Method getReplayHandler = replayModClass.getMethod("getReplayHandler");
            return getReplayHandler.invoke(instance) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static void updateMove(Vec3d position, boolean isStop) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null)
            return;

        // 1. 已确认在回放中，忽略所有后续包（包括 stop 包）
        if (replayCache != null && replayCache) {
            return;
        }

        // 2. 停止包：在拉回玩家前检查是否中途进入了回放模式
        if (isStop) {
            if (isInReplay()) {
                // 回放中：不执行 setPosition 拉回，标记为回放模式
                targetPos = null;
                replayCache = true;
                return;
            }
            targetPos = null;
            replayCache = null;
            if (position != null) {
                client.player.setPosition(position);
                client.player.setVelocity(Vec3d.ZERO);
            }
            return;
        }

        // 3. 移动包：首次收到时检测一次回放状态
        if (replayCache == null) {
            replayCache = isInReplay();
        }

        // 4. 回放中则忽略所有位置同步
        if (replayCache) {
            return;
        }

        // 5. 正常平滑移动
        if (position == null)
            return;

        if (targetPos == null || position.squaredDistanceTo(client.player.getPos()) > 100) {
            client.player.setPosition(position);
        }

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