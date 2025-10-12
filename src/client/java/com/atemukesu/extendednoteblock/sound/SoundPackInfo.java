package com.atemukesu.extendednoteblock.sound;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 代表一个声音包的信息。
 *
 * @param id             声音包的唯一ID (通常是目录名)。
 * @param displayName    在GUI中显示的名称。
 * @param directory      声音包的路径 (文件夹或.zip文件)。
 * @param status         声音包的当前状态。
 * @param isZip          该包是否为.zip压缩文件。
 * @param availableNotes 一个映射，键是乐器ID，值是该乐器可用的采样音符列表。
 */
public record SoundPackInfo(
        String id,
        String displayName,
        Path directory,
        Status status,
        boolean isZip,
        Map<Integer, List<Integer>> availableNotes) {
    public enum Status {
        /** 正常，包含有效的采样。 */
        OK,
        /** 无效，例如 pack.json 缺失或无法读取。 */
        INVALID,
        /** 包是空的，没有找到任何 .ogg 采样文件。 */
        EMPTY
    }
}