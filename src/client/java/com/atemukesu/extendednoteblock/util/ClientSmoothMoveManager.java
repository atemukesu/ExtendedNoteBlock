package com.atemukesu.extendednoteblock.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side smooth move manager.
 * Receives frequent updates from server and interpolates movement.
 */
public class ClientSmoothMoveManager {

    private static Vec3d targetVelocity = Vec3d.ZERO; // Server Velocity (Blocks/ServerTicket)
    private static Vec3d targetPosition = null; // Latest position from server
    private static int ticksRemaining = 0;

    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK
                .register(ClientSmoothMoveManager::tick);
    }

    public static void startMove(Vec3d velocity, int duration, Vec3d position, float tps) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            targetVelocity = velocity;
            targetPosition = position;

            if (duration == 0) {
                ticksRemaining = 0;
                client.player.setVelocity(Vec3d.ZERO);
                // Snap to final position to ensure sync
                if (position != null && position != Vec3d.ZERO) {
                    client.player.setPosition(position);
                }
                return;
            }

            ticksRemaining = duration;
        }
    }

    private static void tick(MinecraftClient client) {
        if (ticksRemaining != 0 && client.player != null && targetPosition != null) {
            // Client Logic:
            // Strictly follow server tick velocity without TPS scaling.
            // If server says 1 block/tick, we move 1 block/tick regardless of actual time
            // passed.
            client.player.setVelocity(targetVelocity);

            // Important: We don't decrement ticksRemaining here strictly for logic control
            // because the Server sends a new packet EVERY tick to refresh state.
            // But we decrement it to handle case where server packets stop coming (lag).
            // Only decrement if positive (finite duration). If negative (infinite), stay
            // negative.
            if (ticksRemaining > 0) {
                ticksRemaining--;
            }
        }
    }
}
