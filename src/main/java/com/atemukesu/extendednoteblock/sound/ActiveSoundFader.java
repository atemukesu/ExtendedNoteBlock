package com.atemukesu.extendednoteblock.sound;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import com.atemukesu.extendednoteblock.network.ModMessages;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

/**
 * 负责在服务器端管理一个正在播放的声音的生命周期，包括淡入、持续和淡出。
 * 它计算最终的绝对音量，并直接发送给客户端应用。
 * <p>
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

    // ============== Advanced Features v1.4.0 ==============
    private List<Float> pitchBendCurve;
    private List<Float> volumeCurve;
    private List<Vec3d> soundPath;

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

    // ============== Advanced Features Setters v1.4.0 ==============
    public void setPitchBendCurve(List<Float> curve) {
        this.pitchBendCurve = curve;
    }

    public void setVolumeCurve(List<Float> curve) {
        this.volumeCurve = curve;
    }

    public void setSoundPath(List<Vec3d> path) {
        this.soundPath = path;
    }

    /**
     * 每个游戏刻调用一次，用于更新声音的音量。
     *
     * @return 如果声音的生命周期已结束，则返回 true。
     */
    public boolean tick() {
        if (isFinished) return true;

        // 1. 检查是否已经到达寿命终点
        if (currentTick >= sustainTicks) {
            isFinished = true;
            return true;
        }

        float baseMaxVolume = originalVelocity / 127.0f;
        float finalVolume = 0;
        float pitchMultiplier = 1.0f;

        // 2. 获取当前 Tick 对应的预采样索引
        // 直接使用currentTick作为索引，与采样时的逻辑完全一致
        int index;
        if (volumeCurve != null && !volumeCurve.isEmpty()) {
            index = MathHelper.clamp(currentTick, 0, volumeCurve.size() - 1); // 双重保险，防止浮点数精度问题
        } else {
            index = 0; // 当没有volumeCurve时，使用默认索引
        }

        if (volumeCurve != null && !volumeCurve.isEmpty()) {
            float baseMaxVolumeValue = originalVelocity / 127.0f;
            finalVolume = volumeCurve.get(index) * baseMaxVolumeValue;
        } else {
            // 回退到原来的 FadeIn/FadeOut 逻辑
            float volumeMultiplier = 1.0f;
            if (fadeInTicks > 0 && currentTick <= fadeInTicks) {
                // 直接使用 currentTick，确保第1个tick就有声音
                float fadeInProgress = (float) currentTick / (float) fadeInTicks;
                volumeMultiplier = Math.min(volumeMultiplier, fadeInProgress);
            }

            // 计算淡出效果
            if (fadeOutTicks > 0 && sustainTicks > 0) {
                int fadeOutStartTick = sustainTicks - fadeOutTicks;
                if (currentTick > fadeOutStartTick) {
                    int timeIntoFadeOut = currentTick - fadeOutStartTick;

                    // 分母改为 fadeOutTicks + 1.0f
                    float fadeOutProgress = 1.0f - ((float) timeIntoFadeOut / (float) (fadeOutTicks + 1));

                    volumeMultiplier = Math.min(volumeMultiplier, fadeOutProgress);
                }
            }
            finalVolume = baseMaxVolume * volumeMultiplier;
        }

        if (pitchBendCurve != null && !pitchBendCurve.isEmpty()) {
            float pitchOffset = pitchBendCurve.get(index);
            pitchMultiplier = (float) Math.pow(2.0, pitchOffset / 12.0);
        }

        // 3. 处理位置移动
        Vec3d currentPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (soundPath != null && !soundPath.isEmpty()) {
            // 使用currentTick作为索引，与采样时的逻辑完全一致
            int pathIndex;
            if (soundPath != null && !soundPath.isEmpty()) {
                pathIndex = MathHelper.clamp(currentTick, 0, soundPath.size() - 1); // 双重保险，防止浮点数精度问题
            } else {
                pathIndex = 0; // 当没有soundPath时，使用默认索引
            }
            Vec3d offset = soundPath.get(pathIndex);
            currentPos = currentPos.add(offset);
        }

        // 确保音量在有效范围内
        finalVolume = Math.max(0.0f, Math.min(finalVolume, 2.0f));

        // 4. 发送综合更新
        ModMessages.sendAdvancedUpdateToClients(world, pos, soundId, finalVolume, pitchMultiplier, currentPos.x, currentPos.y, currentPos.z);

        // 5. 增加计数
        currentTick++;
        return false;
    }

    // 线性插值辅助方法 - 根据插值计算规范进行改进
    private float interpolate(List<Float> curve, float progress) {
        if (curve.isEmpty()) return 0;
        if (curve.size() == 1) return curve.get(0);

        // 将进度映射到曲线数据点索引
        float floatIdx = progress * (curve.size() - 1);
        int idx = (int) Math.floor(floatIdx);
        float nextProgress = floatIdx - idx;

        // 确保索引不越界
        if (idx >= curve.size() - 1) return curve.get(curve.size() - 1);
        if (idx < 0) return curve.get(0);

        // 线性插值计算
        float leftValue = curve.get(idx);
        float rightValue = curve.get(idx + 1);
        return leftValue + nextProgress * (rightValue - leftValue);
    }

    // 向量插值辅助方法
    private Vec3d interpolateVec(List<Vec3d> curve, float progress) {
        if (curve.isEmpty()) return Vec3d.ZERO;
        if (curve.size() == 1) return curve.get(0);

        float floatIdx = progress * (curve.size() - 1);
        int idx = (int) Math.floor(floatIdx);
        float nextProgress = floatIdx - idx;

        // 确保索引不越界
        if (idx >= curve.size() - 1) return curve.get(curve.size() - 1);
        if (idx < 0) return curve.get(0);

        Vec3d start = curve.get(idx);
        Vec3d end = curve.get(idx + 1);
        return start.lerp(end, nextProgress);
    }

    public static class SoundState {
        public final float volume;
        public final float pitch;
        public final double x, y, z;

        public SoundState(float v, float p, double x, double y, double z) {
            this.volume = v;
            this.pitch = p;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public SoundState calculateStateAt(float progress) {
        float baseMaxVolume = originalVelocity / 127.0f;

        // 1. 音量 (兼容逻辑)
        float volume;
        if (volumeCurve != null && !volumeCurve.isEmpty()) {
            // 使用与采样时一致的索引计算方式
            int index;
            if (sustainTicks > 1) {
                index = (int) (progress * (sustainTicks - 1));
            } else {
                index = 0;
            }
            index = MathHelper.clamp(index, 0, volumeCurve.size() - 1);
            volume = volumeCurve.get(index) * baseMaxVolume;
        } else {
            // 兼容原有的 FadeIn 逻辑
            float multiplier = 1.0f;
            int currentT = (int) (progress * sustainTicks);
            if (fadeInTicks > 0 && currentT <= fadeInTicks) {
                multiplier = (float) currentT / (float) fadeInTicks;
            }
            volume = baseMaxVolume * multiplier;
        }

        // 2. 音高 (兼容逻辑)
        float pitchMultiplier = 1.0f;
        if (pitchBendCurve != null && !pitchBendCurve.isEmpty()) {
            // 使用与采样时一致的索引计算方式
            int index;
            if (sustainTicks > 1) {
                index = (int) (progress * (sustainTicks - 1));
            } else {
                index = 0;
            }
            index = MathHelper.clamp(index, 0, pitchBendCurve.size() - 1);
            float semitones = pitchBendCurve.get(index);
            pitchMultiplier = (float) Math.pow(2.0, semitones / 12.0);
        }

        // 3. 位置 (兼容逻辑)
        double curX = pos.getX() + 0.5;
        double curY = pos.getY() + 0.5;
        double curZ = pos.getZ() + 0.5;
        if (soundPath != null && !soundPath.isEmpty()) {
            // 使用与采样时一致的索引计算方式
            int pathIndex;
            if (sustainTicks > 1) {
                pathIndex = (int) (progress * (sustainTicks - 1));
            } else {
                pathIndex = 0;
            }
            pathIndex = MathHelper.clamp(pathIndex, 0, soundPath.size() - 1);
            Vec3d offset = soundPath.get(pathIndex);
            curX += offset.x;
            curY += offset.y;
            curZ += offset.z;
        }

        return new SoundState(volume, pitchMultiplier, curX, curY, curZ);
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