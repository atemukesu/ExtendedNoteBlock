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
        tasks.removeIf(t -> t.entity == entity);
        tasks.add(new MoveTask(entity, velocity, duration));
    }

    public static void stopMove(Entity entity) {
        boolean removed = tasks.removeIf(t -> t.entity == entity);
        if (removed && entity instanceof ServerPlayerEntity player) {
            entity.noClip = false; // Restore clip
            ModMessages.sendSmoothMoveToClient(player, player.getPos(), true);
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

            // 任务完成检查
            if (task.isFinished()) {
                it.remove();
                task.cleanup(); // Restore noClip
                if (task.entity instanceof ServerPlayerEntity player) {
                    ModMessages.sendSmoothMoveToClient(player, player.getPos(), true);
                }
                continue;
            }

            // 执行逻辑
            task.tick();

            // 每 Tick 强制同步：覆盖客户端所有的预测/回滚
            if (task.entity instanceof ServerPlayerEntity player) {
                // isStop = false
                ModMessages.sendSmoothMoveToClient(player, task.getTrustedPos(), false);
            }
        }
    }

    private static class MoveTask {
        final Entity entity;
        final Vec3d velocity;
        final int duration;

        Vec3d trustedPos;

        final Vec3d startPos;
        final Vec3d targetPos;

        int ticksPassed;

        MoveTask(Entity entity, Vec3d velocity, int duration) {
            this.entity = entity;
            this.velocity = velocity;
            this.duration = duration;
            this.ticksPassed = 0;

            // 初始化锚点
            this.startPos = entity.getPos();
            this.trustedPos = startPos; // 初始信任位置

            if (duration > 0) {
                this.targetPos = startPos.add(velocity.multiply(duration));
            } else {
                this.targetPos = null;
            }

            // Enable spectator-like movement
            entity.noClip = true;
        }

        void cleanup() {
            entity.noClip = false;
        }

        void tick() {
            if (entity.isRemoved()) {
                ticksPassed = duration;
                return;
            }

            ticksPassed++;
            Vec3d newPos;

            if (duration > 0) {
                // 有限移动：插值计算（不受 TPS 波动影响总时长，受 TPS 影响实时速度）
                double t = (double) ticksPassed / duration;
                if (t > 1.0)
                    t = 1.0;
                newPos = startPos.lerp(targetPos, t);
            } else {
                // 无限移动：基于内部 trustedPos 累加（不受客户端回滚影响）
                // 这里的 trustedPos 是上一 tick 计算出的理论位置
                newPos = trustedPos.add(velocity);
            }

            // 更新内部信任坐标
            this.trustedPos = newPos;

            // 强行应用到实体（覆盖 Minecraft 所有的物理/碰撞/客户端修正）
            entity.setPosition(newPos);
            entity.velocityModified = true;

            // 关键：对于 ServerPlayer，我们需要不断重置它的 fallDistance 等，防止累计摔落伤害或动作异常
            entity.fallDistance = 0;
        }

        Vec3d getTrustedPos() {
            return trustedPos;
        }

        boolean isFinished() {
            return duration > 0 && ticksPassed >= duration;
        }
    }
}