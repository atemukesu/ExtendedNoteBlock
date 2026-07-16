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
    /**
     * 存储当前正在播放的声音实例，以便可以更新音量或停止它们。
     */
    private static final Map<UUID, StoppablePositionalSoundInstance> PLAYING_SOUNDS = new ConcurrentHashMap<>();
    /**
     * General MIDI 标准中，鼓组的乐器ID。这个乐器有特殊的处理逻辑。
     */
    private static final int DRUM_KIT_INSTRUMENT_ID = 128;
    
    // 定义一个极小的音量阈值，防止被声音引擎剔除
    // Minecraft 可能会剔除音量 <= 0 的声音
    private static final float MIN_ALIVE_VOLUME = 0.0001f;

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

        Identifier soundIdentifier = Identifier.of("extendednoteblock", "notes." + instrumentId + "." + closestNote);
        SoundEvent soundEvent = SoundEvent.of(soundIdentifier);

        // 如果你也想修复普通模式的潜在问题，也可以在这里应用 MIN_ALIVE_VOLUME
        float safeVolume = Math.max(initialVolume, MIN_ALIVE_VOLUME);

        StoppablePositionalSoundInstance soundInstance = new StoppablePositionalSoundInstance(
                soundEvent, SoundCategory.RECORDS, safeVolume, pitch, pos);

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
            // [关键修复]：即使是更新过程中，如果音量降为纯0，某些声音系统实现可能会剔除它
            float safeVolume = Math.max(volume, MIN_ALIVE_VOLUME);
            soundInstance.setVolume(safeVolume);
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

    // [修复] 专门用于处理高级声音的启动
    public static void playAdvancedSound(BlockPos pos, UUID soundId, int instrumentId, int note,
                                         float initialVol, float initialPitchMul,
                                         double startX, double startY, double startZ) {
        
        stopSound(soundId);

        SoundPackInfo activePack = SoundPackManager.getInstance().getActivePackInfo();
        if (activePack == null || activePack.availableNotes().isEmpty()) return;

        int closestNote = activePack.getClosestNoteFor(instrumentId, note);
        
        float basePitch = (float) Math.pow(2.0, (note - closestNote) / 12.0);
        
        if (instrumentId == DRUM_KIT_INSTRUMENT_ID) {
            basePitch = 1.0f;
            if (!activePack.availableNotes().getOrDefault(instrumentId, java.util.List.of()).contains(note)) return;
            closestNote = note;
        }

        float finalStartPitch = basePitch * initialPitchMul;

        Identifier soundIdentifier = Identifier.of("extendednoteblock", "notes." + instrumentId + "." + closestNote);
        SoundEvent soundEvent = SoundEvent.of(soundIdentifier);

        // [关键修复]：如果初始音量为0，Minecraft会直接丢弃声音实例。
        // 我们强制设置一个极小的音量 (0.0001f)，让人耳听不见但引擎认为是有效的。
        float safeVolume = Math.max(initialVol, MIN_ALIVE_VOLUME);

        StoppablePositionalSoundInstance soundInstance = new StoppablePositionalSoundInstance(
                soundEvent, SoundCategory.RECORDS, safeVolume, finalStartPitch, pos);
        
        soundInstance.setPosition(startX, startY, startZ);
        soundInstance.setBasePitch(basePitch); 

        PLAYING_SOUNDS.put(soundId, soundInstance);
        MinecraftClient.getInstance().getSoundManager().play(soundInstance);
    }
    
    // [修复] 更新方法也需要保护
    public static void updateAdvanced(UUID soundId, float vol, float pitchMul, double x, double y, double z) {
        StoppablePositionalSoundInstance sound = PLAYING_SOUNDS.get(soundId);
        if (sound != null) {
            // [关键修复]：即使是更新过程中，如果音量降为纯0，某些声音系统实现可能会剔除它
            // 为了安全起见，只要我们还没发送 stopSound，就保持它活着
            float safeVolume = Math.max(vol, MIN_ALIVE_VOLUME);
            
            sound.setVolume(safeVolume);
            sound.setPitch(sound.getBasePitch() * pitchMul); 
            sound.setPosition(x, y, z);
        }
    }
}