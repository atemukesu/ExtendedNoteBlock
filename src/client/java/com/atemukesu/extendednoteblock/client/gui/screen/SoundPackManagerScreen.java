package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.atemukesu.extendednoteblock.sound.SoundPackInfo;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.StringUtils;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.util.Util;
import java.nio.file.Path;

public class SoundPackManagerScreen extends Screen {
    private final Screen parent;
    private SoundPackListWidget listWidget;
    private boolean hasShownConfirmScreen = false;

    public SoundPackManagerScreen(Screen parent) {
        super(Text.translatable("gui.extendednoteblock.pack_manager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        SoundPackManager.getInstance().scanPacks();
        if (!this.hasShownConfirmScreen) {
            Optional<SoundPackInfo> defaultPackOpt = SoundPackManager.getInstance().getAvailablePacks().stream()
                    .filter(p -> p.id().equals(SoundPackManager.DEFAULT_PACK_ID))
                    .findFirst();
            boolean anyPackIsRendered = SoundPackManager.getInstance().getAvailablePacks().stream()
                    .anyMatch(p -> p.status() != SoundPackInfo.Status.NOT_RENDERED);
            if (!anyPackIsRendered && defaultPackOpt.isPresent()) {
                SoundPackInfo defaultPack = defaultPackOpt.get();
                String fluidSynthPath = ConfigManager.getConfig().fluidSynthPath;
                String ffmpegPath = ConfigManager.getConfig().ffmpegPath;
                if (StringUtils.isNotBlank(fluidSynthPath) && StringUtils.isNotBlank(ffmpegPath)) {
                    this.hasShownConfirmScreen = true;
                    this.client.setScreen(new ConfirmRenderScreen(
                            this,
                            fluidSynthPath,
                            ffmpegPath,
                            defaultPack.sourceSf2Path().toString(),
                            defaultPack.directory()));
                    return;
                } else {
                    System.err.println("Cannot auto-render default pack: FluidSynth or FFMPEG path not configured.");
                }
            }
        }
        int listBottom = this.height - 80;
        this.listWidget = new SoundPackListWidget(this.width, this.height, 32, listBottom, 36);
        this.addDrawableChild(this.listWidget);
        int topButtonRowY = this.height - 76;
        this.addDrawableChild(ButtonWidget
                .builder(Text.translatable("gui.extendednoteblock.pack_manager.button.create_new"), button -> {
                    if (this.client != null)
                        this.client.setScreen(new CreatePackScreen(this));
                }).dimensions(this.width / 2 - 154, topButtonRowY, 150, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.extendednoteblock.config.title"), button -> {
            if (this.client != null)
                this.client.setScreen(new ConfigScreen(this));
        }).dimensions(this.width / 2 + 4, topButtonRowY, 150, 20).build());
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.extendednoteblock.pack_manager.button.open_folder"),
                button -> {
                    Path packDir = SoundPackManager.getInstance().getPacksDirectory();
                    Util.getOperatingSystem().open(packDir.toFile());
                }).dimensions(this.width / 2 - 100, this.height - 52, 200, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> {
            if (this.client != null)
                this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build());
        String activeId = SoundPackManager.getInstance().getActivePackId();
        if (activeId != null) {
            this.listWidget.children().stream()
                    .filter(entry -> entry.pack.id().equals(activeId))
                    .findFirst()
                    .ifPresent(entry -> this.listWidget.setSelected(entry, true));
        }
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
        super.render(context, mouseX, mouseY, delta);
    }

    private class SoundPackListWidget extends ElementListWidget<SoundPackListWidget.PackEntry> {
        public SoundPackListWidget(int width, int height, int top, int bottom, int itemHeight) {
            super(SoundPackManagerScreen.this.client, width, height, top, bottom, itemHeight);
            this.updateEntries();
        }

        public void updateEntries() {
            this.clearEntries();
            SoundPackManager.getInstance().getAvailablePacks()
                    .stream()
                    .sorted((p1, p2) -> {
                        if (p1.status() == SoundPackInfo.Status.NOT_RENDERED
                                && p2.status() != SoundPackInfo.Status.NOT_RENDERED)
                            return -1;
                        if (p1.status() != SoundPackInfo.Status.NOT_RENDERED
                                && p2.status() == SoundPackInfo.Status.NOT_RENDERED)
                            return 1;
                        return p1.displayName().compareToIgnoreCase(p2.displayName());
                    })
                    .forEach(pack -> this.addEntry(new PackEntry(pack)));
        }

        @Override
        public void setSelected(PackEntry entry) {
            this.setSelected(entry, false);
        }

        public void setSelected(PackEntry entry, boolean silent) {
            super.setSelected(entry);
            if (entry != null && !silent && entry.pack.status() == SoundPackInfo.Status.OK) {
                SoundPackManager.getInstance().setActivePack(entry.pack.id());
            }
        }

        @Override
        protected int getScrollbarPositionX() {
            return this.width / 2 + 158;
        }

        @Override
        public int getRowWidth() {
            return 310;
        }

        public class PackEntry extends ElementListWidget.Entry<PackEntry> {
            final SoundPackInfo pack;
            private final ButtonWidget renderButton;
            private final MinecraftClient client;

            public PackEntry(SoundPackInfo pack) {
                this.pack = pack;
                this.client = MinecraftClient.getInstance();
                this.renderButton = ButtonWidget.builder(
                        Text.translatable("gui.extendednoteblock.pack_manager.button.render"),
                        button -> {
                            if (this.client != null) {
                                String fluidSynthPath = ConfigManager.getConfig().fluidSynthPath;
                                String ffmpegPath = ConfigManager.getConfig().ffmpegPath;
                                this.client.setScreen(new ConfirmRenderScreen(
                                        SoundPackManagerScreen.this,
                                        fluidSynthPath,
                                        ffmpegPath,
                                        pack.sourceSf2Path().toString(),
                                        pack.directory()));
                            }
                        }).build();
                String fluidSynthPath = ConfigManager.getConfig().fluidSynthPath;
                String ffmpegPath = ConfigManager.getConfig().ffmpegPath;
                this.renderButton.active = pack.status() != SoundPackInfo.Status.OK &&
                        StringUtils.isNotBlank(fluidSynthPath) &&
                        StringUtils.isNotBlank(ffmpegPath);
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                    int mouseX, int mouseY, boolean hovered, float tickDelta) {
                Text displayName = Text.literal(pack.displayName());
                final Text statusText;
                switch (pack.status()) {
                    case OK -> statusText = Text.translatable("gui.extendednoteblock.pack_manager.status.ok")
                            .formatted(Formatting.GREEN);
                    case INCOMPLETE ->
                        statusText = Text.translatable("gui.extendednoteblock.pack_manager.status.incomplete")
                                .formatted(Formatting.YELLOW);
                    default -> statusText = Text.translatable("gui.extendednoteblock.pack_manager.status.not_rendered")
                            .formatted(Formatting.RED);
                }
                this.renderButton.setX(x + entryWidth - 80 - 5);
                this.renderButton.setY(y + (entryHeight - 20) / 2);
                this.renderButton.setWidth(80);
                this.renderButton.render(context, mouseX, mouseY, tickDelta);
                if (Objects.equals(pack.id(), SoundPackManager.getInstance().getActivePackId())) {
                    context.drawTextWithShadow(client.textRenderer, "▶ ", x, y + entryHeight / 2 - 4, 0x55FF55);
                }
                context.drawTextWithShadow(client.textRenderer, displayName, x + 10, y + 4, 0xFFFFFF);
                context.drawTextWithShadow(client.textRenderer, statusText, x + 10, y + 18, 0xA0A0A0);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (this.renderButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                if (button == 0) {
                    SoundPackListWidget.this.setSelected(this);
                    return true;
                }
                return false;
            }

            @Override
            public List<? extends Element> children() {
                return Collections.singletonList(this.renderButton);
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return Collections.singletonList(this.renderButton);
            }
        }
    }
}