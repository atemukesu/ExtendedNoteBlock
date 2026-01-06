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

        // We use the raw velocity (blocks/tick) directly.
        tasks.add(new MoveTask(entity, velocity, duration));
    }

    public static void stopMove(Entity entity) {
        boolean removed = tasks.removeIf(t -> t.entity == entity);
        if (removed && entity instanceof ServerPlayerEntity player) {
            ModMessages.sendSmoothMoveToClient(player, Vec3d.ZERO, player.getPos());
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
                    ModMessages.sendSmoothMoveToClient(player, Vec3d.ZERO, player.getPos());
                }
                continue;
            }

            task.tick();

            // Send packet every tick
            if (task.entity instanceof ServerPlayerEntity player) {
                // Send current Velocity and Authoritative Position.
                ModMessages.sendSmoothMoveToClient(player, task.velocity, player.getPos());
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
