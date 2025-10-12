package com.atemukesu.extendednoteblock.sound;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 代表一个声音包的信息。这是一个纯粹的数据容器。
 *
 * @param id               声音包的唯一ID (通常是目录名)。
 * @param displayName      在GUI中显示的名称。
 * @param directory        声音包的路径 (文件夹或.zip文件)。
 * @param status           声音包的当前状态。
 * @param isZip            该包是否为.zip压缩文件。
 * @param availableNotes   一个映射，键是乐器ID，值是该乐器可用的采样音符列表。
 * @param noteLookupTables 一个按乐器ID分类的预计算查找表。Key是乐器ID,
 *                         Value是该乐器的查找表(Map<原始音符, 最近音符>)。
 */
public record SoundPackInfo(
        String id,
        String displayName,
        Path directory,
        Status status,
        boolean isZip,
        Map<Integer, List<Integer>> availableNotes,
        Map<Integer, Map<Integer, Integer>> noteLookupTables) {

    public enum Status {
        /** 正常，包含有效的采样。 */
        OK,
        /** 无效，例如 pack.json 缺失或无法读取。 */
        INVALID,
        /** 包是空的，没有找到任何 .ogg 采样文件。 */
        EMPTY
    }

    // 提供一个健壮的获取方法
    public int getClosestNoteFor(int instrumentId, int originalNote) {
        // 首先尝试获取特定乐器的查找表
        Map<Integer, Integer> specificLookupTable = noteLookupTables.get(instrumentId);
        if (specificLookupTable != null && specificLookupTable.containsKey(originalNote)) {
            return specificLookupTable.get(originalNote);
        }

        // 如果特定乐器没有查找表（比如是空的），则回退到乐器0的查找表
        Map<Integer, Integer> fallbackLookupTable = noteLookupTables.get(0);
        if (fallbackLookupTable != null && fallbackLookupTable.containsKey(originalNote)) {
            return fallbackLookupTable.get(originalNote);
        }

        // 如果连乐器0的查找表都没有（比如整个音色包是空的），则返回原始音符，不变调
        return originalNote;
    }
}