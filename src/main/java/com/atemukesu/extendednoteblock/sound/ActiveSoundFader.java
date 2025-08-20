package com.atemukesu.extendednoteblock.sound;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import com.atemukesu.extendednoteblock.network.ModMessages;

import java.util.UUID;

public class ActiveSoundFader {
    private final ServerWorld world;
    private final BlockPos pos;
    private final UUID soundId;
    private final int originalVelocity;
    private final int sustainTicks;
    private final int fadeInTicks;
    private final int fadeOutTicks;

    private int currentTick = 0;
    private float currentVolume = 0.0f;
    private boolean isFinished = false;
    private boolean isFadingOut = false;
    private int fadeOutStartTick = -1;

    public ActiveSoundFader(ServerWorld world, BlockPos pos, UUID soundId, int velocity,
            int sustainTicks, int fadeInTicks, int fadeOutTicks) {
        this.world = world;
        this.pos = pos;
        this.soundId = soundId;
        this.originalVelocity = velocity;
        this.sustainTicks = sustainTicks;
        this.fadeInTicks = Math.max(1, fadeInTicks); // 确保至少为1，避免除零
        this.fadeOutTicks = Math.max(1, fadeOutTicks); // 确保至少为1，避免除零

        // 如果没有淡入，直接设置为最大音量
        if (fadeInTicks <= 0) {
            this.currentVolume = velocity / 127.0f;
        }
    }

    public boolean tick() {
        if (isFinished) {
            return true;
        }

        currentTick++;

        // 淡入阶段
        if (!isFadingOut && fadeInTicks > 0 && currentTick <= fadeInTicks) {
            float fadeInProgress = (float) currentTick / fadeInTicks;
            currentVolume = (originalVelocity / 127.0f) * fadeInProgress;

            // 发送音量更新到客户端
            ModMessages.sendUpdateVolumeToClients(world, pos, soundId, currentVolume);
            return false;
        }

        // 持续阶段 - 检查是否应该开始淡出
        if (!isFadingOut) {
            // 如果没有设置淡入，确保音量正确
            if (fadeInTicks <= 0) {
                currentVolume = originalVelocity / 127.0f;
            }

            // 计算总的声音持续时间（不包括淡出时间）
            int totalSustainTime = fadeInTicks + sustainTicks;

            if (currentTick >= totalSustainTime) {
                // 开始淡出
                startFadeOut();
                return false;
            }

            // 在持续阶段保持音量不变
            return false;
        }

        // 淡出阶段
        if (isFadingOut && fadeOutStartTick != -1) {
            int fadeOutProgress = currentTick - fadeOutStartTick;

            if (fadeOutProgress >= fadeOutTicks) {
                // 淡出完成
                isFinished = true;
                return true;
            }

            // 计算淡出音量
            float fadeOutRatio = 1.0f - ((float) fadeOutProgress / fadeOutTicks);
            currentVolume = (originalVelocity / 127.0f) * fadeOutRatio;

            // 发送音量更新到客户端
            ModMessages.sendUpdateVolumeToClients(world, pos, soundId, currentVolume);
            return false;
        }

        return false;
    }

    public void startFadeOut() {
        if (!isFadingOut) {
            isFadingOut = true;
            fadeOutStartTick = currentTick;

            // 如果没有设置淡出时间，立即结束
            if (fadeOutTicks <= 0) {
                isFinished = true;
            }
        }
    }

    public ServerWorld getWorld() {
        return world;
    }

    public BlockPos getPos() {
        return pos;
    }

    public UUID getSoundId() {
        return soundId;
    }

    public float getCurrentVolume() {
        return currentVolume;
    }

    public boolean isFinished() {
        return isFinished;
    }
}