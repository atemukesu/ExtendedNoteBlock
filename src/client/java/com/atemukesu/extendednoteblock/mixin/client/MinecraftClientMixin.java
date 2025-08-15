package com.atemukesu.extendednoteblock.mixin.client;

import com.atemukesu.extendednoteblock.client.gui.screen.PreLaunchCheckScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 {@link MinecraftClient} 类。
 * <p>
 * <b>目的:</b> 这个 Mixin 的主要目的是在游戏启动后、进入主菜单之前，显示一个自定义的预启动检查屏幕。
 * 这对于检查模组配置（如声音包是否正确启用）非常有用，可以提前向用户发出警告。
 * </p>
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    /**
     * 一个静态标志，用于确保预启动检查屏幕在每次游戏启动时只显示一次。
     * 如果没有这个标志，每次返回主菜单时都会触发检查屏幕。
     */
    private static boolean hasShownCheckScreen = false;

    /**
     * 注入到 {@code setScreen} 方法的开头。
     * <p>
     * <b>注入点:</b> {@code HEAD} - 在目标方法的代码执行之前。
     * <b>行为:</b>
     * 1. 检查游戏是否正要切换到主菜单屏幕 ({@link TitleScreen})。
     * 2. 检查我们的预启动检查屏幕是否尚未显示过。
     * 3. 如果两个条件都满足，它会：
     * a. 将标志位 {@code hasShownCheckScreen} 设置为 true，防止重复显示。
     * b. 取消原定的屏幕切换操作 ({@code ci.cancel()})。
     * c. 转而显示我们自定义的 {@link PreLaunchCheckScreen}。
     * </p>
     * 这样就实现了在进入主菜单前“劫持”并插入自定义屏幕的效果。
     *
     * @param screen 要设置的新屏幕。
     * @param ci     回调信息对象，用于取消原始方法调用。
     */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof TitleScreen && !hasShownCheckScreen) {
            hasShownCheckScreen = true;
            // 显示我们自己的屏幕，而不是主菜单
            MinecraftClient.getInstance().setScreen(new PreLaunchCheckScreen());
            // 取消原有的 setScreen(TitleScreen) 调用
            ci.cancel();
        }
    }
}