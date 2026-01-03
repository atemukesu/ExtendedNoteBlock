package com.atemukesu.extendednoteblock.util;

import com.atemukesu.extendednoteblock.network.ModMessages;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SmoothMoveManager {
    private static final List<MoveTask> tasks = new ArrayList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    public static void startMove(Entity entity, Vec3d velocity, int duration) {
        // Stop existing task for this entity if any
        tasks.removeIf(t -> t.entity == entity);
        tasks.add(new MoveTask(entity, velocity, duration));

        // Sync to client if player
        if (entity instanceof ServerPlayerEntity player) {
            ModMessages.sendSmoothMoveToClient(player, velocity, duration);
        }
    }

    public static void stopMove(Entity entity) {
        boolean removed = tasks.removeIf(t -> t.entity == entity);
        if (removed && entity instanceof ServerPlayerEntity player) {
            // Send duration 0 to stop client side
            ModMessages.sendSmoothMoveToClient(player, Vec3d.ZERO, 0);
        }
    }

    public static boolean isMoving(Entity entity) {
        return tasks.stream().anyMatch(t -> t.entity == entity);
    }

    private static void tick() {
        Iterator<MoveTask> it = tasks.iterator();
        while (it.hasNext()) {
            MoveTask task = it.next();
            if (task.isFinished()) {
                it.remove();

                // Ensure client stops prediction when server task finishes naturally
                if (task.entity instanceof ServerPlayerEntity player) {
                    ModMessages.sendSmoothMoveToClient(player, Vec3d.ZERO, 0);
                }
                continue;
            }
            task.tick();
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

            entity.setVelocity(velocity);
            entity.velocityModified = true;

            // duration < 0 means infinite, so we don't decrement
            if (ticksRemaining > 0) {
                ticksRemaining--;
            }
        }

        boolean isFinished() {
            // 0 means finished. < 0 means infinite (not finished).
            return ticksRemaining == 0;
        }
    }
}
