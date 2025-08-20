package com.atemukesu.extendednoteblock.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientSoundManager {
    private static final Map<UUID, StoppablePositionalSoundInstance> PLAYING_SOUNDS = new ConcurrentHashMap<>();
    private static final int DRUM_KIT_INSTRUMENT_ID = 128;

    public static void playSound(BlockPos pos, UUID soundId, int instrumentId, int note, int velocity) {
        stopSound(soundId); // 先用ID停止，确保不会重复
        // 获取当前激活的音色包信息
        SoundPackInfo activePack = SoundPackManager.getInstance().getActivePackInfo();
        boolean isFullRender = activePack != null && activePack.full();

        float pitch;
        int soundKey;

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

        Identifier soundIdentifier  = new Identifier("extendednoteblock", "notes." + instrumentId + "." + soundKey);
        SoundEvent soundEvent = SoundEvent.of(soundIdentifier);
        // float volume = (Math.max(0.0f, Math.min(1.0f, velocity / 127.0f)));
        StoppablePositionalSoundInstance soundInstance = new StoppablePositionalSoundInstance(
                soundEvent, SoundCategory.RECORDS, 0.001f, pitch, pos, 0); // sustainTicks 不再由客户端管理

        PLAYING_SOUNDS.put(soundId, soundInstance);
        MinecraftClient.getInstance().getSoundManager().play(soundInstance);

    }

    public static void updateVolume(UUID soundId, float volume) {
        StoppablePositionalSoundInstance soundInstance = PLAYING_SOUNDS.get(soundId);
        if (soundInstance != null) {
            soundInstance.setVolume(volume);
        }
    }

    public static void stopSound(UUID soundId) {
        StoppablePositionalSoundInstance existingSound = PLAYING_SOUNDS.remove(soundId);
        if (existingSound != null) {
            existingSound.stopSound();
        }
    }

    // 暂时保留，但服务器逻辑不会用它
    // 兼容性而保留
    public static void stopSound(BlockPos pos) {
        PLAYING_SOUNDS.values().removeIf(sound -> {
            if (sound.getPos().equals(pos)) {
                sound.stopSound();
                return true;
            }
            return false;
        });
    }
}
