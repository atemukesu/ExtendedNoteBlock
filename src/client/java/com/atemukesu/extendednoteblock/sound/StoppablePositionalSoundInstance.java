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

public class StoppablePositionalSoundInstance implements TickableSoundInstance {
    private final BlockPos pos;
    private final SoundEvent soundEvent;
    private final SoundCategory category;
    private float volume;
    private final float pitch;
    private final boolean repeat;
    private final int repeatDelay;
    private boolean done = false;
    @Nullable
    private WeightedSoundSet soundSet;

    public StoppablePositionalSoundInstance(SoundEvent soundEvent, SoundCategory category, float volume, float pitch,
            BlockPos pos, int sustainTicks) {
        this.soundEvent = soundEvent;
        this.category = category;
        this.volume = volume;
        this.pitch = pitch;
        this.pos = pos;
        this.repeat = true;
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

    public void setVolume(float newVolume) {
        this.volume = newVolume;
    }

    @Override
    public float getPitch() {
        return this.pitch;
    }

    @Override
    public double getX() {
        return this.pos.getX() + 0.5;
    }

    @Override
    public double getY() {
        return this.pos.getY() + 0.5;
    }

    @Override
    public double getZ() {
        return this.pos.getZ() + 0.5;
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
    }

    @Override
    public boolean isDone() {
        return this.done;
    }

    public void stopSound() {
        this.done = true;
    }

    public BlockPos getPos() {
        return this.pos;
    }
}