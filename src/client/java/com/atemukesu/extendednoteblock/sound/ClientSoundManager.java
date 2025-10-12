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

        // 1. 获取指定乐器的采样列表
        List<Integer> availableNotes = activePack.availableNotes().get(instrumentId);

        // 2. 优雅回退 (Fallback) 逻辑
        // 如果当前乐器(instrumentId)没有任何采样...
        if (availableNotes == null || availableNotes.isEmpty()) {
            // ...则尝试使用乐器ID 0 (通常是钢琴) 的采样列表作为替代。
            availableNotes = activePack.availableNotes().get(0);
            // 如果连乐器 0 都没有采样，则无法播放，直接返回。
            if (availableNotes == null || availableNotes.isEmpty()) {
                return;
            }
        }

        // 3. 查找最接近的采样音高
        // 从可用的采样列表中，找到与目标音高(note)最接近的一个。
        int closestNote = findClosestNote(note, availableNotes);

        // 4. 计算音高调整 (Pitch)
        // 使用公式 P = 2^(d/12)，其中 d 是目标音高与采样音高的半音差。
        // 这将通过调整播放速度来精确模拟目标音高。
        float pitch = (float) Math.pow(2.0, (note - closestNote) / 12.0);

        // 将 pitch 值四舍五入到小数点后两位。
        // 这会极大地减少 pitch 值的种类。
        pitch = Math.round(pitch * 100.0f) / 100.0f;

        // 5. 特殊处理鼓组
        if (instrumentId == DRUM_KIT_INSTRUMENT_ID) {
            // 鼓组的每个音高代表一个独立的打击乐器（如底鼓、军鼓），它们之间不能通过调音高来模拟。
            // 因此，我们强制音高为1.0（不调整）。
            pitch = 1.0f;

            // 并且，我们只在音色包中确实存在这个鼓点采样时才播放它。
            if (!availableNotes.contains(note)) {
                // 如果找不到精确匹配的鼓点，就不播放声音，以保证音乐的准确性。
                return;
            }
            // 强制使用目标音高作为采样的key，因为我们期望精确匹配。
            closestNote = note;
        }

        // 6. 创建并播放声音实例
        // 构建声音事件的Identifier，格式为 "extendednoteblock:notes.<乐器ID>.<采样音高ID>"
        Identifier soundIdentifier = new Identifier("extendednoteblock", "notes." + instrumentId + "." + closestNote);
        SoundEvent soundEvent = SoundEvent.of(soundIdentifier);

        StoppablePositionalSoundInstance soundInstance = new StoppablePositionalSoundInstance(
                soundEvent, SoundCategory.RECORDS, initialVolume, pitch, pos);

        PLAYING_SOUNDS.put(soundId, soundInstance);
        MinecraftClient.getInstance().getSoundManager().play(soundInstance);
    }

    /**
     * 从一个已排序的列表中找到最接近目标值的数字。
     * 
     * @param targetNote     目标音高。
     * @param availableNotes 一个已排序的可用采样音高列表。
     * @return 列表中最接近 targetNote 的值。
     */
    private static int findClosestNote(int targetNote, List<Integer> availableNotes) {
        if (availableNotes.isEmpty()) {
            return targetNote; // 理论上不会执行到这里
        }

        // 使用二分查找算法来高效地定位最接近的值。
        int low = 0;
        int high = availableNotes.size() - 1;

        // 处理边界情况
        if (targetNote <= availableNotes.get(low))
            return availableNotes.get(low);
        if (targetNote >= availableNotes.get(high))
            return availableNotes.get(high);

        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (targetNote < availableNotes.get(mid)) {
                high = mid - 1;
            } else if (targetNote > availableNotes.get(mid)) {
                low = mid + 1;
            } else {
                return availableNotes.get(mid); // 找到了完全匹配的值
            }
        }

        // 循环结束后，目标值位于 high 和 low 索引之间。
        // 比较这两个索引处的值与目标值的距离，返回更近的那个。
        return (availableNotes.get(low) - targetNote) < (targetNote - availableNotes.get(high))
                ? availableNotes.get(low)
                : availableNotes.get(high);
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
            if (sound.getPos().equals(pos)) {
                sound.stopSound();
                return true;
            }
            return false;
        });
    }
}