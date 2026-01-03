package com.atemukesu.extendednoteblock.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side smooth move manager.
 * Uses explicit TPS provided by server to scale movement.
 */
public class ClientSmoothMoveManager {

    private static Vec3d targetVelocity = Vec3d.ZERO;
    private static int ticksRemaining = 0;
    private static double currentScale = 1.0;

    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK
                .register(ClientSmoothMoveManager::tick);
    }

    public static void startMove(Vec3d velocity, int duration, Vec3d position, float tps) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            targetVelocity = velocity;
            client.player.setPosition(position);

            if (duration == 0) {
                ticksRemaining = 0;
                client.player.setVelocity(Vec3d.ZERO);
                currentScale = 1.0;
                return;
            }

            // Calculate scale based on provided Server TPS
            // Client TPS is constant 20.
            // Scale = ServerTPS / ClientTPS
            if (tps > 0) {
                currentScale = tps / 20.0;
            } else {
                currentScale = 1.0; // Default if tps invalid
            }

            // Duration is in Server Ticks.
            // Client Ticks = Server Ticks * (ClientTPS / ServerTPS) = Server Ticks / Scale
            if (currentScale > 0) {
                ticksRemaining = (int) (duration / currentScale);
            } else {
                ticksRemaining = duration;
            }
        }
    }

    private static void tick(MinecraftClient client) {
        if (ticksRemaining > 0 && client.player != null) {
            // Apply Scaled Velocity every tick
            client.player.setVelocity(targetVelocity.multiply(currentScale));
            ticksRemaining--;
        }
    }
}
