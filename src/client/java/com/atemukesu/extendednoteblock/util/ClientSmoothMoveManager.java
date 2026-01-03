package com.atemukesu.extendednoteblock.util;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class ClientSmoothMoveManager {
    private static Vec3d targetVelocity = Vec3d.ZERO;
    private static int ticksRemaining = 0;

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(ClientSmoothMoveManager::tick);
    }

    public static void startMove(Vec3d velocity, int duration, Vec3d position) {
        targetVelocity = velocity;
        ticksRemaining = duration;

        // Sync position if player exists
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            // Only sync if significant difference to avoid jitter?
            // Or always sync to enforce server authority strictly?
            // "update target entity position like extended note block update" implies
            // strict sync.
            client.player.setPosition(position);
            client.player.setVelocity(velocity);
        }
    }

    private static void tick(MinecraftClient client) {
        // ticksRemaining == 0 means stop.
        // ticksRemaining < 0 means infinite.
        // ticksRemaining > 0 means finite duration.
        boolean shouldMove = ticksRemaining != 0;

        if (shouldMove && client.player != null) {
            Entity player = client.player;
            player.setVelocity(targetVelocity);
            // Client-side velocity application needs to be persistent or it gets reset by
            // gravity/friction quickly
            // By applying it every tick, we override natural movement logic to some extent,
            // simulating the "smooth move"
            if (ticksRemaining > 0) {
                ticksRemaining--;
            }
        }
    }
}
