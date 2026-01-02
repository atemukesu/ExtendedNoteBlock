package com.atemukesu.extendednoteblock.sound;

import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * 一个可停止、支持动态更新位置、音量和音高的声音实例。
 * 这是高级音符盒功能（如弯音、音量包络、声源移动）的核心音频组件。
 */
public class StoppablePositionalSoundInstance implements TickableSoundInstance {
    private final BlockPos originPos;
    private final SoundEvent soundEvent;
    private final SoundCategory category;

    // 动态属性
    private float volume;
    private float pitch;

    // 基础音高：用于弯音计算的基准值 (Target Note Pitch)
    // 最终播放的 pitch = basePitch * pitchMultiplier (from server curve)
    private float basePitch;

    private final boolean repeat;
    private final int repeatDelay;
    private boolean done = false;
    @Nullable
    private WeightedSoundSet soundSet;

    // 动态位置
    private double x;
    private double y;
    private double z;

    /**
     * 构造函数。
     *
     * @param volume 初始播放音量。
     * @param pitch  初始播放音高（即最终计算出的音高）。
     */
    public StoppablePositionalSoundInstance(SoundEvent soundEvent, SoundCategory category, float volume, float pitch,
                                            BlockPos pos) {
        this.soundEvent = soundEvent;
        this.category = category;
        this.volume = volume;
        this.pitch = pitch;

        // 默认情况下，基础音高等于初始音高。
        // 如果是高级模式，ClientSoundManager 会随后调用 setBasePitch 来覆盖它。
        this.basePitch = pitch;

        this.originPos = pos;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.repeat = false;
        this.repeatDelay = 0;
    }

    @Override
    public Identifier getId() {
        return this.soundEvent.getId();
    }

    @Override
    @Nullable
    public WeightedSoundSet getSoundSet(net.minecraft.client.sound.SoundManager soundManager) {
        if (this.soundSet == null) {
            this.soundSet = soundManager.get(this.getId());
        }
        return this.soundSet;
    }

    @Override
    public Sound getSound() {
        if (this.soundSet == null) {
            return null;
        }
        return this.soundSet.getSound(Random.create());
    }

    @Override
    public SoundCategory getCategory() {
        return this.category;
    }

    @Override
    public boolean isRepeatable() {
        return this.repeat;
    }

    @Override
    public int getRepeatDelay() {
        return this.repeatDelay;
    }

    @Override
    public float getVolume() {
        return this.volume;
    }

    /**
     * 动态更新此声音实例的音量。
     *
     * @param newVolume 新音量 (0.0 - 2.0+)。
     */
    public void setVolume(float newVolume) {
        this.volume = newVolume;
    }

    @Override
    public float getPitch() {
        return this.pitch;
    }

    /**
     * 动态更新此声音实例的实际播放音高。
     * 这允许在播放期间实现弯音效果。
     *
     * @param newPitch 新的最终音高值。
     */
    public void setPitch(float newPitch) {
        this.pitch = newPitch;
    }

    /**
     * 获取此声音实例的基础音高。
     * 基础音高是根据 MIDI 音符计算出的标准音高，不包含弯音偏移。
     *
     * @return 原始基础音高。
     */
    public float getBasePitch() {
        return this.basePitch;
    }

    /**
     * 设置此声音实例的基础音高。
     * 在高级模式下，这应该在创建实例后立即被设置为 (TargetNote / SampleNote) 的比率。
     *
     * @param basePitch 基础音高。
     */
    public void setBasePitch(float basePitch) {
        this.basePitch = basePitch;
    }

    @Override
    public double getX() {
        return this.x;
    }

    @Override
    public double getY() {
        return this.y;
    }

    @Override
    public double getZ() {
        return this.z;
    }

    /**
     * 动态更新此声源的位置。
     *
     * @param x 新 X 坐标。
     * @param y 新 Y 坐标。
     * @param z 新 Z 坐标。
     */
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * 获取此声音的原始方块位置。
     *
     * @return 原始方块位置。
     */
    public BlockPos getOriginPos() {
        return this.originPos;
    }

    @Override
    public AttenuationType getAttenuationType() {
        return AttenuationType.LINEAR;
    }

    @Override
    public boolean isRelative() {
        return false;
    }

    @Override
    public void tick() {
        // Tick 逻辑由 ClientSoundManager 外部处理，
        // 该管理器根据曲线更新音量、音高和位置。
    }

    @Override
    public boolean isDone() {
        return this.done;
    }

    public void stopSound() {
        this.done = true;
    }

    /**
     * 获取方块位置（用于旧版兼容性）。
     *
     * @deprecated 使用 {@link #getOriginPos()} 代替。
     */
    @Deprecated
    public BlockPos getPos() {
        return this.originPos;
    }
}