package com.atemukesu.extendednoteblock.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class EnbBulkEditorWidget extends ElementListWidget<EnbBulkEditorWidget.Entry> {

    public EnbBulkEditorWidget(MinecraftClient client, int width, int height, int top, int bottom) {
        super(client, width, height, top, bottom, 50); // 每行 50 像素高
        this.centerListVertically = false;
    }

    @Override
    public int getRowWidth() {
        return 380;
    }

    public void addSection(Text title) {
        this.addEntry(new HeaderEntry(title));
    }

    @Override
    public int addEntry(Entry entry) {
        return super.addEntry(entry);
    }

    @Override
    protected int getScrollbarPositionX() {
        return this.width - 15;
    }

    // 基础条目抽象类
    public abstract static class Entry extends ElementListWidget.Entry<Entry> {
        public abstract String getKey();

        public abstract String getValue();
    }

    // 1. 标题装饰条目
    public static class HeaderEntry extends Entry {
        private final Text text;

        public HeaderEntry(Text text) {
            this.text = text;
        }

        @Override
        public String getKey() {
            return "";
        }

        @Override
        public String getValue() {
            return "";
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int width, int height, int mouseX, int mouseY,
                boolean hovered, float delta) {
            context.fill(x, y + height - 5, x + width, y + height - 4, 0x55FFFFFF);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, text, x, y + 15, 0xFFAA00);
        }

        @Override
        public List<? extends Element> children() {
            return List.of();
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of();
        }
    }

    // 2. 数值参数条目 (Note, Velocity, Sustain)
    public static class NumberEntry extends Entry {
        private final String key;
        private final Text label;
        public final TextFieldWidget input;
        public final net.minecraft.client.gui.widget.ButtonWidget modeButton;
        public int mode = 0; // 0=SET, 1=ADD, 2=MULT
        private final boolean showMode;

        public NumberEntry(String key, Text label, String initialValue) {
            this(key, label, initialValue, true);
        }

        public NumberEntry(String key, Text label, String initialValue, boolean showMode) {
            this.key = key;
            this.label = label;
            this.showMode = showMode;
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            this.input = new TextFieldWidget(tr, 0, 0, 60, 16, label);
            this.input.setText(initialValue);

            if (showMode) {
                this.modeButton = net.minecraft.client.gui.widget.ButtonWidget.builder(getModeText(), b -> {
                    this.mode = (this.mode + 1) % 3;
                    b.setMessage(getModeText());
                }).dimensions(0, 0, 20, 16).build();
            } else {
                this.modeButton = null;
            }
        }

        private Text getModeText() {
            return switch (mode) {
                case 1 -> Text.literal("+").formatted(net.minecraft.util.Formatting.GREEN);
                case 2 -> Text.literal("×").formatted(net.minecraft.util.Formatting.AQUA);
                default -> Text.literal("=").formatted(net.minecraft.util.Formatting.YELLOW);
            };
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getValue() {
            return input.getText();
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int width, int height, int mouseX, int mouseY,
                boolean hovered, float delta) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, label, x, y + 12, 0xFFFFFF);

            // Mode Button
            if (showMode && modeButton != null) {
                modeButton.setX(x + width - 95);
                modeButton.setY(y + 8);
                modeButton.render(context, mouseX, mouseY, delta);
            }

            // Input
            input.setX(x + width - 70);
            input.setY(y + 8);
            input.render(context, mouseX, mouseY, delta);
        }

        @Override
        public List<? extends Element> children() {
            if (showMode && modeButton != null) {
                return List.of(input, modeButton);
            }
            return List.of(input);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            if (showMode && modeButton != null) {
                return List.of(input, modeButton);
            }
            return List.of(input);
        }
    }

    // 3. 表达式条目 (X, Y, Z)
    public static class MathEntry extends Entry {
        private final String id;
        public final MathExpressionWidget input;

        public MathEntry(String id, String initialValue) {
            this.id = id;
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            this.input = new MathExpressionWidget(tr, 0, 0, 200, 16, Text.of(id));
            this.input.setText(initialValue);
        }

        @Override
        public String getKey() {
            return "Expression" + id;
        }

        @Override
        public String getValue() {
            return input.getText();
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int width, int height, int mouseX, int mouseY,
                boolean hovered, float delta) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, "§d" + id + "(t, d)", x, y + 12,
                    0xFFFFFF);
            input.setX(x + width - 210);
            input.setY(y + 8);
            input.render(context, mouseX, mouseY, delta);
        }

        @Override
        public List<? extends Element> children() {
            return List.of(input);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(input);
        }
    }

    // 4. 曲线条目 (Volume, Pitch)
    public static class CurveEntry extends Entry {
        public final VisualCurveWidget curve;
        private final boolean isVol;

        public CurveEntry(String title, boolean isVol, NbtList pts) {
            this.isVol = isVol;
            this.curve = new VisualCurveWidget(0, 0, 340, 80, title, "",
                    isVol ? 0f : -24f, isVol ? 2f : 24f, isVol ? 0xFF55FF55 : 0xFFFFFF55, isVol);

            if (pts != null) {
                List<VisualCurveWidget.DataPoint> data = new ArrayList<>();
                for (int i = 0; i < pts.size(); i++) {
                    NbtCompound tag = pts.getCompound(i);
                    data.add(new VisualCurveWidget.DataPoint(tag.getFloat("t"), tag.getFloat("v")));
                }
                curve.setPoints(data);
            }
        }

        @Override
        public String getKey() {
            return isVol ? "VolumePoints" : "PitchBendPoints";
        }

        @Override
        public String getValue() {
            return "";
        } // 曲线值通过 getPoints 拿

        @Override
        public void render(DrawContext context, int index, int y, int x, int width, int height, int mouseX, int mouseY,
                boolean hovered, float delta) {
            curve.setX(x);
            curve.setY(y + 5);
            curve.setWidth(width);
            curve.setHeight(80);
            curve.render(context, mouseX, mouseY, delta);
        }

        public int getHeight() {
            return 90;
        }

        @Override
        public List<? extends Element> children() {
            return List.of(curve);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(curve);
        }
    }
}