package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.client.gui.widget.MathExpressionWidget;
import com.atemukesu.extendednoteblock.client.gui.widget.VisualCurveWidget;
import com.atemukesu.extendednoteblock.network.ClientModMessages;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Removed unused imports handled by optimization? 
// No, I need to keep imports that are actually used.
// Checking what is used: ButtonWidget (Yes), TextFieldWidget (Yes), NbtCompound (Yes), Text (Yes), BlockPos (Yes)
// CurvePoint and Vec3d are used in previous code but mainly NbtList handling now?
// Actually VisualCurveWidget handles points.
// Let's keep just what we need.

public class ConductorScreen extends Screen {
    private final BlockPos min, max;
    private final NbtCompound sample;

    // UI Components
    private TextFieldWidget noteInput, velocityInput, sustainInput;
    private TextFieldWidget fadeInInput, fadeOutInput;

    private VisualCurveWidget volCurve;
    private VisualCurveWidget pitchCurve;
    private TextFieldWidget rangeInput;
    private MathExpressionWidget exprX, exprY, exprZ;

    // State
    private int pitchBendRange = 2;
    private String errorMessageX, errorMessageY, errorMessageZ;
    private long errorDisplayTimeX, errorDisplayTimeY, errorDisplayTimeZ;
    private static final long ERROR_DISPLAY_DURATION = 5000;

    // Field Modes: "note" -> 0=Set, 1=Add, 2=Mult, 3=Div, 4=Sub
    private final Map<String, Integer> fieldModes = new HashMap<>();
    private final Map<String, ButtonWidget> fieldModeButtons = new HashMap<>();
    private final Map<String, TextFieldWidget> fieldInputs = new HashMap<>(); // [NEW] Track inputs

    public ConductorScreen(BlockPos min, BlockPos max, Map<String, Integer> counts, Map<String, NbtCompound> samples) {
        super(Text.translatable("gui.extendednoteblock.conductor.title"));
        this.min = min;
        this.max = max;
        this.sample = samples.getOrDefault("extendednoteblock:extended_note_block", new NbtCompound());

        try {
            this.pitchBendRange = com.atemukesu.extendednoteblock.config.ConfigManager.getConfig().pitchBendRange;
        } catch (Exception e) {
            this.pitchBendRange = 2;
        }
    }

    @Override
    protected void init() {
        int sidebarWidth = 160; // Increased sidebar width for mode buttons
        int canvasWidth = this.width - sidebarWidth - 30;
        int canvasHeight = (this.height - 180) / 2;
        int gap = 20;

        // --- Left Sidebar: Basic Parameters ---
        int leftX = 20;
        int currentY = 50;

        // Field setup helper
        // Note
        noteInput = createFieldRow("note", leftX, currentY, 60);
        currentY += 35;

        // Velocity
        velocityInput = createFieldRow("velocity", leftX, currentY, 100);
        currentY += 35;

        // Sustain
        sustainInput = createFieldRow("sustainTime", leftX, currentY, 40);
        currentY += 35;

        // Fade In
        fadeInInput = createFieldRow("fadeInTime", leftX, currentY, 0);
        currentY += 35;

        // Fade Out
        fadeOutInput = createFieldRow("fadeOutTime", leftX, currentY, 0);
        currentY += 35;

        // --- Right Area: Advanced Curves ---
        int curveX = sidebarWidth + 10;
        int curveYStart = 35;

        // Vol Curve
        volCurve = new VisualCurveWidget(curveX, curveYStart, canvasWidth, canvasHeight,
                Text.translatable("gui.extendednoteblock.advanced.volume_envelope").getString(),
                Text.translatable("gui.extendednoteblock.advanced.volume_tooltip_format").getString(),
                0f, 2f, 0xFF55FF55, true);
        addDrawableChild(volCurve);

        // Pitch Curve
        int pitchY = curveYStart + canvasHeight + gap;
        pitchCurve = new VisualCurveWidget(curveX, pitchY, canvasWidth, canvasHeight,
                Text.translatable("gui.extendednoteblock.advanced.pitch_bend_semitones").getString(),
                Text.translatable("gui.extendednoteblock.advanced.pitch_tooltip_format").getString(),
                -pitchBendRange, pitchBendRange, 0xFFFFFF55, false);
        addDrawableChild(pitchCurve);

        // Range Config for Pitch
        rangeInput = new TextFieldWidget(textRenderer, curveX + canvasWidth - 50, pitchY - 18, 50, 16,
                Text.translatable("gui.extendednoteblock.advanced.range"));
        rangeInput.setText(String.valueOf(pitchBendRange));
        rangeInput.setChangedListener(this::onRangeChanged);
        addDrawableChild(rangeInput);

        // --- Bottom Area: Expressions ---
        int exprYPos = pitchY + canvasHeight + 20;
        int exprWidth = (canvasWidth) / 3 - 5;

        exprX = new MathExpressionWidget(textRenderer, curveX, exprYPos, exprWidth, 20,
                Text.translatable("gui.extendednoteblock.advanced.x_axis"));
        exprY = new MathExpressionWidget(textRenderer, curveX + exprWidth + 5, exprYPos, exprWidth, 20,
                Text.translatable("gui.extendednoteblock.advanced.y_axis"));
        exprZ = new MathExpressionWidget(textRenderer, curveX + (exprWidth + 5) * 2, exprYPos, exprWidth, 20,
                Text.translatable("gui.extendednoteblock.advanced.z_axis"));

        exprX.setTextChangeCallback(t -> validateSingleExpression("X", t, () -> errorMessageX, m -> errorMessageX = m,
                () -> errorDisplayTimeX, time -> errorDisplayTimeX = time));
        exprY.setTextChangeCallback(t -> validateSingleExpression("Y", t, () -> errorMessageY, m -> errorMessageY = m,
                () -> errorDisplayTimeY, time -> errorDisplayTimeY = time));
        exprZ.setTextChangeCallback(t -> validateSingleExpression("Z", t, () -> errorMessageZ, m -> errorMessageZ = m,
                () -> errorDisplayTimeZ, time -> errorDisplayTimeZ = time));

        addDrawableChild(exprX);
        addDrawableChild(exprY);
        addDrawableChild(exprZ);

        // --- Action Buttons ---
        int btnY = height - 30;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.extendednoteblock.conductor.button.clear"), b -> {
            ClientModMessages.sendClearSelectionToServer();
            this.close();
        }).dimensions(20, btnY, 80, 20).build());

        addDrawableChild(
                ButtonWidget.builder(Text.translatable("gui.extendednoteblock.conductor.button.apply"), b -> apply())
                        .dimensions(110, btnY, 80, 20).build());

        loadSampleData();
    }

    // Helper to create: [Mode] [-] [Input] [+]
    // Modes: -1=Keep, 0=Set, 1=Add, 4=Sub, 2=Mult, 3=Div
    private TextFieldWidget createFieldRow(String key, int x, int y, int defaultValue) {
        // Default Mode: Keep (-1)
        fieldModes.putIfAbsent(key, -1);

        // Mode Button
        ButtonWidget modeBtn = ButtonWidget.builder(getModeText(fieldModes.get(key)), b -> {
            cycleMode(key, b);
        }).dimensions(x, y, 20, 20).build();
        addDrawableChild(modeBtn);
        fieldModeButtons.put(key, modeBtn);

        // Minus Button
        TextFieldWidget input = new TextFieldWidget(textRenderer, x + 46, y, 40, 20, Text.literal(""));
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> adjustField(key, input, -1))
                .dimensions(x + 24, y, 20, 20).build());

        // Input Field
        int sampleVal = sample.contains(key) ? sample.getInt(key) : defaultValue;
        input.setText(String.valueOf(sampleVal));
        addDrawableChild(input);
        fieldInputs.put(key, input); // Track input

        // Plus Button
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> adjustField(key, input, 1))
                .dimensions(x + 88, y, 20, 20).build());

        // Initialize Visual State
        updateFieldVisuals(key, fieldModes.get(key));

        return input;
    }

    private void cycleMode(String key, ButtonWidget btn) {
        int m = fieldModes.getOrDefault(key, -1);
        // Cycle: -1(Keep) -> 0(Set) -> 1(Add) -> 4(Sub) -> 2(Mult) -> 3(Div) -> -1
        m = switch (m) {
            case -1 -> 0;
            case 0 -> 1;
            case 1 -> 4;
            case 4 -> 2;
            case 2 -> 3;
            case 3 -> -1;
            default -> -1;
        };
        fieldModes.put(key, m);
        btn.setMessage(getModeText(m));

        // Update Visuals based on new mode
        updateFieldVisuals(key, m);
    }

    private void updateFieldVisuals(String key, int mode) {
        TextFieldWidget input = fieldInputs.get(key);
        if (input == null)
            return;

        if (mode == -1) {
            // Keep Mode: Disable input and gray out
            input.setEditable(false);
            input.setEditableColor(0xFF888888); // Gray
        } else {
            // Normal Mode
            input.setEditable(true);
            input.setEditableColor(0xFFE0E0E0); // White
        }
    }

    private Text getModeText(int mode) {
        return switch (mode) {
            case 0 -> Text.literal("=").formatted(Formatting.YELLOW);
            case 1 -> Text.literal("+").formatted(Formatting.GREEN);
            case 2 -> Text.literal("x").formatted(Formatting.AQUA);
            case 3 -> Text.literal("/").formatted(Formatting.AQUA);
            case 4 -> Text.literal("-").formatted(Formatting.RED);
            default -> Text.literal("K").formatted(Formatting.GRAY); // Keep
        };
    }

    private void adjustField(String key, TextFieldWidget field, int delta) {
        if (fieldModes.getOrDefault(key, -1) == -1)
            return; // Don't adjust if in Keep mode
        try {
            // For Set/Add/Sub, we just adjust integer value?
            // If mode calls for decimals (Mult/Div), this +/- might be weird.
            // But assume normal integer adjustment for now.
            float val = Float.parseFloat(field.getText());
            // If integer string, keep it integer
            if (field.getText().contains(".")) {
                field.setText(String.format("%.1f", val + delta));
            } else {
                field.setText(String.valueOf((int) val + delta));
            }
        } catch (NumberFormatException e) {
            field.setText(String.valueOf(delta));
        }
    }

    private void onRangeChanged(String text) {
        try {
            int r = Integer.parseInt(text);
            if (r > 0 && r <= 48) {
                this.pitchBendRange = r;
                pitchCurve.setMinMax(-r, r);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void loadSampleData() {
        if (sample.contains("AdvancedData")) {
            NbtCompound adv = sample.getCompound("AdvancedData");
            if (adv.contains("VolumePoints"))
                loadPoints(volCurve, adv.getList("VolumePoints", 10));
            if (adv.contains("PitchBendPoints"))
                loadPoints(pitchCurve, adv.getList("PitchBendPoints", 10));

            if (adv.contains("ExpressionX"))
                exprX.setText(adv.getString("ExpressionX"));
            if (adv.contains("ExpressionY"))
                exprY.setText(adv.getString("ExpressionY"));
            if (adv.contains("ExpressionZ"))
                exprZ.setText(adv.getString("ExpressionZ"));
        }
    }

    private void loadPoints(VisualCurveWidget widget, NbtList list) {
        List<VisualCurveWidget.DataPoint> points = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound p = list.getCompound(i);
            points.add(new VisualCurveWidget.DataPoint(p.getFloat("t"), p.getFloat("v")));
        }
        widget.setPoints(points);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("gui.extendednoteblock.conductor.title_styled"), width / 2, 8, 0xFFD4AF37);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("gui.extendednoteblock.conductor.selection_info", min.toShortString(),
                        max.toShortString()),
                width / 2, 20, 0xFFAAAAAA);

        // Sidebar Labels
        int leftX = 20;
        int currentY = 50;
        drawLabel(context, "gui.extendednoteblock.conductor.param.note", leftX, currentY);
        currentY += 35;
        drawLabel(context, "gui.extendednoteblock.conductor.param.velocity", leftX, currentY);
        currentY += 35;
        drawLabel(context, "gui.extendednoteblock.conductor.param.sustain", leftX, currentY);
        currentY += 35;
        drawLabel(context, "gui.extendednoteblock.fadein_time", leftX, currentY);
        currentY += 35;
        drawLabel(context, "gui.extendednoteblock.fadeout_time", leftX, currentY);
        currentY += 35;

        // Expression Labels
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.advanced.x_axis"),
                exprX.getX(), exprX.getY() - 10, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.advanced.y_axis"),
                exprY.getX(), exprY.getY() - 10, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.advanced.z_axis"),
                exprZ.getX(), exprZ.getY() - 10, 0xAAAAAA);

        // Range Label
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.advanced.range_label"),
                rangeInput.getX() - 50, rangeInput.getY() + 4, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);

        drawExpressionError(context, exprX, errorMessageX, errorDisplayTimeX);
        drawExpressionError(context, exprY, errorMessageY, errorDisplayTimeY);
        drawExpressionError(context, exprZ, errorMessageZ, errorDisplayTimeZ);
    }

    private void drawLabel(DrawContext context, String key, int x, int y) {
        context.drawTextWithShadow(textRenderer, Text.translatable(key), x, y - 10, 0xFFFFFF);
    }

    private void apply() {
        if (!validateExpressions())
            return;

        List<ClientModMessages.BulkUpdateEntry> updates = new ArrayList<>();
        NbtCompound rootPatch = new NbtCompound();
        NbtCompound advancedData = new NbtCompound();

        // 1. Basic Fields with Modes
        addUpdate(updates, "note", noteInput);
        addUpdate(updates, "velocity", velocityInput);
        addUpdate(updates, "sustainTime", sustainInput);
        addUpdate(updates, "fadeInTime", fadeInInput);
        addUpdate(updates, "fadeOutTime", fadeOutInput);

        // 2. Curves
        NbtList volPoints = new NbtList();
        for (VisualCurveWidget.DataPoint p : volCurve.getPoints()) {
            NbtCompound tag = new NbtCompound();
            tag.putFloat("t", p.timePercent);
            tag.putFloat("v", p.value);
            volPoints.add(tag);
        }
        advancedData.put("VolumePoints", volPoints);

        NbtList pitchPoints = new NbtList();
        for (VisualCurveWidget.DataPoint p : pitchCurve.getPoints()) {
            NbtCompound tag = new NbtCompound();
            tag.putFloat("t", p.timePercent);
            tag.putFloat("v", p.value);
            pitchPoints.add(tag);
        }
        advancedData.put("PitchBendPoints", pitchPoints);

        // 3. Sound Path & Expressions
        String sx = exprX.getText(), sy = exprY.getText(), sz = exprZ.getText();
        advancedData.putString("ExpressionX", sx);
        advancedData.putString("ExpressionY", sy);
        advancedData.putString("ExpressionZ", sz);

        int sustain = 40;
        try {
            sustain = Integer.parseInt(sustainInput.getText());
        } catch (Exception ignored) {
        }

        if (!sx.isEmpty() || !sy.isEmpty() || !sz.isEmpty()) {
            NbtList path = generatePath(sx, sy, sz, sustain);
            if (path != null)
                advancedData.put("SoundPath", path);
        } else {
            advancedData.put("SoundPath", new NbtList());
        }

        if (!advancedData.isEmpty()) {
            rootPatch.put("AdvancedData", advancedData);
        }

        ClientModMessages.sendSmartBulkUpdateToServer(min, max, "extendednoteblock:extended_note_block", updates,
                rootPatch);
        this.close();
    }

    private void addUpdate(List<ClientModMessages.BulkUpdateEntry> updates, String key, TextFieldWidget input) {
        int mode = fieldModes.getOrDefault(key, -1);
        if (mode == -1)
            return; // Skip if Keep mode
        String val = input.getText();
        updates.add(new ClientModMessages.BulkUpdateEntry(key, mode, val));
    }

    private NbtList generatePath(String ex, String ey, String ez, int sustain) {
        NbtList list = new NbtList();
        try {
            Expression eX = new ExpressionBuilder(ex.isEmpty() ? "0" : ex).variables("t", "d").build();
            Expression eY = new ExpressionBuilder(ey.isEmpty() ? "0" : ey).variables("t", "d").build();
            Expression eZ = new ExpressionBuilder(ez.isEmpty() ? "0" : ez).variables("t", "d").build();

            for (int i = 0; i < sustain; i++) {
                double t = (double) i / Math.max(1, sustain);
                NbtCompound pos = new NbtCompound();
                pos.putDouble("x", eX.setVariable("t", t).setVariable("d", i).evaluate());
                pos.putDouble("y", eY.setVariable("t", t).setVariable("d", i).evaluate());
                pos.putDouble("z", eZ.setVariable("t", t).setVariable("d", i).evaluate());
                list.add(pos);
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean validateExpressions() {
        return validate(exprX.getText()) && validate(exprY.getText()) && validate(exprZ.getText());
    }

    private boolean validate(String expr) {
        if (expr.trim().isEmpty())
            return true;
        try {
            new ExpressionBuilder(expr).variables("t", "d").build().setVariable("t", 0.5).setVariable("d", 10)
                    .evaluate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void validateSingleExpression(String label, String expr, java.util.function.Supplier<String> getError,
            java.util.function.Consumer<String> setError, java.util.function.Supplier<Long> getTime,
            java.util.function.Consumer<Long> setTime) {
        if (!expr.trim().isEmpty()) {
            try {
                double res = new ExpressionBuilder(expr).variables("t", "d").build().setVariable("t", 0.5)
                        .setVariable("d", 10).evaluate();
                if (Double.isNaN(res) || Double.isInfinite(res))
                    throw new ArithmeticException("Invalid");
                setError.accept(null);
            } catch (Exception e) {
                setError.accept(
                        Text.translatable("gui.extendednoteblock.advanced.error.invalid_syntax", label).getString());
                setTime.accept(System.currentTimeMillis());
            }
        } else {
            setError.accept(null);
        }
    }

    private void drawExpressionError(DrawContext context, MathExpressionWidget widget, String errorMessage,
            long errorDisplayTime) {
        if (errorMessage != null && System.currentTimeMillis() - errorDisplayTime < ERROR_DISPLAY_DURATION) {
            int errorX = widget.getX() + widget.getWidth() + 5;
            int errorY = widget.getY();
            int errorTextWidth = Math.min(textRenderer.getWidth(errorMessage), 200);
            int clampedErrorX = Math.min(errorX, width - errorTextWidth - 5);
            context.fill(clampedErrorX, errorY, clampedErrorX + errorTextWidth, errorY + 12, 0xCCFF0000);
            String displayText = textRenderer.trimToWidth(errorMessage, 200);
            context.drawText(textRenderer, displayText, clampedErrorX + 2, errorY + 2, 0xFFFFFF, false);
        }
    }
}