package com.atemukesu.extendednoteblock.mixin.client;

import com.atemukesu.extendednoteblock.config.TickFixConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CommandBlockScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandBlockScreen.class)
public abstract class CommandBlockScreenMixin extends Screen {

    protected CommandBlockScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addTickFixToggle(CallbackInfo ci) {
        boolean enabled = TickFixConfig.isEnabled();

        int btnWidth = 100;
        int btnHeight = 20;
        int btnX = this.width - btnWidth - 8;
        int btnY = this.height - btnHeight - 8;

        Text restartText = Text.translatable("gui.extendednoteblock.tick_fix.restart_required");
        int labelWidth = textRenderer.getWidth(restartText);
        TextWidget label = new TextWidget(labelWidth, btnHeight, restartText, textRenderer);
        label.setPosition(btnX - 4 - labelWidth, btnY);
        addDrawableChild(label);

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.extendednoteblock.tick_fix." + (enabled ? "enabled" : "disabled")),
                button -> {
                    boolean newState = !TickFixConfig.isEnabled();
                    TickFixConfig.setEnabled(newState);
                    button.setMessage(Text.translatable(
                            "gui.extendednoteblock.tick_fix." + (newState ? "enabled" : "disabled")));
                })
                .dimensions(btnX, btnY, btnWidth, btnHeight)
                .build());
    }
}
