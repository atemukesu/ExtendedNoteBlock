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

        // 获取当前激活的音色包信息
        SoundPackInfo activePack = SoundPackManager.getInstance().getActivePackInfo();
        boolean isFullRender = activePack != null && activePack.full(); 

        float pitch;
        int soundKey;

        // 鼓组的逻辑保持不变
        if (instrumentId == DRUM_KIT_INSTRUMENT_ID) {
            pitch = 1.0f;
            soundKey = note;
        }
        // 如果是全渲染音色包，直接使用音符作为key，音高为1.0
        else if (isFullRender) {
            pitch = 1.0f;
            soundKey = note; // 直接使用原始音高作为声音文件的key
        }
        // 否则，使用原有的八度音阶 + 变调逻辑
        else {
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
