package com.atemukesu.extendednoteblock.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientSoundManager {
    private static final Map<BlockPos, StoppablePositionalSoundInstance> PLAYING_SOUNDS = new ConcurrentHashMap<>();
    private static final int DRUM_KIT_INSTRUMENT_ID = 128;

    public static void playSound(BlockPos pos, int instrumentId, int note, int velocity, int sustainTicks) {
        stopSound(pos);
        float pitch;
        int soundKey;
        if (instrumentId == DRUM_KIT_INSTRUMENT_ID) {
            pitch = 1.0f;
            soundKey = note;
        } else {
            int baseNote = (note / 12) * 12;
            baseNote = Math.max(0, Math.min(120, baseNote));
            int semitoneDifference = note - baseNote;
            pitch = (float) Math.pow(2.0, semitoneDifference / 12.0);
            soundKey = baseNote;
        }
        Identifier soundId = new Identifier("extendednoteblock", "notes." + instrumentId + "." + soundKey);
        SoundEvent soundEvent = SoundEvent.of(soundId);
        float volume = (Math.max(0.0f, Math.min(1.0f, velocity / 127.0f)));
        StoppablePositionalSoundInstance soundInstance = new StoppablePositionalSoundInstance(
                soundEvent, SoundCategory.RECORDS, volume, pitch, pos, sustainTicks);
        PLAYING_SOUNDS.put(pos, soundInstance);
        MinecraftClient.getInstance().getSoundManager().play(soundInstance);
    }

    public static void stopSound(BlockPos pos) {
        StoppablePositionalSoundInstance existingSound = PLAYING_SOUNDS.remove(pos);
        if (existingSound != null) {
            existingSound.stopSound();
        }
    }
}