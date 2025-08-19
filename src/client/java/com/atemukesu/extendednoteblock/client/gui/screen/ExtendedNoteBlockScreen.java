package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.client.gui.widget.ComboBoxWidget;
import com.atemukesu.extendednoteblock.map.InstrumentMap;
import com.atemukesu.extendednoteblock.network.ModMessages;
import com.atemukesu.extendednoteblock.screen.ExtendedNoteBlockScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 扩展音符盒的图形用户界面(GUI)屏幕。
 * <p>
 * 该屏幕允许玩家修改扩展音符盒的各种属性，包括：
 * <ul>
 * <li><b>音符(Note):</b> 通过一个可交互的钢琴键盘部件选择 MIDI 音符 (0-127)。</li>
 * <li><b>乐器(Instrument):</b> 通过一个下拉组合框选择 General MIDI 乐器。</li>
 * <li><b>力度(Velocity):</b> 通过文本框设置音符的力度。</li>
 * <li><b>延音(Sustain):</b> 通过文本框设置音符的延音时长（以游戏刻为单位）。</li>
 * <li><b>延迟播放时间(DelayedPlayingTime):</b> 通过文本框设置在接收到红石信号之后延迟多久播放音符 (以 ms 为单位)。
 * </ul>
 * 当屏幕关闭时，所有更改都会通过网络数据包发送到服务器。
 */
@Environment(EnvType.CLIENT)
public class ExtendedNoteBlockScreen extends HandledScreen<ExtendedNoteBlockScreenHandler> {
    private int note;
    private int velocity;
    private int sustain;
    private int instrumentId;
    private int delayedPlayingTime;
    private TextFieldWidget velocityField;
    private TextFieldWidget sustainField;
    private TextFieldWidget delayField;
    private ComboBoxWidget<InstrumentOption> instrumentComboBox;
    private PianoWidget pianoWidget;
    private Text hoveredKeyText = Text.empty();

    /**
     * 一个辅助内部类，用于表示组合框中的乐器选项。
     * <p>
     * 它将乐器的 ID 和其可读名称封装在一起。
     * {@link #toString()} 方法被重写以提供在 GUI 中显示的格式化字符串。
     */
    private static class InstrumentOption {
        private final int id;
        private final String name;

        /**
         * 构造一个新的乐器选项。
         * 
         * @param id   乐器的 MIDI ID。
         * @param name 乐器的可读名称。
         */
        public InstrumentOption(int id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * 获取乐器的 ID。
         * 
         * @return 乐器的 MIDI ID。
         */
        public int getId() {
            return id;
        }

        /**
         * 返回用于在组合框中显示的字符串表示形式。
         * 
         * @return 格式为 "ID - 名称" 的字符串。
         */
        @Override
        public String toString() {
            return String.format("%d - %s", id, name);
        }
    }

    /**
     * 构造扩展音符盒屏幕。
     *
     * @param handler   与此屏幕关联的屏幕处理器，用于同步数据。
     * @param inventory 玩家物品栏。
     * @param title     屏幕的标题。
     */
    public ExtendedNoteBlockScreen(ExtendedNoteBlockScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.note = handler.getNote();
        this.velocity = handler.getVelocity();
        this.sustain = handler.getSustain();
        this.instrumentId = handler.getInstrumentId();
        this.delayedPlayingTime = handler.getDelayedPlayingTime();
    }

    /**
     * 初始化屏幕和所有 GUI 部件。
     * <p>
     * 此方法在屏幕显示时调用，负责创建和布局所有交互元素，
     * 如乐器组合框、力度/延音文本框和钢琴键盘部件。
     */
    @Override
    protected void init() {
        super.init();
        final int PADDING = 10;
        final int WIDE_ROW_SPACING = 40;
        final int PIANO_HEIGHT = 70;
        final int PIANO_BOTTOM_MARGIN = 40;
        int pianoWidgetWidth = (int) (this.width * 0.9);
        int pianoWidgetX = (this.width - pianoWidgetWidth) / 2;
        int pianoWidgetY = this.height - PIANO_BOTTOM_MARGIN - PIANO_HEIGHT;
        // 创建并添加钢琴部件
        this.pianoWidget = new PianoWidget(pianoWidgetX, pianoWidgetY, pianoWidgetWidth, PIANO_HEIGHT,
                newNote -> this.note = newNote,
                hoveredText -> this.hoveredKeyText = hoveredText);
        this.addDrawableChild(this.pianoWidget);
        int topControlsX = pianoWidgetX;
        int topControlsWidth = pianoWidgetWidth;
        int currentY = PADDING + 20;
        // 创建并添加乐器选择组合框
        List<InstrumentOption> instrumentOptions = createInstrumentOptions();
        int initialIndex = findInstrumentIndex(instrumentOptions, this.instrumentId);
        this.instrumentComboBox = new ComboBoxWidget<>(topControlsX, currentY, topControlsWidth, 20,
                instrumentOptions, initialIndex, selectedIndex -> {
                    if (selectedIndex >= 0 && selectedIndex < instrumentOptions.size()) {
                        this.instrumentId = instrumentOptions.get(selectedIndex).getId();
                    }
                });
        this.addDrawableChild(this.instrumentComboBox);
        currentY += WIDE_ROW_SPACING;
        int thirdWidth = (topControlsWidth - PADDING) / 3;
        int velocityX = topControlsX;
        int sustainX = velocityX + thirdWidth + PADDING;
        int delayX = sustainX + thirdWidth + PADDING;
        // 创建并添加力度输入框
        this.velocityField = new TextFieldWidget(this.textRenderer, velocityX, currentY, thirdWidth, 20,
                Text.translatable("gui.extendednoteblock.velocity"));
        this.velocityField.setMaxLength(3);
        this.velocityField.setText(String.valueOf(this.velocity));
        this.velocityField.setChangedListener(text -> this.velocity = parseInteger(text, 0, 127, this.velocity));
        this.addDrawableChild(this.velocityField);
        // 创建并添加延音输入框
        this.sustainField = new TextFieldWidget(this.textRenderer, sustainX, currentY, thirdWidth, 20,
                Text.translatable("gui.extendednoteblock.sustain_ticks"));
        this.sustainField.setMaxLength(3);
        this.sustainField.setText(String.valueOf(this.sustain));
        this.sustainField.setChangedListener(text -> this.sustain = parseInteger(text, 0, 400, this.sustain));
        this.addDrawableChild(this.sustainField);
        this.delayField = new TextFieldWidget(this.textRenderer, delayX, currentY, thirdWidth, 20,
                Text.translatable("gui.extendednoteblock.delay_ms"));
        this.delayField.setMaxLength(4); // 5000 是4位数
        this.delayField.setText(String.valueOf(this.delayedPlayingTime));
        this.delayField.setChangedListener(
                text -> this.delayedPlayingTime = parseInteger(text, 0, 5000, this.delayedPlayingTime));
        this.addDrawableChild(this.delayField);
    }

    /**
     * 处理鼠标点击事件。
     * 将事件委托给自定义部件（组合框和钢琴），以处理它们的特定交互。
     *
     * @param mouseX 鼠标的 X 坐标。
     * @param mouseY 鼠标的 Y 坐标。
     * @param button 被按下的鼠标按钮。
     * @return 如果事件被某个部件处理，则返回 {@code true}。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.instrumentComboBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.pianoWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 处理鼠标释放事件。
     * 主要用于重置钢琴滚动条的拖动状态。
     *
     * @param mouseX 鼠标的 X 坐标。
     * @param mouseY 鼠标的 Y 坐标。
     * @param button 被释放的鼠标按钮。
     * @return 如果事件被某个部件处理，则返回 {@code true}。
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.pianoWidget.isDraggingScrollbar = false;
        if (this.instrumentComboBox.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (this.pianoWidget.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 处理鼠标拖动事件。
     * 将事件委托给自定义部件，以处理滚动条的拖动。
     *
     * @param mouseX 鼠标的 X 坐标。
     * @param mouseY 鼠标的 Y 坐标。
     * @param button 被按下的鼠标按钮。
     * @param deltaX X 方向上的拖动距离。
     * @param deltaY Y 方向上的拖动距离。
     * @return 如果事件被某个部件处理，则返回 {@code true}。
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.instrumentComboBox.isDraggingScrollbar()
                && this.instrumentComboBox.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (this.pianoWidget.isDraggingScrollbar()
                && this.pianoWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    /**
     * 处理鼠标滚轮滚动事件。
     * 将事件委托给自定义部件，用于滚动组合框的下拉列表或钢琴键盘。
     *
     * @param mouseX 鼠标的 X 坐标。
     * @param mouseY 鼠标的 Y 坐标。
     * @param amount 滚动的量。
     * @return 如果事件被某个部件处理，则返回 {@code true}。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.instrumentComboBox.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        if (this.pianoWidget.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    /**
     * 一个自定义的可点击部件，用于渲染一个可滚动的钢琴键盘。
     * <p>
     * 用户可以通过点击琴键来选择一个音符，或者通过滚动条和鼠标滚轮来浏览全部 128 个 MIDI 音符。
     * 它通过回调函数与父屏幕通信，以更新所选音符和悬停信息。
     */
    private class PianoWidget extends ClickableWidget {
        private static final String[] NOTE_NAMES = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
        private static final boolean[] IS_BLACK_KEY = { false, true, false, true, false, false, true, false, true,
                false, true, false };
        private final Consumer<Integer> onNoteSelect;
        private final Consumer<Text> onHover;
        private final int whiteKeyCount = 75; // 128个音符中大约有75个白键
        private final int whiteKeyWidth = 12;
        private final int blackKeyWidth = 8;
        private final int blackKeyHeight;
        private final int totalWidth = whiteKeyCount * whiteKeyWidth; // 钢琴总宽度
        private double scrollOffset = 0.0; // 当前水平滚动偏移量
        public boolean isDraggingScrollbar = false; // 标记是否正在拖动滚动条

        /**
         * 构造一个新的钢琴部件。
         *
         * @param x            部件的 X 坐标。
         * @param y            部件的 Y 坐标。
         * @param width        部件的宽度。
         * @param height       部件的高度。
         * @param onNoteSelect 当一个音符被选中时调用的回调函数。
         * @param onHover      当鼠标悬停在一个琴键上时调用的回调函数，用于更新提示文本。
         */
        public PianoWidget(int x, int y, int width, int height, Consumer<Integer> onNoteSelect,
                Consumer<Text> onHover) {
            super(x, y, width, height, Text.empty());
            this.onNoteSelect = onNoteSelect;
            this.onHover = onHover;
            this.blackKeyHeight = (int) (height * 0.65);
            this.scrollToNote(ExtendedNoteBlockScreen.this.note); // 初始化时滚动到当前音符
        }

        /**
         * 检查用户是否正在拖动滚动条。
         * 
         * @return 如果正在拖动，返回 {@code true}。
         */
        public boolean isDraggingScrollbar() {
            return this.isDraggingScrollbar;
        }

        /**
         * {@inheritDoc}
         * <p>
         * 如果点击在滚动条上，则开始拖动。
         */
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && isMouseOverScrollbar(mouseX, mouseY)) {
                this.isDraggingScrollbar = true;
                this.mouseDragged(mouseX, mouseY, button, 0.0, 0.0); // 立即更新位置
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        /**
         * {@inheritDoc}
         * <p>
         * 释放鼠标时停止拖动滚动条。
         */
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0) {
                if (this.isDraggingScrollbar) {
                    this.isDraggingScrollbar = false;
                    return true;
                }
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        /**
         * {@inheritDoc}
         * <p>
         * 如果正在拖动滚动条，则根据鼠标位置更新滚动偏移量。
         */
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (this.isDraggingScrollbar) {
                double contentScrollRange = Math.max(0, this.totalWidth - this.width);
                if (contentScrollRange > 0) {
                    int handleWidth = Math.max(10, (int) ((double) this.width / this.totalWidth * this.width));
                    double trackWidth = this.width - handleWidth;
                    if (trackWidth > 0) {
                        double mouseRelativeX = mouseX - this.getX();
                        double handleTargetX = mouseRelativeX - (double) handleWidth / 2.0;
                        double scrollPercentage = MathHelper.clamp(handleTargetX / trackWidth, 0.0, 1.0);
                        this.scrollOffset = scrollPercentage * contentScrollRange;
                        clampScroll();
                    }
                }
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        /**
         * 检查鼠标是否在滚动条区域内。
         * 
         * @param mouseX 鼠标的 X 坐标。
         * @param mouseY 鼠标的 Y 坐标。
         * @return 如果鼠标在滚动条上，返回 {@code true}。
         */
        private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
            int scrollbarY = getY() + this.height + 2;
            int scrollbarHeight = 6;
            return mouseX >= getX() && mouseX < getX() + this.width && mouseY >= scrollbarY
                    && mouseY < scrollbarY + scrollbarHeight;
        }

        /**
         * {@inheritDoc}
         * <p>
         * 渲染钢琴键盘。这包括白键、黑键、八度标记和滚动条。
         * 会根据当前选中的音符和鼠标悬停的音符改变琴键颜色。
         */
        @Override
        protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            int left = getX();
            int top = getY();
            int hoveredKey = getHoveredKey(mouseX, mouseY);
            onHover.accept(hoveredKey != -1
                    ? Text.translatable("gui.extendednoteblock.piano.key_info", getNoteName(hoveredKey), hoveredKey)
                    : Text.empty());
            context.fill(left, top, left + this.width, top + this.height, 0xFF000000);
            context.enableScissor(left, top, left + this.width, top + this.height);
            // 绘制白键
            int whiteKeyIndex = 0;
            for (int i = 0; i < 128; i++) {
                if (!IS_BLACK_KEY[i % 12]) {
                    int keyX = left + (int) (whiteKeyIndex * whiteKeyWidth - scrollOffset);
                    if (keyX < left + this.width && keyX + whiteKeyWidth > left) {
                        int color = 0xFFFFFFFF;
                        if (i == ExtendedNoteBlockScreen.this.note)
                            color = 0xFF5555FF; // 选中颜色
                        else if (i == hoveredKey)
                            color = 0xFFCCCCCC; // 悬停颜色
                        context.fill(keyX, top, keyX + whiteKeyWidth, top + this.height, 0xFF000000);
                        context.fill(keyX + 1, top + 1, keyX + whiteKeyWidth - 1, top + this.height - 1, color);
                    }
                    whiteKeyIndex++;
                }
            }
            // 绘制黑键 (在白键之上)
            whiteKeyIndex = 0;
            for (int i = 0; i < 128; i++) {
                if (!IS_BLACK_KEY[i % 12]) {
                    whiteKeyIndex++;
                } else {
                    int keyX = left + (int) (whiteKeyIndex * whiteKeyWidth - (this.blackKeyWidth / 2.0) - scrollOffset);
                    if (keyX < left + this.width && keyX + blackKeyWidth > left) {
                        int color = 0xFF202020;
                        if (i == ExtendedNoteBlockScreen.this.note)
                            color = 0xFF0000AA; // 选中颜色
                        else if (i == hoveredKey)
                            color = 0xFF505050; // 悬停颜色
                        context.fill(keyX, top, keyX + blackKeyWidth, top + blackKeyHeight, color);
                    }
                }
            }
            context.disableScissor();
            // 绘制八度名称
            whiteKeyIndex = 0;
            for (int i = 0; i < 128; i++) {
                if (i % 12 == 0) { // 每个 C 音符
                    int keyX = left + (int) (whiteKeyIndex * whiteKeyWidth - scrollOffset);
                    if (keyX + whiteKeyWidth > left && keyX < left + this.width) {
                        String octaveName = getNoteName(i);
                        int textWidth = textRenderer.getWidth(octaveName);
                        int textX = keyX + (whiteKeyWidth - textWidth) / 2;
                        context.drawText(textRenderer, octaveName, textX, top + this.height - 12, 0xFF000000, false);
                    }
                }
                if (!IS_BLACK_KEY[i % 12]) {
                    whiteKeyIndex++;
                }
            }
            renderScrollbar(context);
        }

        /**
         * 渲染钢琴下方的水平滚动条。
         * 
         * @param context 绘制上下文。
         */
        private void renderScrollbar(DrawContext context) {
            int left = getX();
            int top = getY();
            int scrollbarY = top + this.height + 2;
            int scrollbarHeight = 6;
            // 绘制滚动条背景/轨道
            context.fill(left, scrollbarY, left + this.width, scrollbarY + scrollbarHeight, 0xFF000000);
            context.fill(left + 1, scrollbarY + 1, left + this.width - 1, scrollbarY + scrollbarHeight - 1, 0xFF555555);
            if (totalWidth > this.width) {
                // 计算并绘制滚动条滑块
                int handleWidth = Math.max(10, (int) ((double) this.width / totalWidth * this.width));
                int scrollableWidth = this.width - 2 - handleWidth;
                int handleX = left + 1 + (int) ((scrollOffset / (totalWidth - this.width)) * scrollableWidth);
                context.fill(handleX, scrollbarY + 1, handleX + handleWidth, scrollbarY + scrollbarHeight - 1,
                        0xFF888888);
            }
        }

        /**
         * {@inheritDoc}
         * <p>
         * 当点击一个琴键时，调用 {@code onNoteSelect} 回调，播放点击音效，并关闭屏幕。
         */
        @Override
        public void onClick(double mouseX, double mouseY) {
            int key = getHoveredKey(mouseX, mouseY);
            if (key != -1) {
                onNoteSelect.accept(key);
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ExtendedNoteBlockScreen.this.close(); // 选中音符后直接关闭界面
            }
        }

        /**
         * {@inheritDoc}
         * <p>
         * 如果鼠标在钢琴部件上，则使用滚轮来调整水平滚动偏移量。
         */
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            if (isHovered()) {
                scrollOffset -= amount * whiteKeyWidth * 2;
                clampScroll();
                return true;
            }
            return false;
        }

        /**
         * 根据鼠标坐标计算当前悬停的琴键。
         * <p>
         * 由于黑键覆盖在白键之上，此方法会优先检查黑键。
         *
         * @param mouseX 鼠标的 X 坐标。
         * @param mouseY 鼠标的 Y 坐标。
         * @return 悬停的琴键的 MIDI 音符编号 (0-127)，如果没有悬停在任何琴键上则返回 -1。
         */
        private int getHoveredKey(double mouseX, double mouseY) {
            if (!this.isHovered())
                return -1;
            int left = getX(), top = getY();
            // 优先检查黑键
            int whiteKeyIdx = 0;
            for (int i = 0; i < 128; i++) {
                if (!IS_BLACK_KEY[i % 12]) {
                    whiteKeyIdx++;
                } else {
                    int keyX = left + (int) (whiteKeyIdx * whiteKeyWidth - (this.blackKeyWidth / 2.0) - scrollOffset);
                    if (mouseX >= keyX && mouseX < keyX + blackKeyWidth && mouseY >= top
                            && mouseY < top + blackKeyHeight) {
                        return i;
                    }
                }
            }
            // 检查白键
            whiteKeyIdx = 0;
            for (int i = 0; i < 128; i++) {
                if (!IS_BLACK_KEY[i % 12]) {
                    int keyX = left + (int) (whiteKeyIdx * whiteKeyWidth - scrollOffset);
                    if (mouseX >= keyX && mouseX < keyX + whiteKeyWidth && mouseY >= top
                            && mouseY < top + this.height) {
                        return i;
                    }
                    whiteKeyIdx++;
                }
            }
            return -1;
        }

        /**
         * 将钢琴视图滚动到指定的音符，使其大致位于中心。
         * 
         * @param note 要滚动到的 MIDI 音符编号。
         */
        private void scrollToNote(int note) {
            int whiteKeyIndex = 0;
            for (int i = 0; i < note; i++) {
                if (!IS_BLACK_KEY[i % 12])
                    whiteKeyIndex++;
            }
            this.scrollOffset = (whiteKeyIndex * whiteKeyWidth) - (this.width / 2.0);
            clampScroll();
        }

        /**
         * 限制滚动偏移量，防止其超出有效范围。
         */
        private void clampScroll() {
            scrollOffset = MathHelper.clamp(scrollOffset, 0, Math.max(0, totalWidth - this.width));
        }

        /**
         * 将 MIDI 音符编号转换为音名和八度（例如，60 -> "C4"）。
         * 
         * @param midiNote MIDI 音符编号 (0-127)。
         * @return 格式化的音符名称字符串。
         */
        public static String getNoteName(int midiNote) {
            if (midiNote < 0 || midiNote > 127)
                return "??";
            int octave = (midiNote / 12) - 1;
            String note = NOTE_NAMES[midiNote % 12];
            return note + octave;
        }

        /**
         * {@inheritDoc}
         * 为辅助功能添加默认的旁白。
         */
        @Override
        public void appendClickableNarrations(NarrationMessageBuilder builder) {
            this.appendDefaultNarrations(builder);
        }
    }

    /**
     * 渲染屏幕上的所有元素。
     * <p>
     * 这包括背景、所有子部件、标题、标签以及悬停提示文本。
     *
     * @param context 绘制上下文。
     * @param mouseX  鼠标的 X 坐标。
     * @param mouseY  鼠标的 Y 坐标。
     * @param delta   自上一帧以来的时间。
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        // 绘制标题和标签
        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.instrument"),
                this.instrumentComboBox.getX(), this.instrumentComboBox.getY() - 10, 0xA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.velocity"),
                this.velocityField.getX(), this.velocityField.getY() - 10, 0xA0A0A0);
        Text sustainLabel = Text.translatable("gui.extendednoteblock.sustain_ticks");
        context.drawTextWithShadow(textRenderer, sustainLabel, this.sustainField.getX(), this.sustainField.getY() - 10,
                0xA0A0A0);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.sustain.info"),
                this.sustainField.getX() + 4, this.sustainField.getY() + this.sustainField.getHeight() + 4,
                0x808080);

        Text delayLabel = Text.translatable("gui.extendednoteblock.delay_ms");
        context.drawTextWithShadow(textRenderer, delayLabel, this.delayField.getX(), this.delayField.getY() - 10,
                0xA0A0A0);
        // 使用 delayLabel 和 delayField 的坐标来定位提示信息
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.delay_ms.info"),
                this.delayField.getX() + 4, this.delayField.getY() + this.delayField.getHeight() + 4, 0x808080);
        // 绘制底部中央的悬停提示
        context.drawCenteredTextWithShadow(textRenderer, this.hoveredKeyText, this.width / 2, this.height - 20,
                0xFFFFFF);
        // 渲染组合框的下拉列表（如果打开），确保它在最顶层
        this.instrumentComboBox.renderDropdownOverlay(context, mouseX, mouseY);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 在这个屏幕中，我们使用 {@link #render(DrawContext, int, int, float)} 中的
     * {@code renderBackground}，
     * 所以这个方法留空。
     */
    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * 在这个屏幕中，所有前景元素（如标签）都在主 {@link #render} 方法中绘制，
     * 以便更好地控制布局，因此这个方法留空。
     */
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
    }

    /**
     * 安全地将字符串解析为整数，并将其限制在指定的范围内。
     *
     * @param text         要解析的字符串。
     * @param min          允许的最小值（包含）。
     * @param max          允许的最大值（包含）。
     * @param defaultValue 如果解析失败，返回的默认值。
     * @return 解析并限制范围后的整数，或在失败时返回默认值。
     */
    private int parseInteger(String text, int min, int max, int defaultValue) {
        try {
            int value = Integer.parseInt(text);
            return MathHelper.clamp(value, min, max);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 创建一个包含所有更新后音符盒属性的网络数据包，并将其发送到服务器。
     */
    private void sendUpdatePacket() {
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(this.handler.blockPos);
            buf.writeInt(MathHelper.clamp(this.note, 0, 127));
            buf.writeInt(MathHelper.clamp(this.velocity, 0, 127));
            buf.writeInt(MathHelper.clamp(this.sustain, 0, 400));
            buf.writeInt(MathHelper.clamp(this.delayedPlayingTime, 0, 5000));
            buf.writeInt(MathHelper.clamp(this.instrumentId, 0, 128));
            ClientPlayNetworking.send(ModMessages.UPDATE_NOTE_BLOCK_ID, buf);
        } catch (Exception e) {
            System.err.println("Failed to send note block update packet: " + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 重写此方法以确保在屏幕关闭时，将所有更改发送到服务器。
     */
    @Override
    public void close() {
        sendUpdatePacket();
        super.close();
    }

    /**
     * 创建用于乐器选择组合框的选项列表。
     * <p>
     * 从 {@link InstrumentMap} 加载 General MIDI 乐器名称。
     *
     * @return 一个包含所有可用乐器的 {@link InstrumentOption} 列表。
     */
    private List<InstrumentOption> createInstrumentOptions() {
        List<InstrumentOption> options = new ArrayList<>();
        Map<Integer, String> instrumentNames = InstrumentMap.GM_INSTRUMENT_ID_TO_NAME;
        for (int i = 0; i <= 128; i++) {
            String name = instrumentNames.getOrDefault(i, "Unknown Instrument");
            options.add(new InstrumentOption(i, name));
        }
        return options;
    }

    /**
     * 在乐器选项列表中查找与给定乐器 ID 匹配的索引。
     *
     * @param options      乐器选项列表。
     * @param instrumentId 要查找的乐器 ID。
     * @return 匹配项的索引，如果未找到则返回 0。
     */
    private int findInstrumentIndex(List<InstrumentOption> options, int instrumentId) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).getId() == instrumentId)
                return i;
        }
        return 0; // 默认返回第一个选项
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@code false} 以允许游戏在 GUI 打开时继续在后台运行。
     */
    @Override
    public boolean shouldPause() {
        return false;
    }
}