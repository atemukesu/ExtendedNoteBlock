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

        // 1. 停止包：结束当前移动会话，重置缓存
        if (isStop) {
            targetPos = null;
            replayCache = null;
            if (position != null) {
                client.player.setPosition(position);
                client.player.setVelocity(Vec3d.ZERO);
            }
            return;
        }

        // 2. 移动包：首次收到时检测一次回放状态
        if (replayCache == null) {
            replayCache = isInReplay();
        }

        // 3. 回放中则忽略所有位置同步
        if (replayCache) {
            return;
        }

        // 4. 正常平滑移动
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