package com.atemukesu.extendednoteblock.util;

import net.minecraft.util.StringIdentifiable;

/**
 * 代表一个八度内的12个音高（C, C#, D...）。
 * 这个枚举实现了 StringIdentifiable 接口，使其可以方便地用作方块状态属性。
 */
public enum NotePitch implements StringIdentifiable {
    C("c"),
    C_SHARP("cs"),
    D("d"),
    D_SHARP("ds"),
    E("e"),
    F("f"),
    F_SHARP("fs"),
    G("g"),
    G_SHARP("gs"),
    A("a"),
    A_SHARP("as"),
    B("b");

    private final String name;

    NotePitch(String name) {
        this.name = name;
    }

    /**
     * 返回此枚举值的字符串表示形式，用于方块状态文件 (.json)。
     * @return 状态名称，例如 "c", "cs"。
     */
    @Override
    public String asString() {
        return this.name;
    }

    /**
     * 静态工具方法，根据 MIDI 音符值 (0-127) 计算并返回对应的音高枚举。
     * 它通过取12的模将任何 MIDI 音符映射到一个八度内的12个半音。
     * @param note MIDI 音符值，范围 0-127。
     * @return 对应的 NotePitch 枚举实例。
     */
    public static NotePitch fromMidiNote(int note) {
        int noteInOctave = note % 12;
        return switch (noteInOctave) {
            case 0 -> C;
            case 1 -> C_SHARP;
            case 2 -> D;
            case 3 -> D_SHARP;
            case 4 -> E;
            case 5 -> F;
            case 6 -> F_SHARP;
            case 7 -> G;
            case 8 -> G_SHARP;
            case 9 -> A;
            case 10 -> A_SHARP;
            case 11 -> B;
            default -> C; // 这是一个备用情况，理论上不会发生，因为 note % 12 的结果总是在 0-11 之间。
        };
    }
}