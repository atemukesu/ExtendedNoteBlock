package com.atemukesu.extendednoteblock.mixin.client;
// com.chunfeng.noteadd.mixin;

import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEvent.class)
public abstract class SoundEventMixin {
    @Shadow
    private boolean staticDistance;

    @Inject(
            method = "getDistanceToTravel(F)F",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onGetDistanceToTravel(float volume, CallbackInfoReturnable<Float> cir) {
        if (!this.staticDistance) {
            cir.setReturnValue(48.0F); // 固定声音衰减距离为48
            cir.cancel();
        }
    }
}