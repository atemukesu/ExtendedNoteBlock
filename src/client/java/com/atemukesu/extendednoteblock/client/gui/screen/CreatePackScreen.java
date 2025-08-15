package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.atemukesu.extendednoteblock.sound.SoundPackInfo;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import java.nio.file.Path;
import java.util.List;

public class CreatePackScreen extends Screen {
    private final Screen parent;
    private NewPackListWidget listWidget;

    public CreatePackScreen(Screen parent) {
        super(Text.translatable("gui.extendednoteblock.create_pack.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.listWidget = new NewPackListWidget(this.width, this.height, 32, this.height - 80, 60);
        this.addDrawableChild(this.listWidget);
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.extendednoteblock.create_pack.button.open_sources_folder"),
                button -> {
                    Path sourcesDir = SoundPackManager.getInstance().getSourcesDirectory();
                    Util.getOperatingSystem().open(sourcesDir.toFile());
                }).dimensions(this.width / 2 - 100, this.height - 52, 200, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), (button) -> {
            if (this.client != null)
                this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public void close() {
        if (this.client != null)
            this.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        this.listWidget.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 13, 0xFFFFFF);
        Text hint = Text.translatable("gui.extendednoteblock.create_pack.hint", SoundPackManager.SOURCES_DIR_NAME)
                .formatted(Formatting.YELLOW);
        context.drawCenteredTextWithShadow(textRenderer, hint, this.width / 2, this.height - 76, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    private class NewPackListWidget extends ElementListWidget<NewPackListWidget.Entry> {
        public abstract static class Entry extends ElementListWidget.Entry<Entry> {
        }

        public NewPackListWidget(int width, int height, int top, int bottom, int itemHeight) {
            super(CreatePackScreen.this.client, width, height, top, bottom, itemHeight);
            this.updateEntries();
        }

        public void updateEntries() {
            this.clearEntries();
            List<Path> newSoundfonts = SoundPackManager.getInstance().findNewSoundfonts();
            if (newSoundfonts.isEmpty()) {
                this.addEntry(new InfoEntry(Text.translatable("gui.extendednoteblock.create_pack.no_new_sources")
                        .formatted(Formatting.GRAY)));
            } else {
                newSoundfonts.forEach(path -> this.addEntry(new NewPackEntry(path)));
            }
        }

        @Override
        public int getRowWidth() {
            return 350;
        }

        @Override
        protected int getScrollbarPositionX() {
            return (this.width / 2) + (this.getRowWidth() / 2);
        }

        public class InfoEntry extends Entry {
            private final Text message;

            public InfoEntry(Text message) {
                this.message = message;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                    int mouseX, int mouseY, boolean hovered, float tickDelta) {
                context.drawCenteredTextWithShadow(client.textRenderer, this.message, CreatePackScreen.this.width / 2,
                        y + entryHeight / 2 - 4, 0xFFFFFF);
            }

            @Override
            public List<? extends net.minecraft.client.gui.Element> children() {
                return List.of();
            }

            @Override
            public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
                return List.of();
            }
        }

        public class NewPackEntry extends Entry {
            private final Path sf2Path;
            private final TextFieldWidget nameField;
            private final ButtonWidget createButton;

            @Override
            public java.util.List<? extends net.minecraft.client.gui.Element> children() {
                return java.util.List.of(this.nameField, this.createButton);
            }

            @Override
            public java.util.List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
                return java.util.List.of(this.nameField, this.createButton);
            }

            public NewPackEntry(Path path) {
                this.sf2Path = path;
                String defaultName = path.getFileName().toString().replaceFirst("[.][^.]+$", "");
                this.nameField = new TextFieldWidget(client.textRenderer, 0, 0, 200, 20,
                        Text.translatable("gui.extendednoteblock.create_pack.name_field"));
                this.nameField.setText(defaultName);
                this.createButton = ButtonWidget
                        .builder(Text.translatable("gui.extendednoteblock.create_pack.button.create"), button -> {
                            String displayName = this.nameField.getText().trim();
                            if (!displayName.isEmpty()) {
                                String proposedId = displayName.replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase();
                                if (SoundPackManager.getInstance().getPackInfoById(proposedId) != null) {
                                    this.nameField.setEditableColor(Formatting.RED.getColorValue());
                                    return;
                                }
                                SoundPackInfo newPack = SoundPackManager.getInstance().createNewPack(displayName,
                                        this.sf2Path);
                                if (newPack != null && client != null) {
                                    client.setScreen(new RenderingScreen(
                                            CreatePackScreen.this.parent,
                                            ConfigManager.getConfig().fluidSynthPath,
                                            ConfigManager.getConfig().ffmpegPath,
                                            newPack.sourceSf2Path().toString(),
                                            newPack.directory()));
                                }
                            }
                        }).dimensions(0, 0, 100, 20).build();
                this.nameField.setChangedListener(text -> {
                    this.nameField.setEditableColor(0xFFFFFFFF);
                });
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                    int mouseX, int mouseY, boolean hovered, float tickDelta) {
                context.drawTextWithShadow(client.textRenderer, this.sf2Path.getFileName().toString(), x + 5, y + 8,
                        0xFFFFFF);
                int secondRowY = y + 30;
                this.nameField.setX(x + 5);
                this.nameField.setY(secondRowY);
                this.nameField.render(context, mouseX, mouseY, tickDelta);
                this.createButton.setX(x + entryWidth - this.createButton.getWidth() - 5);
                this.createButton.setY(secondRowY);
                this.createButton.render(context, mouseX, mouseY, tickDelta);
            }
        }
    }
}