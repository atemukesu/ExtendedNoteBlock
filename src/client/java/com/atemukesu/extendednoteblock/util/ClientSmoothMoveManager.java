package com.atemukesu.extendednoteblock.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side smooth move manager.
 * This is now purely server-driven: the server sends position updates every
 * tick,
 * and this class just applies them. No client-side tick logic.
 */
public class ClientSmoothMoveManager {

    /**
     * Initialize the manager. No longer registers tick events - movement is now
     * purely driven by server packets.
     */
    public static void init() {
        // No tick listener needed anymore.
        // All movement is server-authoritative via packets.
    }

    /**
     * Called when a smooth move packet is received from the server.
     * 
     * @param velocity The velocity to apply
     * @param duration Duration remaining (used for stop detection: 0 = stop)
     * @param position The server-authoritative position to sync to
     */
    public static void startMove(Vec3d velocity, int duration, Vec3d position) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            if (duration == 0) {
                // Stop command - just reset velocity
                client.player.setVelocity(Vec3d.ZERO);
            } else {
                // Active movement - sync position and velocity from server
                client.player.setPosition(position);
                client.player.setVelocity(velocity);
            }
        }
    }
}
