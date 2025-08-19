package com.atemukesu.extendednoteblock.sound;

import java.nio.file.Path;

public record SoundPackInfo(
        String id,
        String displayName,
        Path directory,
        Path sourceSf2Path,
        Status status,
        boolean full
        ) {
    public enum Status {
        // 标准
        OK,
        // 不完整
        INCOMPLETE,
        // 没有渲染
        NOT_RENDERED,
        // 源文件丢失
        SOURCE_MISSING,
        ERROR
    }
}