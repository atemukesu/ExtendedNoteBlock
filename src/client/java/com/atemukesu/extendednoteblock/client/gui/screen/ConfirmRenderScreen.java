package com.atemukesu.extendednoteblock.client.gui.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.nio.file.Path;

public class ConfirmRenderScreen extends Screen {
    private final Screen parentScreen;
    private final String fluidSynthPath;
    private final String ffmpegPath;
    private final String sf2Path;
    private final Path outputPackDir;
    private static final Text TITLE = Text.translatable("gui.extendednoteblock.confirm_render.title");
    private static final Text WARNING_LINE_1 = Text.translatable("gui.extendednoteblock.config.hint.long_operation")
            .formatted(Formatting.YELLOW);
    private static final Text WARNING_LINE_2 = Text.translatable("gui.extendednoteblock.confirm_render.line2");
    private static final Text WARNING_LINE_3 = Text.translatable("gui.extendednoteblock.confirm_render.line3");
    private static final Text BUTTON_START = Text.translatable("gui.extendednoteblock.confirm_render.button.start");
    private static final Text BUTTON_LATER = Text.translatable("gui.extendednoteblock.config.button.decide_later");

    public ConfirmRenderScreen(Screen parent, String fluidSynthPath, String ffmpegPath, String sf2Path,
            Path outputPackDir) {
        super(TITLE);
        this.parentScreen = parent;
        this.fluidSynthPath = fluidSynthPath;
        this.ffmpegPath = ffmpegPath;
        this.sf2Path = sf2Path;
        this.outputPackDir = outputPackDir;
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(BUTTON_START, button -> {
            if (this.client != null) {
                this.client.setScreen(
                        new RenderingScreen(this.parentScreen, fluidSynthPath, ffmpegPath, sf2Path, outputPackDir));
            }
        }).dimensions(this.width / 2 - 154, this.height / 2 + 20, 150, 20).build());
        this.addDrawableChild(ButtonWidget.builder(BUTTON_LATER, button -> {
            if (this.client != null) {
                this.close();
            }
        }).dimensions(this.width / 2 + 4, this.height / 2 + 20, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 50,
                0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, WARNING_LINE_1, this.width / 2, this.height / 2 - 20,
                0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, WARNING_LINE_2, this.width / 2, this.height / 2 - 10,
                0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, WARNING_LINE_3, this.width / 2, this.height / 2,
                0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) {
            if (this.parentScreen == null) {
            } else {
                System.out.println(this.parentScreen.toString());
                this.client.setScreen(this.parentScreen);
            }
        }
    }
}