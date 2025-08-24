package com.atemukesu.extendednoteblock.sound;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import com.atemukesu.extendednoteblock.network.ModMessages;

import java.util.UUID;

/**
 * 负责在服务器端管理一个正在播放的声音的生命周期，包括淡入、持续和淡出。
 * 它计算最终的绝对音量，并直接发送给客户端应用。
 *
 * - sustainTicks: 代表音符播放的总时长。
 * - fadeInTicks: 在总时长的开头部分进行淡入，从音量0平滑过渡到最大音量。
 * - fadeOutTicks: 在总时长的结尾部分进行淡出，从最大音量平滑过渡到0。
 * - Velocity: 用于计算基础的最大音量。
 */
public class ActiveSoundFader {
    private final ServerWorld world;
    private final BlockPos pos;
    private final UUID soundId;
    private final int originalVelocity;
    private final int sustainTicks;
    private final int fadeInTicks;
    private final int fadeOutTicks;

    private int currentTick = 0;
    private float currentAbsoluteVolume = 0.0f;
    private boolean isFinished = false;

    // 用于处理由外部事件（如红石信号关闭）触发的强制淡出
    private boolean isFadingOutForced = false;
    private int forcedFadeOutStartTick = -1;
    private float volumeOnForcedFadeOut = 1.0f;

    public ActiveSoundFader(ServerWorld world, BlockPos pos, UUID soundId, int velocity,
            int sustainTicks, int fadeInTicks, int fadeOutTicks) {
        this.world = world;
        this.pos = pos;
        this.soundId = soundId;
        this.originalVelocity = velocity;
        this.sustainTicks = sustainTicks;
        this.fadeInTicks = fadeInTicks;
        this.fadeOutTicks = fadeOutTicks;
    }

    /**
     * 每个游戏刻调用一次，用于更新声音的音量。
     * 
     * @return 如果声音的生命周期已结束，则返回 true。
     */
    public boolean tick() {
        if (isFinished) {
            return true;
        }

        currentTick++;

        float baseMaxVolume = originalVelocity / 127.0f;
        float volumeMultiplier = 1.0f;

        if (isFadingOutForced) {
            int fadeOutProgress = currentTick - forcedFadeOutStartTick;

            if (fadeOutTicks <= 0 || fadeOutProgress >= fadeOutTicks) {
                isFinished = true;
                return true;
            }

            float fadeOutRatio = 1.0f - ((float) fadeOutProgress / fadeOutTicks);
            this.currentAbsoluteVolume = volumeOnForcedFadeOut * fadeOutRatio;

            ModMessages.sendUpdateVolumeToClients(world, pos, soundId, this.currentAbsoluteVolume);
            return false;
        }

        if (sustainTicks > 0 && currentTick > sustainTicks) {
            isFinished = true;
            return true;
        }

        // 计算淡入效果
        if (fadeInTicks > 0 && currentTick <= fadeInTicks) {
            float fadeInProgress = (float) (currentTick - 1) / (float) fadeInTicks;
            volumeMultiplier = Math.min(volumeMultiplier, fadeInProgress);
        }

        // 计算淡出效果
        if (fadeOutTicks > 0 && sustainTicks > 0) {
            int fadeOutStartTick = sustainTicks - fadeOutTicks;
            if (currentTick > fadeOutStartTick) {
                int timeIntoFadeOut = currentTick - fadeOutStartTick;
                float fadeOutProgress = 1.0f - ((float) timeIntoFadeOut / (float) fadeOutTicks);
                volumeMultiplier = Math.min(volumeMultiplier, fadeOutProgress);
            }
        }

        // 当 fadeInTicks + fadeOutTicks > sustainTicks 时

        // 计算最终的绝对音量
        this.currentAbsoluteVolume = baseMaxVolume * volumeMultiplier;

        // 确保音量在有效范围内
        this.currentAbsoluteVolume = Math.max(0.0f, Math.min(this.currentAbsoluteVolume, 1.0f));

        // 将最终计算出的绝对音量发送到客户端
        ModMessages.sendUpdateVolumeToClients(world, pos, soundId, this.currentAbsoluteVolume);

        return false;
    }

    /**
     * 强制开始淡出过程，通常在音符被外部事件（如红石信号关闭）中断时调用。
     */
    public void startFadeOut() {
        if (!isFadingOutForced) {
            isFadingOutForced = true;
            forcedFadeOutStartTick = currentTick;
            volumeOnForcedFadeOut = this.currentAbsoluteVolume;

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

    public boolean isFinished() {
        return isFinished;
    }
}