package com.atemukesu.extendednoteblock.mixin.client;
// com.chunfeng.noteadd.mixin;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
// import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;

@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {

    // @Redirect(
    // method = "getAdjustedPitch",
    // at = @At(
    // value = "INVOKE",
    // target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F"
    // )
    // )
    // private float removePitchClamp(float value, float min, float max) {
    // return value;
    // }

    @ModifyVariable(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At(value = "STORE"), ordinal = 1 // 表示修改方法中的第二个float类型的局部变量
    )
    private float modifyAttenuationDistance(float g, SoundInstance soundInstance) {
        // 只修改线性衰减的声音衰减距离
        if (soundInstance.getAttenuationType() == SoundInstance.AttenuationType.LINEAR) {
            return 48.0F;
        }
        return g;
    }

    /**
     * Injects into the getAdjustedPitch method to bypass the pitch clamp.
     *
     * @param sound The sound instance being processed.
     * @param cir   The CallbackInfoReturnable, used to set the return value and
     *              cancel the original method.
     */
    @Inject(
            // 目标方法：getAdjustedPitch(SoundInstance)
            // 方法签名描述符：(Lnet/minecraft/client/sound/SoundInstance;)F
            // F 代表返回类型是 float
            method = "getAdjustedPitch(Lnet/minecraft/client/sound/SoundInstance;)F",
            // 注入点在方法的开头
            at = @At("HEAD"),
            // 允许我们取消原方法的执行并提供自己的返回值
            cancellable = true)
    private void extendedNoteBlock_getAdjustedPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        // 检查声音的命名空间是否是我们的 Mod ID
        if (ExtendedNoteBlock.MOD_ID.equals(sound.getId().getNamespace())) {
            // 如果是我们的声音，直接返回未经修改的原始音高。
            // sound.getPitch() 返回的是我们计算出的、可能超出 [0.5, 2.0] 范围的值。
            cir.setReturnValue(sound.getPitch());

            // 我们已经设置了返回值，所以不需要再执行原方法中的 MathHelper.clamp(...) 了。
            // cancellable = true 会让 Mixin 在这里结束方法的执行。
        }

        // 如果不是我们的声音，这个注入方法什么也不做，
        // 原版的 getAdjustedPitch 方法会继续执行，并正常地应用音高限制。
    }

}