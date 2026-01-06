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

    public static void init() {
        ServerTickEvents.START_SERVER_TICK.register(SmoothMoveManager::tick);
    }

    public static void startMove(Entity entity, Vec3d velocity, int duration) {
        // Stop existing task for this entity if any
        tasks.removeIf(t -> t.entity == entity);

        // Create new task. If duration > 0, we calculate target and use interpolation.
        // If duration < 0, we use velocity integration.
        tasks.add(new MoveTask(entity, velocity, duration));
    }

    public static void stopMove(Entity entity) {
        boolean removed = tasks.removeIf(t -> t.entity == entity);
        if (removed && entity instanceof ServerPlayerEntity player) {
            ModMessages.sendSmoothMoveToClient(player, player.getPos());
        }
    }

    public static boolean isMoving(Entity entity) {
        return tasks.stream().anyMatch(t -> t.entity == entity);
    }

    private static void tick(MinecraftServer server) {
        if (tasks.isEmpty())
            return;

        Iterator<MoveTask> it = tasks.iterator();
        while (it.hasNext()) {
            MoveTask task = it.next();
            if (task.isFinished()) {
                it.remove();

                if (task.entity instanceof ServerPlayerEntity player) {
                    ModMessages.sendSmoothMoveToClient(player, player.getPos());
                }
                continue;
            }

            task.tick();

            // Send packet every tick
            if (task.entity instanceof ServerPlayerEntity player) {
                // Send Authoritative Position only.
                ModMessages.sendSmoothMoveToClient(player, player.getPos());
            }
        }
    }

    private static class MoveTask {
        final Entity entity;
        final Vec3d startPos;
        final Vec3d targetPos; // Used if duration > 0
        final Vec3d velocity; // Used if duration < 0 (infinite)
        final int duration;
        int ticksPassed;

        MoveTask(Entity entity, Vec3d velocity, int duration) {
            this.entity = entity;
            this.startPos = entity.getPos();
            this.velocity = velocity;
            this.duration = duration;
            this.ticksPassed = 0;

            if (duration > 0) {
                this.targetPos = startPos.add(velocity.multiply(duration));
            } else {
                this.targetPos = null;
            }
        }

        void tick() {
            if (entity.isRemoved()) {
                ticksPassed = duration; // Force finish
                return;
            }

            ticksPassed++;

            Vec3d newPos;
            if (duration > 0) {
                // Absolute Interpolation
                // t goes from 0 to 1
                double t = (double) ticksPassed / duration;
                if (t > 1.0)
                    t = 1.0;
                newPos = startPos.lerp(targetPos, t);
            } else {
                // Infinite movement: integration
                newPos = entity.getPos().add(velocity);
            }

            entity.setPosition(newPos);
            entity.velocityModified = true;
        }

        boolean isFinished() {
            return duration > 0 && ticksPassed >= duration;
        }
    }
}
