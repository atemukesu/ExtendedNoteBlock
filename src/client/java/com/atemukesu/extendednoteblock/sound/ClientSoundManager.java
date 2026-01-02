package com.atemukesu.extendednoteblock.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端声音管理器。
 * 负责接收服务器的播放指令，并根据当前激活的音色包智能地播放声音。
 */
public class ClientSoundManager {
    /** 存储当前正在播放的声音实例，以便可以更新音量或停止它们。 */
    private static final Map<UUID, StoppablePositionalSoundInstance> PLAYING_SOUNDS = new ConcurrentHashMap<>();
    /** General MIDI 标准中，鼓组的乐器ID。这个乐器有特殊的处理逻辑。 */
    private static final int DRUM_KIT_INSTRUMENT_ID = 128;

    /**
     * 播放一个扩展音符盒的声音。
     * 这是整个客户端声音处理的核心方法。
     *
     * @param pos           声音播放的位置。
     * @param soundId       由服务器生成的声音唯一ID。
     * @param instrumentId  目标乐器ID。
     * @param note          目标MIDI音高 (0-127)。
     * @param velocity      力度 (0-127)，用于计算基础音量。
     * @param initialVolume 服务器计算出的初始绝对音量 (考虑了淡入)。
     */
    public static void playSound(BlockPos pos, UUID soundId, int instrumentId, int note, int velocity,
            float initialVolume) {
        // 在播放新声音前，确保停止任何具有相同ID的旧声音实例。
        stopSound(soundId);

        // 获取当前激活的音色包信息。
        SoundPackInfo activePack = SoundPackManager.getInstance().getActivePackInfo();

        // 如果没有激活的包，或者包内没有任何采样，则直接返回，不播放任何声音。
        if (activePack == null || activePack.availableNotes().isEmpty()) {
            return;
        }

        int closestNote = activePack.getClosestNoteFor(instrumentId, note);

        // 4. 计算音高调整 (Pitch)
        // 使用公式 P = 2^(d/12)，其中 d 是目标音高与采样音高的半音差。
        // 这将通过调整播放速度来精确模拟目标音高。
        float pitch = (float) Math.pow(2.0, (note - closestNote) / 12.0);

        // 将 pitch 值四舍五入到小数点后两位。
        // 这会极大地减少 pitch 值的种类。
        pitch = Math.round(pitch * 100.0f) / 100.0f;

        if (instrumentId == DRUM_KIT_INSTRUMENT_ID) {
            pitch = 1.0f;
            if (!activePack.availableNotes().getOrDefault(instrumentId, List.of()).contains(note)) {
                return;
            }
            closestNote = note;
        }

        Identifier soundIdentifier = new Identifier("extendednoteblock", "notes." + instrumentId + "." + closestNote);
        SoundEvent soundEvent = SoundEvent.of(soundIdentifier);

        StoppablePositionalSoundInstance soundInstance = new StoppablePositionalSoundInstance(
                soundEvent, SoundCategory.RECORDS, initialVolume, pitch, pos);

        PLAYING_SOUNDS.put(soundId, soundInstance);
        MinecraftClient.getInstance().getSoundManager().play(soundInstance);

    }

    /**
     * 更新一个正在播放的声音的音量。
     * 
     * @param soundId 声音的唯一ID。
     * @param volume  新的音量值 (0.0 - 1.0)。
     */
    public static void updateVolume(UUID soundId, float volume) {
        StoppablePositionalSoundInstance soundInstance = PLAYING_SOUNDS.get(soundId);
        if (soundInstance != null) {
            soundInstance.setVolume(volume);
        }
    }

    /**
     * 停止一个指定ID的声音。
     * 
     * @param soundId 声音的唯一ID。
     */
    public static void stopSound(UUID soundId) {
        StoppablePositionalSoundInstance existingSound = PLAYING_SOUNDS.remove(soundId);
        if (existingSound != null) {
            existingSound.stopSound();
        }
    }

    /**
     * 停止在指定方块位置播放的所有声音。
     * (保留此方法以兼容旧逻辑或用于方块被破坏等情况)。
     * 
     * @param pos 方块位置。
     */
    public static void stopSound(BlockPos pos) {
        PLAYING_SOUNDS.values().removeIf(sound -> {
            if (sound.getOriginPos().equals(pos)) {
                sound.stopSound();
                return true;
            }
            return false;
        });
    }
    
    // ============== Advanced Features v1.4.0 ==============
    /**
     * 更新一个正在播放的声音的高级参数：音量、音高和位置。
     * 
     * @param soundId 声音的唯一ID。
     * @param vol 新的音量值 (0.0 - 2.0)。
     * @param pitchMul 音高倍率，用于弯音效果。
     * @param x 新的声音位置X坐标。
     * @param y 新的声音位置Y坐标。
     * @param z 新的声音位置Z坐标。
     */
    public static void updateAdvanced(UUID soundId, float vol, float pitchMul, double x, double y, double z) {
        StoppablePositionalSoundInstance sound = PLAYING_SOUNDS.get(soundId);
        if (sound != null) {
            sound.setVolume(vol);
            // 基础音高已经在播放时确定，现在应用弯音倍率
            sound.setPitch(sound.getBasePitch() * pitchMul);
            sound.setPosition((float)x, (float)y, (float)z);
        }
    }
}