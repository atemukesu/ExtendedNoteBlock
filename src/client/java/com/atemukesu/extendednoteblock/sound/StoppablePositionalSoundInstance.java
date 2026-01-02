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
 * A stoppable, positional sound instance that supports dynamic updates to
 * volume, pitch, and position.
 * This is the core audio component for advanced note block features like pitch
 * bend, volume curves,
 * and sound source movement.
 */
public class StoppablePositionalSoundInstance implements TickableSoundInstance {
    private final BlockPos originPos;
    private final SoundEvent soundEvent;
    private final SoundCategory category;
    private float volume;
    private float pitch;
    private final boolean repeat;
    private final int repeatDelay;
    private boolean done = false;
    @Nullable
    private WeightedSoundSet soundSet;

    // Store the original pitch for bend calculations
    private final float basePitch;

    // Dynamic position for sound source movement
    private double x;
    private double y;
    private double z;

    public StoppablePositionalSoundInstance(SoundEvent soundEvent, SoundCategory category, float volume, float pitch,
            BlockPos pos) {
        this.soundEvent = soundEvent;
        this.category = category;
        this.volume = volume;
        this.pitch = pitch;
        this.basePitch = pitch; // Store the original pitch
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
     * Updates the volume of this sound instance dynamically.
     *
     * @param newVolume The new volume (0.0 - 2.0+).
     */
    public void setVolume(float newVolume) {
        this.volume = newVolume;
    }

    @Override
    public float getPitch() {
        return this.pitch;
    }

    /**
     * Updates the pitch of this sound instance dynamically.
     * This allows for pitch bend effects during playback.
     *
     * @param newPitch The new pitch multiplier (0.5 = octave down, 2.0 = octave
     *                 up).
     */
    public void setPitch(float newPitch) {
        this.pitch = newPitch;
    }

    /**
     * Gets the original base pitch of this sound instance.
     * This is used for pitch bend calculations where the original pitch is multiplied by a bend factor.
     *
     * @return The original base pitch.
     */
    public float getBasePitch() {
        return this.basePitch;
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
     * Updates the position of this sound source dynamically.
     * This allows for sound source movement effects during playback.
     *
     * @param x The new X coordinate.
     * @param y The new Y coordinate.
     * @param z The new Z coordinate.
     */
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Gets the original block position of this sound.
     *
     * @return The origin block position.
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
        // Tick logic is handled externally by the ClientSoundManager
        // which updates volume, pitch, and position based on curves
    }

    @Override
    public boolean isDone() {
        return this.done;
    }

    public void stopSound() {
        this.done = true;
    }

    /**
     * Gets the block position (for legacy compatibility).
     * 
     * @deprecated Use {@link #getOriginPos()} instead.
     */
    @Deprecated
    public BlockPos getPos() {
        return this.originPos;
    }
}