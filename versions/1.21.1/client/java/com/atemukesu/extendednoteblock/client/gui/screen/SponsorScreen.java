package com.atemukesu.extendednoteblock.client.gui.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;

public class SponsorScreen extends Screen {
    private final Screen parent;
    public static final String SPONSOR_URL = "https://afdian.com/a/atommix";

    public SponsorScreen(Screen parent) {
        super(Text.translatable("gui.extendednoteblock.sponsor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int y = this.height - 70; // Buttons at the bottom

        // Sponsor Button
        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("gui.extendednoteblock.sponsor.action"), button -> {
                    Util.getOperatingSystem().open(URI.create(SPONSOR_URL));
                }).dimensions(this.width / 2 - 100, y, 200, 20).build());

        y += 25;

        // Back Button
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> {
            this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 100, y, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        int y = 50;
        Text description = Text.translatable("gui.extendednoteblock.sponsor.description");
        context.drawCenteredTextWithShadow(this.textRenderer, description, this.width / 2, y, 0xEEEEEE);
        y += 25;

        Text story = Text.translatable("gui.extendednoteblock.sponsor.story");
        context.drawTextWrapped(this.textRenderer, story, 20, y, this.width - 40, 0xAAAAAA);

        int storyHeight = this.textRenderer.getWrappedLinesHeight(story, this.width - 40);
        y += storyHeight + 30;

        Text thanks = Text.translatable("gui.extendednoteblock.sponsor.thanks");
        context.drawCenteredTextWithShadow(this.textRenderer, thanks, this.width / 2, y, 0xFFADAA);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
