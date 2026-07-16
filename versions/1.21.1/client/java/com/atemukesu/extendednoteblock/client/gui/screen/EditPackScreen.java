package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import com.atemukesu.extendednoteblock.client.gui.screen.ExtendedNoteBlockScreen.PianoWidget;
import com.atemukesu.extendednoteblock.sound.SoundPackInfo;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 音色包编辑界面。
 * - 独占式播放逻辑：一次只播放一个预览，提供清晰的播放/停止状态反馈。
 * - 移除预览按钮的点击音，防止干扰乐器音头。
 * - 界面关闭时自动停止播放。
 * - 保持了分组显示、安全删除、空状态引导等优化。
 */
public class EditPackScreen extends Screen {
    private final Screen parent;
    private SoundPackInfo packInfo;
    private SampleListWidget listWidget;

    /**
     * 当前正在播放的预览声音实例。
     */
    @Nullable
    private AbstractSoundInstance currentlyPlayingSound;
    /**
     * 触发了当前播放的列表条目，用于状态更新。
     */
    @Nullable
    private SampleListWidget.SampleEntry currentlyPlayingEntry;

    public EditPackScreen(Screen parent, SoundPackInfo packInfo) {
        super(Text.translatable("gui.extendednoteblock.edit_pack.title", packInfo.displayName()));
        this.parent = parent;
        this.packInfo = packInfo;
    }

    @Override
    protected void init() {
        super.init();
        // 初始化列表控件，占据屏幕中央大部分区域
        this.listWidget = new SampleListWidget(this.width, this.height, 32, this.height - 80);
        this.addDrawableChild(this.listWidget);

        // 底部按钮区域的Y坐标
        int buttonY = this.height - 52;
        int bottomRowY = this.height - 28;

        // "打开文件夹" 按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.extendednoteblock.edit_pack.button.open_folder"),
                button -> Util.getOperatingSystem().open(getSamplesDirectory().toFile()))
                .dimensions(this.width / 2 - 154, buttonY, 150, 20).build());

        // "重新加载" 按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.extendednoteblock.edit_pack.button.reload"),
                this::reloadPackInfo).dimensions(this.width / 2 + 4, buttonY, 150, 20).build());

        // "完成" 按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), (button) -> {
            if (this.client != null)
                this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 100, bottomRowY, 200, 20).build());
    }

    /**
     * 播放一个指定的采样预览。
     * 如果已有其他预览正在播放，会先停止它。
     * 
     * @param entry      触发播放的列表条目。
     * @param soundEvent 要播放的声音事件。
     */
    private void playPreview(SampleListWidget.SampleEntry entry, SoundEvent soundEvent) {
        stopCurrentPreview(); // 确保独占播放

        this.currentlyPlayingSound = new PreviewSoundInstance(soundEvent, SoundCategory.MASTER, 1.0f, 1.0f);
        if (this.client != null) {
            this.client.getSoundManager().play(this.currentlyPlayingSound);
            this.currentlyPlayingEntry = entry;
            entry.setPlaying(true); // 更新按钮状态
        }
    }

    /**
     * 停止当前正在播放的预览声音。
     */
    private void stopCurrentPreview() {
        if (this.currentlyPlayingSound != null && this.client != null) {
            // 使用客户端的声音管理器来停止声音，这是最可靠的方式
            this.client.getSoundManager().stop(this.currentlyPlayingSound);
            this.currentlyPlayingSound = null;
        }
        if (this.currentlyPlayingEntry != null) {
            this.currentlyPlayingEntry.setPlaying(false); // 重置按钮状态
            this.currentlyPlayingEntry = null;
        }
    }

    /**
     * 获取当前音色包的采样文件夹路径。
     * 路径格式为: .../pack_name/assets/mod_id/sounds/notes/
     * 
     * @return 采样文件夹的 Path 对象。
     */
    private Path getSamplesDirectory() {
        return packInfo.directory().resolve("assets").resolve(ExtendedNoteBlock.MOD_ID).resolve("sounds")
                .resolve("notes");
    }

    /**
     * 重新加载音色包信息并刷新界面。
     * 
     * @param button 触发此操作的按钮（可以为null）。
     */
    private void reloadPackInfo(ButtonWidget button) {
        stopCurrentPreview(); // 重载前停止声音
        SoundPackManager.getInstance().loadOrUpdatePack(this.packInfo.directory(), false);
        this.packInfo = SoundPackManager.getInstance().getPackInfoById(this.packInfo.id());
        this.listWidget.updateEntries();

        if (this.client != null) {
            // 弹出Toast提示用户资源已重载
            this.client.getToastManager().add(new net.minecraft.client.toast.SystemToast(
                    net.minecraft.client.toast.SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.translatable("gui.extendednoteblock.edit_pack.reloaded.title"),
                    Text.translatable("gui.extendednoteblock.edit_pack.reloaded.description")));
            // 触发Minecraft的资源重载
            this.client.reloadResources();
        }
    }

    /**
     * 当屏幕关闭时调用。
     */
    @Override
    public void close() {
        stopCurrentPreview(); // 关闭界面时停止声音
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    /**
     * 当屏幕被移除时调用（例如切换到另一个屏幕）。
     * 确保声音被停止。
     */
    @Override
    public void removed() {
        super.removed();
        stopCurrentPreview();
    }

    /**
     * 渲染屏幕主内容。
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        Text hint1 = Text.translatable("gui.extendednoteblock.edit_pack.hint1").formatted(Formatting.YELLOW);
        context.drawCenteredTextWithShadow(textRenderer, hint1, this.width / 2, this.height - 76, 0xFFFFFF);
        Text hint2 = Text.translatable("gui.extendednoteblock.edit_pack.hint2").formatted(Formatting.GRAY);
        context.drawCenteredTextWithShadow(textRenderer, hint2, this.width / 2, this.height - 66, 0xFFFFFF);

        this.listWidget.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 13, 0xFFFFFF);
    }

    /**
     * 一个自定义的按钮控件，它在被点击时不会播放默认的UI声音。
     * 用于预览按钮，以避免干扰乐器采样的音头。
     */
    private static class NoSoundButtonWidget extends ButtonWidget {
        // 构造函数保持为 protected，通过 Builder 创建
        protected NoSoundButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress,
                NarrationSupplier narrationSupplier) {
            super(x, y, width, height, message, onPress, narrationSupplier);
        }

        // 修复：将静态工厂方法重命名为 `create` 以避免与父类冲突
        public static Builder create(Text message, PressAction onPress) {
            return new Builder(message, onPress);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            // 重写此方法并保持为空，以禁用按钮点击音。
        }

        // 自定义的 Builder 类，不再继承 vanilla 的 Builder，以避免可见性问题
        public static class Builder {
            private final Text message;
            private final PressAction onPress;
            private int x;
            private int y;
            private int width = 150;
            private int height = 20;
            @Nullable
            private Tooltip tooltip;

            public Builder(Text message, PressAction onPress) {
                this.message = message;
                this.onPress = onPress;
            }

            public Builder dimensions(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
                return this;
            }

            // 修复：移除了未使用的 tooltip 方法

            public NoSoundButtonWidget build() {
                NoSoundButtonWidget button = new NoSoundButtonWidget(this.x, this.y, this.width, this.height,
                        this.message, this.onPress, DEFAULT_NARRATION_SUPPLIER);
                button.setTooltip(this.tooltip);
                return button;
            }
        }
    }

    /**
     * 一个专门用于UI预览的可停止的声音实例。
     */
    private static class PreviewSoundInstance extends AbstractSoundInstance {
        public PreviewSoundInstance(SoundEvent sound, SoundCategory category, float volume, float pitch) {
            super(sound, category, Random.create());
            this.volume = volume;
            this.pitch = pitch;
            this.relative = true;
            this.attenuationType = AttenuationType.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }
    }

    /**
     * 内部类，用于显示采样文件的列表。
     */
    private class SampleListWidget extends ElementListWidget<SampleListWidget.Entry> {

        public SampleListWidget(int width, int height, int top, int bottom) {
            super(EditPackScreen.this.client, width, bottom - top, top, 35);
            updateEntries();
        }

        public void updateEntries() {
            this.clearEntries();
            if (packInfo.availableNotes().isEmpty()) {
                this.addEntry(new EmptyEntry());
            } else {
                packInfo.availableNotes().keySet().stream()
                        .sorted()
                        .forEach(instrumentId -> {
                            this.addEntry(new InstrumentHeaderEntry(instrumentId));
                            List<Integer> notes = packInfo.availableNotes().get(instrumentId);
                            notes.sort(Comparator.naturalOrder());
                            for (int note : notes) {
                                this.addEntry(new SampleEntry(instrumentId, note));
                            }
                        });
            }
        }

        @Override
        public int getRowWidth() {
            return 350;
        }

        @Override
        public int getScrollbarX() {
            return (this.width / 2) + (getRowWidth() / 2) + 4;
        }

        public abstract class Entry extends ElementListWidget.Entry<Entry> {
        }

        public class EmptyEntry extends Entry {
            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                    int mouseX, int mouseY, boolean hovered, float tickDelta) {
                Text emptyMessage = Text.translatable("gui.extendednoteblock.edit_pack.empty_pack_message")
                        .formatted(Formatting.GRAY);
                int listHeight = SampleListWidget.this.getBottom() - SampleListWidget.this.getY();
                int textY = SampleListWidget.this.getY() + listHeight / 2 - 4; // 垂直居中
                context.drawCenteredTextWithShadow(textRenderer, emptyMessage, SampleListWidget.this.width / 2, textY,
                        0xFFFFFF);
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return Collections.emptyList();
            }

            @Override
            public List<? extends Element> children() {
                return Collections.emptyList();
            }
        }

        public class InstrumentHeaderEntry extends Entry {
            private final Text title;

            public InstrumentHeaderEntry(int instrumentId) {
                // 根据乐器ID选择对应的翻译key
                Text instrumentNameText = (instrumentId == 128)
                        ? Text.translatable("gui.extendednoteblock.edit_pack.instrument.drum_kit")
                        : Text.translatable("gui.extendednoteblock.edit_pack.instrument.generic");

                // 使用可翻译的格式化字符串来组合最终的标题
                this.title = Text
                        .translatable("gui.extendednoteblock.edit_pack.instrument.header_format", instrumentNameText,
                                instrumentId)
                        .formatted(Formatting.YELLOW, Formatting.BOLD);
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                    int mouseX, int mouseY, boolean hovered, float tickDelta) {
                context.drawCenteredTextWithShadow(client.textRenderer, this.title,
                        client.getWindow().getScaledWidth() / 2, y + entryHeight / 2 - 4, 0xFFFFFF);
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return Collections.emptyList();
            }

            @Override
            public List<? extends Element> children() {
                return Collections.emptyList();
            }
        }

        public class SampleEntry extends Entry {
            private final int instrumentId;
            private final int note;
            private final SoundEvent soundEvent;
            private final ButtonWidget previewButton;
            private final ButtonWidget removeButton;
            private boolean isPlaying = false;

            public SampleEntry(int instrumentId, int note) {
                this.instrumentId = instrumentId;
                this.note = note;
                Identifier soundId = Identifier.of(ExtendedNoteBlock.MOD_ID,
                        "notes." + this.instrumentId + "." + this.note);
                this.soundEvent = SoundEvent.of(soundId);

                // 修复：使用重命名后的 `create` 方法
                this.previewButton = NoSoundButtonWidget.create(Text.literal("▶"), button -> {
                    if (this.isPlaying) {
                        EditPackScreen.this.stopCurrentPreview();
                    } else {
                        EditPackScreen.this.playPreview(this, this.soundEvent);
                    }
                }).dimensions(0, 0, 20, 20).build();
                updatePreviewButtonState();

                this.removeButton = ButtonWidget.builder(Text.literal("❌"), button -> {
                    Text question = Text.translatable("gui.extendednoteblock.edit_pack.delete_confirm.question",
                            this.instrumentId + "." + this.note + ".ogg");
                    Text warning = Text.translatable("gui.extendednoteblock.edit_pack.delete_confirm.warning")
                            .formatted(Formatting.RED);

                    client.setScreen(new ConfirmScreen(
                            confirmed -> {
                                if (confirmed) {
                                    this.deleteFile();
                                }
                                client.setScreen(EditPackScreen.this);
                            },
                            question, warning));
                }).dimensions(0, 0, 20, 20).build();
                this.removeButton
                        .setTooltip(Tooltip.of(Text.translatable("gui.extendednoteblock.edit_pack.tooltip.remove")));
            }

            private void deleteFile() {
                try {
                    Path fileToRemove = getSamplesDirectory().resolve(this.instrumentId + "." + this.note + ".ogg");
                    Files.deleteIfExists(fileToRemove);
                    EditPackScreen.this.reloadPackInfo(null);
                } catch (IOException e) {
                    ExtendedNoteBlock.LOGGER.error("Failed to delete sample file", e);
                }
            }

            private void updatePreviewButtonState() {
                if (isPlaying) {
                    this.previewButton.setMessage(Text.literal("⏹"));
                    this.previewButton.setTooltip(
                            Tooltip.of(Text.translatable("gui.extendednoteblock.edit_pack.tooltip.stop_preview")));
                } else {
                    this.previewButton.setMessage(Text.literal("▶"));
                    this.previewButton.setTooltip(
                            Tooltip.of(Text.translatable("gui.extendednoteblock.edit_pack.tooltip.preview")));
                }
            }

            public void setPlaying(boolean playing) {
                this.isPlaying = playing;
                updatePreviewButtonState();
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                    int mouseX, int mouseY, boolean hovered, float tickDelta) {
                String noteName = PianoWidget.getNoteName(note);
                Text infoText = Text.translatable("gui.extendednoteblock.edit_pack.sample_info", noteName, note);

                context.drawTextWithShadow(client.textRenderer, infoText, x + 5, y + (entryHeight - 8) / 2, 0xFFFFFF);

                int buttonY = y + (entryHeight - 20) / 2;
                this.removeButton.setX(x + entryWidth - this.removeButton.getWidth() - 5);
                this.removeButton.setY(buttonY);
                this.previewButton.setX(this.removeButton.getX() - this.previewButton.getWidth() - 5);
                this.previewButton.setY(buttonY);

                this.removeButton.render(context, mouseX, mouseY, tickDelta);
                this.previewButton.render(context, mouseX, mouseY, tickDelta);
            }

            @Override
            public List<? extends Element> children() {
                return List.of(this.previewButton, this.removeButton);
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return List.of(this.previewButton, this.removeButton);
            }
        }
    }
}