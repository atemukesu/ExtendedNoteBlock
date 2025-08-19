package com.atemukesu.extendednoteblock.mixin.client;
// com.chunfeng.noteadd.mixin;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
// import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {

    // @Redirect(
    //         method = "getAdjustedPitch",
    //         at = @At(
    //                 value = "INVOKE",
    //                 target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F"
    //         )
    // )
    // private float removePitchClamp(float value, float min, float max) {
    //     return value;
    // }

    @ModifyVariable(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At(value = "STORE"),
            ordinal = 1 // 表示修改方法中的第二个float类型的局部变量
    )
    private float modifyAttenuationDistance(float g, SoundInstance soundInstance) {
        // 只修改线性衰减的声音衰减距离
        if (soundInstance.getAttenuationType() == SoundInstance.AttenuationType.LINEAR) {
            return 48.0F;
        }
        return g;
    }
}