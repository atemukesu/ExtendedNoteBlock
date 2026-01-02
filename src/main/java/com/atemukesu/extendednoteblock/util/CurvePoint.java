package com.atemukesu.extendednoteblock.util;

public class CurvePoint {
    public float time;  // 0.0 ~ 1.0 (百分比)
    public float value; // 具体数值

    public CurvePoint(float time, float value) {
        this.time = time;
        this.value = value;
    }
}