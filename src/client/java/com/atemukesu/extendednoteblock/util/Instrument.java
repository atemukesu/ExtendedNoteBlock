package com.atemukesu.extendednoteblock.util;

import java.util.List;

public record Instrument(String name, int bank, int program, List<Integer> notes, boolean isDrumKit) {
}