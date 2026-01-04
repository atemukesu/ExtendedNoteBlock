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
    private static double serverTps = 20.0; // Latest TPS from server

    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK
                .register(ClientSmoothMoveManager::tick);
    }

    public static void startMove(Vec3d velocity, int duration, Vec3d position, float tps) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            targetVelocity = velocity;
            targetPosition = position;
            // Update local stored TPS only if valid
            if (tps > 0) {
                serverTps = tps;
            }

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
        if (ticksRemaining > 0 && client.player != null && targetPosition != null) {
            // Client Logic:
            // 1. Calculate Velocity in Client Ticks (20 TPS)
            // V_client = V_server * (ServerTPS / ClientTPS)
            // ClientTPS is fixed at 20.
            double scale = serverTps / 20.0;
            Vec3d scaledVelocity = targetVelocity.multiply(scale);

            // 2. Calculate Position Correction
            // The server sends its CURRENT position every tick.
            // We want to be at 'targetPosition'.
            // Current client position might have drifted.
            Vec3d currentPos = client.player.getPos();
            Vec3d error = targetPosition.subtract(currentPos);

            // 3. Apply Correction
            // We add a fraction of the error to the velocity to smooth it out.
            // Factor 0.3 means we close 30% of the gap per tick.
            // This effectively handles sub-tick interpolation drift and non-integer TPS
            // ratios.
            Vec3d correction = error.multiply(0.3);

            // Apply bounds to correction to prevent crazy snapping if lag spike?
            // For now, simple proportional control is usually fine for movement.

            client.player.setVelocity(scaledVelocity.add(correction));

            // Important: We don't decrement ticksRemaining here strictly for logic control
            // because the Server sends a new packet EVERY tick to refresh state.
            // But we decrement it to handle case where server packets stop coming (lag).
            ticksRemaining--;
        }
    }
}
