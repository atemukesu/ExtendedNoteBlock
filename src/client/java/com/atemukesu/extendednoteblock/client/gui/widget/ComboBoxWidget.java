package com.atemukesu.extendednoteblock.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ComboBoxWidget<T> extends ClickableWidget {
    private static final int MAX_VISIBLE_ITEMS = 8;
    private static final int ITEM_HEIGHT = 12;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int MIN_SCROLLBAR_HEIGHT = 8;
    private static final int COLOR_MAIN_BOX_NORMAL = 0xFF404040;
    private static final int COLOR_MAIN_BOX_HOVERED = 0xFF5F5F5F;
    private static final int COLOR_BORDER = 0xFF8F8F8F;
    private static final int COLOR_DROPDOWN_BG = 0xE0101010;
    private static final int COLOR_DROPDOWN_BORDER = 0xFFAAAAAA;
    private static final int COLOR_ITEM_SELECTED = 0xFF4A90E2;
    private static final int COLOR_ITEM_HOVERED = 0xFF606060;
    private static final int COLOR_SCROLLBAR_TRACK = 0xFF202020;
    private static final int COLOR_SCROLLBAR_THUMB = 0xFF808080;
    private static final int COLOR_TEXT_NORMAL = 0xFFE0E0E0;
    private static final int COLOR_TEXT_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_TEXT_MAIN = 0xFFFFFF;
    private final List<T> options;
    private final Consumer<Integer> onSelect;
    private int selectedIndex;
    private boolean isOpen = false;
    private int scroll = 0;
    private boolean isDraggingScrollbar = false;

    public ComboBoxWidget(int x, int y, int width, int height, List<T> options, int initialIndex,
            Consumer<Integer> onSelect) {
        super(x, y, width, height, Text.empty());
        this.options = new ArrayList<>(options);
        this.selectedIndex = MathHelper.clamp(initialIndex, 0, Math.max(0, options.size() - 1));
        this.onSelect = onSelect;
        this.updateMessage();
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        int mainBoxColor = this.isHovered() && !this.isOpen ? COLOR_MAIN_BOX_HOVERED : COLOR_MAIN_BOX_NORMAL;
        context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(),
                mainBoxColor);
        context.drawBorder(this.getX(), this.getY(), this.getWidth(), this.getHeight(), COLOR_BORDER);
        String displayText = getDisplayText();
        if (!displayText.isEmpty()) {
            String truncatedText = truncateText(textRenderer, displayText, this.getWidth() - 20);
            context.drawText(textRenderer, truncatedText, this.getX() + 4, this.getY() + (this.height - 8) / 2,
                    COLOR_TEXT_MAIN, false);
        }
        int arrowX = this.getX() + this.getWidth() - 12;
        int arrowY = this.getY() + this.getHeight() / 2;
        context.drawText(textRenderer, this.isOpen ? "▲" : "▼", arrowX, arrowY - 4, COLOR_TEXT_MAIN, false);
    }

    public void renderDropdownOverlay(DrawContext context, int mouseX, int mouseY) {
        if (this.isOpen && !this.options.isEmpty()) {
            MinecraftClient client = MinecraftClient.getInstance();
            TextRenderer textRenderer = client.textRenderer;
            renderDropdown(context, mouseX, mouseY, textRenderer);
        }
    }

    private String getDisplayText() {
        return this.selectedIndex >= 0 && this.selectedIndex < this.options.size()
                ? this.options.get(this.selectedIndex).toString()
                : "";
    }

    private String truncateText(TextRenderer textRenderer, String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        return textRenderer.trimToWidth(text, maxWidth - textRenderer.getWidth(ellipsis)) + ellipsis;
    }

    private void renderDropdown(DrawContext context, int mouseX, int mouseY, TextRenderer textRenderer) {
        int dropdownY = this.getY() + this.getHeight();
        int visibleItems = Math.min(MAX_VISIBLE_ITEMS, this.options.size());
        int dropdownHeight = visibleItems * ITEM_HEIGHT + 2;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);
        context.fill(this.getX(), dropdownY, this.getX() + this.getWidth(), dropdownY + dropdownHeight,
                COLOR_DROPDOWN_BG);
        context.drawBorder(this.getX(), dropdownY, this.getWidth(), dropdownHeight, COLOR_DROPDOWN_BORDER);
        int totalContentHeight = this.options.size() * ITEM_HEIGHT;
        int maxScroll = Math.max(0, totalContentHeight - (visibleItems * ITEM_HEIGHT));
        int scrollOffset = MathHelper.clamp(this.scroll, 0, maxScroll);
        int contentWidth = this.options.size() > MAX_VISIBLE_ITEMS ? this.getWidth() - SCROLLBAR_WIDTH - 2
                : this.getWidth() - 2;
        context.enableScissor(this.getX() + 1, dropdownY + 1, this.getX() + contentWidth + 1,
                dropdownY + dropdownHeight - 1);
        for (int i = 0; i < this.options.size(); i++) {
            int itemY = dropdownY + 1 + (i * ITEM_HEIGHT) - scrollOffset;
            if (itemY + ITEM_HEIGHT < dropdownY + 1 || itemY > dropdownY + dropdownHeight - 1) {
                continue;
            }
            boolean isHovered = mouseX >= this.getX() + 1 && mouseX < this.getX() + contentWidth + 1 &&
                    mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean isSelected = i == this.selectedIndex;
            if (isSelected) {
                context.fill(this.getX() + 1, itemY, this.getX() + contentWidth + 1, itemY + ITEM_HEIGHT,
                        COLOR_ITEM_SELECTED);
            } else if (isHovered) {
                context.fill(this.getX() + 1, itemY, this.getX() + contentWidth + 1, itemY + ITEM_HEIGHT,
                        COLOR_ITEM_HOVERED);
            }
            String itemText = this.options.get(i).toString();
            String truncatedText = truncateText(textRenderer, itemText, contentWidth - 8);
            int textColor = isSelected ? COLOR_TEXT_SELECTED : COLOR_TEXT_NORMAL;
            context.drawText(textRenderer, truncatedText, this.getX() + 4, itemY + 2, textColor, false);
        }
        context.disableScissor();
        if (maxScroll > 0) {
            renderScrollbar(context, dropdownY, dropdownHeight, maxScroll);
        }
        context.getMatrices().pop();
    }

    private void renderScrollbar(DrawContext context, int dropdownY, int dropdownHeight, int maxScroll) {
        int scrollbarX = this.getX() + this.getWidth() - SCROLLBAR_WIDTH - 1;
        int scrollbarTrackHeight = dropdownHeight - 2;
        context.fill(scrollbarX, dropdownY + 1, scrollbarX + SCROLLBAR_WIDTH, dropdownY + dropdownHeight - 1,
                COLOR_SCROLLBAR_TRACK);
        int visibleItems = Math.min(MAX_VISIBLE_ITEMS, this.options.size());
        float scrollbarRatio = (float) visibleItems / (float) this.options.size();
        int scrollbarHeight = Math.max(MIN_SCROLLBAR_HEIGHT, (int) (scrollbarTrackHeight * scrollbarRatio));
        float scrollRatio = maxScroll > 0 ? (float) this.scroll / maxScroll : 0;
        int scrollbarY = dropdownY + 1 + (int) (scrollRatio * (scrollbarTrackHeight - scrollbarHeight));
        context.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight,
                COLOR_SCROLLBAR_THUMB);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || button != 0) {
            return false;
        }
        if (isMouseOverMainBox(mouseX, mouseY)) {
            this.isOpen = !this.isOpen;
            if (!this.isOpen) {
                this.isDraggingScrollbar = false;
            } else {
                ensureSelectedItemVisible();
            }
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            return true;
        }
        if (this.isOpen) {
            int dropdownY = this.getY() + this.getHeight();
            int visibleItems = Math.min(MAX_VISIBLE_ITEMS, this.options.size());
            int dropdownHeight = visibleItems * ITEM_HEIGHT + 2;
            boolean isClickInDropdownBounds = mouseX >= this.getX() && mouseX < this.getX() + this.getWidth() &&
                    mouseY >= dropdownY && mouseY < dropdownY + dropdownHeight;
            if (isClickInDropdownBounds) {
                boolean needsScrollbar = this.options.size() > MAX_VISIBLE_ITEMS;
                boolean isClickOnScrollbar = needsScrollbar
                        && mouseX >= this.getX() + this.getWidth() - SCROLLBAR_WIDTH - 1;
                if (isClickOnScrollbar) {
                    this.isDraggingScrollbar = true;
                    updateScrollFromMousePosition(mouseY, dropdownY, dropdownHeight);
                    return true;
                }
                int totalContentHeight = this.options.size() * ITEM_HEIGHT;
                int maxScroll = Math.max(0, totalContentHeight - (visibleItems * ITEM_HEIGHT));
                int scrollOffset = MathHelper.clamp(this.scroll, 0, maxScroll);
                int clickedItemIndex = ((int) mouseY - dropdownY - 1 + scrollOffset) / ITEM_HEIGHT;
                if (clickedItemIndex >= 0 && clickedItemIndex < this.options.size()) {
                    this.setSelectedIndex(clickedItemIndex);
                    this.isOpen = false;
                    this.isDraggingScrollbar = false;
                    this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                    return true;
                }
                return true;
            } else {
                this.isOpen = false;
                this.isDraggingScrollbar = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.isOpen && this.options.size() > MAX_VISIBLE_ITEMS) {
            int dropdownY = this.getY() + this.getHeight();
            int visibleItems = Math.min(MAX_VISIBLE_ITEMS, this.options.size());
            int dropdownHeight = visibleItems * ITEM_HEIGHT + 2;
            if (mouseX >= this.getX() && mouseX < this.getX() + this.getWidth() &&
                    mouseY >= dropdownY && mouseY < dropdownY + dropdownHeight) {
                int totalContentHeight = this.options.size() * ITEM_HEIGHT;
                int maxScroll = Math.max(0, totalContentHeight - (visibleItems * ITEM_HEIGHT));
                this.scroll = MathHelper.clamp(this.scroll - (int) (amount * ITEM_HEIGHT), 0, maxScroll);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDraggingScrollbar && this.options.size() > MAX_VISIBLE_ITEMS) {
            int dropdownY = this.getY() + this.getHeight();
            int visibleItems = Math.min(MAX_VISIBLE_ITEMS, this.options.size());
            int dropdownHeight = visibleItems * ITEM_HEIGHT + 2;
            updateScrollFromMousePosition(mouseY, dropdownY, dropdownHeight);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void updateScrollFromMousePosition(double mouseY, int dropdownY, int dropdownHeight) {
        int scrollbarTrackHeight = dropdownHeight - 2;
        int visibleItems = Math.min(MAX_VISIBLE_ITEMS, this.options.size());
        int totalContentHeight = this.options.size() * ITEM_HEIGHT;
        int maxScroll = Math.max(0, totalContentHeight - (visibleItems * ITEM_HEIGHT));
        float scrollbarRatio = (float) (visibleItems * ITEM_HEIGHT) / (float) totalContentHeight;
        int scrollbarHeight = Math.max(MIN_SCROLLBAR_HEIGHT, (int) (scrollbarTrackHeight * scrollbarRatio));
        double scrollableArea = scrollbarTrackHeight - scrollbarHeight;
        if (scrollableArea <= 0) {
            return;
        }
        double relativeMouseY = mouseY - dropdownY - 1;
        double clampedMouseY = MathHelper.clamp(relativeMouseY - scrollbarHeight / 2.0, 0.0, scrollableArea);
        double scrollPercent = clampedMouseY / scrollableArea;
        this.scroll = (int) (scrollPercent * maxScroll);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDraggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isMouseOverMainBox(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX < this.getX() + this.getWidth() &&
                mouseY >= this.getY() && mouseY < this.getY() + this.getHeight();
    }

    private void ensureSelectedItemVisible() {
        if (this.selectedIndex < 0 || this.options.size() <= MAX_VISIBLE_ITEMS) {
            return;
        }
        int selectedItemTop = this.selectedIndex * ITEM_HEIGHT;
        int selectedItemBottom = selectedItemTop + ITEM_HEIGHT;
        int visibleTop = this.scroll;
        int visibleBottom = this.scroll + (MAX_VISIBLE_ITEMS * ITEM_HEIGHT);
        if (selectedItemTop < visibleTop) {
            this.scroll = selectedItemTop;
        } else if (selectedItemBottom > visibleBottom) {
            this.scroll = selectedItemBottom - (MAX_VISIBLE_ITEMS * ITEM_HEIGHT);
        }
        int totalContentHeight = this.options.size() * ITEM_HEIGHT;
        int maxScroll = Math.max(0, totalContentHeight - (MAX_VISIBLE_ITEMS * ITEM_HEIGHT));
        this.scroll = MathHelper.clamp(this.scroll, 0, maxScroll);
    }

    public void setSelectedIndex(int index) {
        int oldIndex = this.selectedIndex;
        this.selectedIndex = MathHelper.clamp(index, 0, Math.max(0, this.options.size() - 1));
        if (this.selectedIndex != oldIndex) {
            this.updateMessage();
            if (this.onSelect != null) {
                this.onSelect.accept(this.selectedIndex);
            }
            if (this.isOpen) {
                ensureSelectedItemVisible();
            }
        }
    }

    public int getSelectedIndex() {
        return this.selectedIndex;
    }

    public T getSelectedValue() {
        if (this.selectedIndex >= 0 && this.selectedIndex < this.options.size()) {
            return this.options.get(this.selectedIndex);
        }
        return null;
    }

    private void updateMessage() {
        if (this.selectedIndex >= 0 && this.selectedIndex < this.options.size()) {
            this.setMessage(Text.of(this.options.get(this.selectedIndex).toString()));
        } else {
            this.setMessage(Text.literal(""));
        }
    }

    public boolean isDraggingScrollbar() {
        return this.isDraggingScrollbar;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        super.playDownSound(soundManager);
    }

    public void closeDropdown() {
        this.isOpen = false;
        this.isDraggingScrollbar = false;
    }

    public boolean isDropdownOpen() {
        return this.isOpen;
    }

    public void setOptions(List<T> newOptions) {
        this.options.clear();
        this.options.addAll(newOptions);
        if (this.selectedIndex >= this.options.size()) {
            this.selectedIndex = Math.max(0, this.options.size() - 1);
        }
        this.scroll = 0;
        this.updateMessage();
    }

    public List<T> getOptions() {
        return new ArrayList<>(this.options);
    }

    public void addOption(T option) {
        this.options.add(option);
        this.updateMessage();
    }

    public void removeOption(int index) {
        if (index >= 0 && index < this.options.size()) {
            this.options.remove(index);
            if (this.selectedIndex >= this.options.size()) {
                this.selectedIndex = Math.max(0, this.options.size() - 1);
            }
            this.updateMessage();
        }
    }

    public void clearOptions() {
        this.options.clear();
        this.selectedIndex = -1;
        this.scroll = 0;
        this.updateMessage();
    }
}