package com.atemukesu.extendednoteblock.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side smooth move manager.
 * Refactored to match ActiveSoundFader's architecture:
 * 1. Server Authoritative: Strictly applies the Position and Velocity sent by
 * Server.
 * 2. Decoupled from Client Tick: Logic runs on packet receipt (Event Driven),
 * not on local tick loop.
 */
public class ClientSmoothMoveManager {

    /**
     * Called when a SmoothMove packet is received from server.
     * Acts as a direct state update (like ClientSoundManager.updateAdvanced).
     */
    public static void startMove(Vec3d velocity, Vec3d position) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {

            // 1. Server Authoritative Position Update
            if (position != null) {
                client.player.setPosition(position);
            }

            // 2. Velocity Update
            client.player.setVelocity(velocity);
        }
    }

    public static void init() {
        // No client tick listener needed anymore.
        // Logic is fully driven by ModMessages.SMOOTH_MOVE_ID packets.
    }
}
