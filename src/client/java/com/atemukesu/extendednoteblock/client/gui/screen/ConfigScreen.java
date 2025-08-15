package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.atemukesu.extendednoteblock.config.ModConfig;
import com.atemukesu.extendednoteblock.util.PathDetector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget ffmpegField;
    private TextFieldWidget fluidSynthField;
    private TextFieldWidget threadsField;
    private ButtonWidget continueButton;
    private ButtonWidget howToUseButton;

    public ConfigScreen(Screen parent) {
        super(Text.translatable("gui.extendednoteblock.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        ModConfig config = ConfigManager.getConfig();
        int fieldWidth = 300;
        int fieldCenterX = this.width / 2;
        this.ffmpegField = new TextFieldWidget(this.textRenderer, fieldCenterX - fieldWidth / 2, 60, fieldWidth, 20,
                Text.translatable("gui.extendednoteblock.config.ffmpeg_path.placeholder"));
        this.ffmpegField.setText(config.ffmpegPath);
        this.addDrawableChild(this.ffmpegField);
        this.fluidSynthField = new TextFieldWidget(this.textRenderer, fieldCenterX - fieldWidth / 2, 110, fieldWidth,
                20, Text.translatable("gui.extendednoteblock.config.fluidsynth_path.placeholder"));
        this.fluidSynthField.setText(config.fluidSynthPath);
        this.addDrawableChild(this.fluidSynthField);
        this.threadsField = new TextFieldWidget(this.textRenderer, fieldCenterX - fieldWidth / 2, 160, fieldWidth, 20,
                Text.translatable("gui.extendednoteblock.config.threads.placeholder"));
        this.threadsField.setText(config.threads == null ? "" : config.threads.toString());
        this.addDrawableChild(this.threadsField);
        this.howToUseButton = ButtonWidget.builder(Text.literal("?"), button -> {
            Util.getOperatingSystem().open("https://atemukesu.github.io/ExtendedNoteBlock/docs/howtouse.html");
        }).dimensions(this.ffmpegField.getX() + this.ffmpegField.getWidth() + 4, 60, 20, 20).build();
        this.addDrawableChild(this.howToUseButton);
        this.setInitialFocus(this.ffmpegField);
        PathDetector.detectExecutablesAsync(foundPaths -> {
            if (this.client != null) {
                this.client.execute(() -> {
                    if (this.ffmpegField.getText().isEmpty() && foundPaths.containsKey("ffmpeg")) {
                        this.ffmpegField.setText("ffmpeg");
                    }
                    if (this.fluidSynthField.getText().isEmpty() && foundPaths.containsKey("fluidsynth")) {
                        this.fluidSynthField.setText("fluidsynth");
                    }
                });
            }
        });
        this.continueButton = ButtonWidget
                .builder(Text.translatable("gui.extendednoteblock.config.button.continue_to_packs"),
                        button -> saveAndContinue())
                .dimensions(this.width / 2 - 100, this.height - 40, 200, 20).build();
        this.addDrawableChild(this.continueButton);
    }

    private void saveAndContinue() {
        saveConfig();
        if (this.client != null) {
            this.client.setScreen(new SoundPackManagerScreen(this.parent));
        }
    }

    @Override
    public void close() {
        saveConfig();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    private void saveConfig() {
        ModConfig config = ConfigManager.getConfig();
        config.ffmpegPath = this.ffmpegField.getText().trim();
        config.fluidSynthPath = this.fluidSynthField.getText().trim();
        String threadsText = this.threadsField.getText().trim();
        if (threadsText.isEmpty()) {
            config.threads = null;
        } else {
            try {
                int num = Integer.parseInt(threadsText);
                config.threads = num > 0 ? num : null;
            } catch (NumberFormatException e) {
                config.threads = null;
            }
        }
        ConfigManager.saveConfig();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("gui.extendednoteblock.config.ffmpeg_path.label"), this.ffmpegField.getX(), 48,
                0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("gui.extendednoteblock.config.fluidsynth_path.label"), this.fluidSynthField.getX(),
                98, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.extendednoteblock.config.threads.label"),
                this.threadsField.getX(), 148, 0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
    }
}