package com.atemukesu.extendednoteblock.util;

import com.atemukesu.extendednoteblock.network.ModMessages;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SmoothMoveManager {
    private static final List<MoveTask> tasks = new ArrayList<>();

    // TPS Calculation variables
    private static long lastTickTime = 0;
    private static final double[] tickIntervals = new double[10]; // Keep last 10 ticks for smoothing
    private static int tickIndex = 0;
    private static double smoothedTps = 20.0;

    public static void init() {
        ServerTickEvents.START_SERVER_TICK.register(SmoothMoveManager::tick);
    }

    public static void startMove(Entity entity, Vec3d velocity, int duration, float tps) {
        // Stop existing task for this entity if any
        tasks.removeIf(t -> t.entity == entity);

        // We use the raw velocity (blocks/tick) directly.
        // The 'tps' argument here is ignored for server-side movement logic.
        tasks.add(new MoveTask(entity, velocity, duration));
    }

    public static void startMove(Entity entity, Vec3d velocity, int duration) {
        startMove(entity, velocity, duration, 20.0f);
    }

    public static void stopMove(Entity entity) {
        boolean removed = tasks.removeIf(t -> t.entity == entity);
        if (removed && entity instanceof ServerPlayerEntity player) {
            // Send duration 0 to stop client side
            ModMessages.sendSmoothMoveToClient(player, Vec3d.ZERO, 0, player.getPos());
        }
    }

    public static boolean isMoving(Entity entity) {
        return tasks.stream().anyMatch(t -> t.entity == entity);
    }

    private static void tick(MinecraftServer server) {
        // 1. Calculate real TPS using rolling average
        long now = System.nanoTime();
        if (lastTickTime != 0) {
            long diff = now - lastTickTime;
            // Store interval in seconds
            tickIntervals[tickIndex] = diff / 1_000_000_000.0;
            tickIndex = (tickIndex + 1) % tickIntervals.length;

            // Calculate average interval
            double sum = 0;
            int samples = 0;
            for (double interval : tickIntervals) {
                if (interval > 0) {
                    sum += interval;
                    samples++;
                }
            }

            if (samples > 0) {
                double avgInterval = sum / samples;
                if (avgInterval > 0) {
                    smoothedTps = 1.0 / avgInterval;
                }
            }
        }
        lastTickTime = now;

        if (tasks.isEmpty())
            return;

        Iterator<MoveTask> it = tasks.iterator();
        while (it.hasNext()) {
            MoveTask task = it.next();
            if (task.isFinished()) {
                it.remove();

                if (task.entity instanceof ServerPlayerEntity player) {
                    ModMessages.sendSmoothMoveToClient(player, Vec3d.ZERO, 0, player.getPos());
                }
                continue;
            }

            task.tick();

            // Send packet every tick
            if (task.entity instanceof ServerPlayerEntity player) {
                // Determine TPS to send.
                // If the array is not fully populated yet (samples < 10), smoothedTps might be
                // unstable,
                // but usually fine after 0.5s.
                // We send the computed smoothedTps.
                ModMessages.sendSmoothMoveToClient(player, task.velocity, task.ticksRemaining, player.getPos(),
                        (float) smoothedTps);
            }
        }
    }

    private static class MoveTask {
        final Entity entity;
        final Vec3d velocity;
        int ticksRemaining;

        MoveTask(Entity entity, Vec3d velocity, int duration) {
            this.entity = entity;
            this.velocity = velocity;
            this.ticksRemaining = duration;
        }

        void tick() {
            if (entity.isRemoved()) {
                ticksRemaining = 0;
                return;
            }

            // Apply raw velocity (blocks per tick)
            entity.setPosition(entity.getPos().add(velocity));
            entity.velocityModified = true;

            // duration < 0 means infinite
            if (ticksRemaining > 0) {
                ticksRemaining--;
            }
        }

        boolean isFinished() {
            return ticksRemaining == 0;
        }
    }
}
