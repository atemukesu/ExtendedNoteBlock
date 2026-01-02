package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;
import com.atemukesu.extendednoteblock.client.gui.widget.MathExpressionWidget;
import com.atemukesu.extendednoteblock.client.gui.widget.VisualCurveWidget;
import com.atemukesu.extendednoteblock.network.ClientModMessages;
import com.atemukesu.extendednoteblock.util.CurvePoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.ArrayList;
import java.util.List;

public class AdvancedSettingsScreen extends Screen {
    private final ExtendedNoteBlockEntity entity;
    private final Screen parent;

    private VisualCurveWidget volCurve;
    private VisualCurveWidget pitchCurve;
    private MathExpressionWidget exprX, exprY, exprZ;

    // 用于存储表达式字符串，以便在NBT中保存
    private String storedExprX = "";
    private String storedExprY = "";
    private String storedExprZ = "";
    private TextFieldWidget rangeInput;

    // 错误信息 - 现在为每个输入框单独存储错误信息
    private String errorMessageX = null;
    private String errorMessageY = null;
    private String errorMessageZ = null;
    private long errorDisplayTimeX = 0;
    private long errorDisplayTimeY = 0;
    private long errorDisplayTimeZ = 0;
    private static final long ERROR_DISPLAY_DURATION = 5000; // 5秒

    public AdvancedSettingsScreen(Screen parent, ExtendedNoteBlockEntity entity) {
        super(Text.translatable("gui.extendednoteblock.advanced.title"));
        this.parent = parent;
        this.entity = entity;
    }

    protected void init() {
        int sidebarWidth = 100;
        int canvasWidth = this.width - sidebarWidth - 30;
        // Reduced height calculation to allow for larger gap between curves
        // Reduced height calculation to allow for larger gap between curves
        int canvasHeight = (this.height - 160) / 2; // Reduced slightly to ensure fit
        int gap = 30; // Space between volume curve and pitch curve

        // 音量曲线: 0.0 -> 2.0
        volCurve = new VisualCurveWidget(20, 35, canvasWidth, canvasHeight,
                Text.translatable("gui.extendednoteblock.advanced.volume_envelope").getString(),
                Text.translatable("gui.extendednoteblock.advanced.volume_tooltip_format").getString(),
                0f, 2f, 0xFF55FF55, true);

        // 弯音曲线: Configurable Range
        int range = com.atemukesu.extendednoteblock.config.ConfigManager.getConfig().pitchBendRange;
        int pitchCurveY = 35 + canvasHeight + gap;
        pitchCurve = new VisualCurveWidget(20, pitchCurveY, canvasWidth, canvasHeight,
                Text.translatable("gui.extendednoteblock.advanced.pitch_bend_semitones").getString(),
                Text.translatable("gui.extendednoteblock.advanced.pitch_tooltip_format").getString(),
                -range, range, 0xFFFFFF55, false);

        // Range Configuration Input
        // Positioned in the gap between the two curves, right-aligned
        int rangeInputX = 20 + canvasWidth - 50;
        int rangeControlY = pitchCurveY - 22;

        // Add +/- Buttons for Range
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> adjustRange(-1))
                .dimensions(rangeInputX - 22, rangeControlY, 20, 16).build());

        rangeInput = new TextFieldWidget(textRenderer, rangeInputX, rangeControlY, 50, 16,
                Text.translatable("gui.extendednoteblock.advanced.range"));
        rangeInput.setText(String.valueOf(range));
        rangeInput.setChangedListener(text -> {
            try {
                int newRange = Integer.parseInt(text);
                if (newRange > 0 && newRange <= 48) { // Hard limit 48
                    com.atemukesu.extendednoteblock.config.ConfigManager.getConfig().pitchBendRange = newRange;
                    pitchCurve.setMinMax(-newRange, newRange);
                }
            } catch (NumberFormatException ignored) {
            }
        });
        addDrawableChild(rangeInput);

        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> adjustRange(1))
                .dimensions(rangeInputX + 52, rangeControlY, 20, 16).build());

        // Label logic moved to render()

        addDrawableChild(volCurve);
        addDrawableChild(pitchCurve);

        // 将输入框放在右侧或下方，排版更像专业DAW
        // Move editY down to allow space for labels above
        int editY = pitchCurveY + canvasHeight + 20;
        exprX = new MathExpressionWidget(textRenderer, 20, editY, canvasWidth / 3 - 5, 20,
                Text.translatable("gui.extendednoteblock.advanced.x_axis"));
        exprY = new MathExpressionWidget(textRenderer, 20 + canvasWidth / 3, editY, canvasWidth / 3 - 5, 20,
                Text.translatable("gui.extendednoteblock.advanced.y_axis"));
        exprZ = new MathExpressionWidget(textRenderer, 20 + 2 * canvasWidth / 3, editY, canvasWidth / 3 - 5, 20,
                Text.translatable("gui.extendednoteblock.advanced.z_axis"));

        // 为每个表达式输入框设置文本变化监听器
        exprX.setTextChangeCallback(text -> validateSingleExpression("X", text, () -> errorMessageX,
                msg -> errorMessageX = msg, () -> errorDisplayTimeX, time -> errorDisplayTimeX = time));
        exprY.setTextChangeCallback(text -> validateSingleExpression("Y", text, () -> errorMessageY,
                msg -> errorMessageY = msg, () -> errorDisplayTimeY, time -> errorDisplayTimeY = time));
        exprZ.setTextChangeCallback(text -> validateSingleExpression("Z", text, () -> errorMessageZ,
                msg -> errorMessageZ = msg, () -> errorDisplayTimeZ, time -> errorDisplayTimeZ = time));

        addDrawableChild(exprX);
        addDrawableChild(exprY);
        addDrawableChild(exprZ);
        loadExistingData();
    }

    private void adjustRange(int delta) {
        try {
            int current = Integer.parseInt(rangeInput.getText());
            int newVal = Math.max(1, Math.min(48, current + delta));
            rangeInput.setText(String.valueOf(newVal));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. 绘制深色工业风背景
        context.fill(0, 0, width, height, 0xFF111111);
        super.render(context, mouseX, mouseY, delta);

        // 2. 绘制标题栏装饰
        context.fill(0, 0, width, 25, 0xFF222222);

        // Draw Range Label
        int sidebarWidth = 100;
        int canvasWidth = this.width - sidebarWidth - 30;
        int canvasHeight = (this.height - 160) / 2; // Match init calculation
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.advanced.range_label"),
                20 + canvasWidth - 100,
                45 + canvasHeight - 16 + 5, 0xAAAAAA); // Adjusted Y

        context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), width / 2, 8,
                0xFFAA00);

        // 3. 侧边/底部操作提示
        int tipX = width - 105;
        int tipY = 40;
        context.drawText(textRenderer,
                "§6[" + Text.translatable("gui.extendednoteblock.advanced.controls").getString() + "]", tipX, tipY,
                0xFFFFFF, true);
        String[] tips = {
                "§7" + Text.translatable("gui.extendednoteblock.advanced.right_click_del").getString(),
                "§7" + Text.translatable("gui.extendednoteblock.advanced.double_click_add").getString(),
                "§7" + Text.translatable("gui.extendednoteblock.advanced.drag_move").getString()
        };
        for (int i = 0; i < tips.length; i++) {
            context.drawText(textRenderer, tips[i], tipX, tipY + 15 + (i * 12), 0xCCCCCC, false);
        }

        // 函数和自变量说明 - 放在操作说明下方
        drawFunctionHelp(context, tipX, tipY + 20 + (tips.length * 12));

        // 4. 表达式标签 - MOVED ABOVE INPUTS
        // Removed the generic valid math label at bottom

        // Draw labels above each input box
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.advanced.x_axis"),
                exprX.getX(), exprX.getY() - 10, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.advanced.y_axis"),
                exprY.getX(), exprY.getY() - 10, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.advanced.z_axis"),
                exprZ.getX(), exprZ.getY() - 10, 0xAAAAAA);

        // 绘制每个输入框右侧的错误信息
        drawExpressionError(context, exprX, errorMessageX, errorDisplayTimeX);
        drawExpressionError(context, exprY, errorMessageY, errorDisplayTimeY);
        drawExpressionError(context, exprZ, errorMessageZ, errorDisplayTimeZ);
    }

    // 绘制单个表达式输入框的错误信息
    private void drawExpressionError(DrawContext context, MathExpressionWidget widget, String errorMessage,
            long errorDisplayTime) {
        if (errorMessage != null && System.currentTimeMillis() - errorDisplayTime < ERROR_DISPLAY_DURATION) {
            int errorX = widget.getX() + widget.getWidth() + 5; // 在输入框右侧显示错误
            int errorY = widget.getY();
            // 确保错误信息不会超出屏幕边界
            int errorTextWidth = Math.min(textRenderer.getWidth(errorMessage), 200);
            int clampedErrorX = Math.min(errorX, width - errorTextWidth - 5);
            // 绘制错误背景
            context.fill(clampedErrorX, errorY, clampedErrorX + errorTextWidth, errorY + 12, 0xCCFF0000);
            // 绘制错误文本，限制长度以避免超出屏幕
            String displayText = textRenderer.trimToWidth(errorMessage, 200);
            context.drawText(textRenderer, displayText, clampedErrorX + 2, errorY + 2, 0xFFFFFF, false);
        }
    }

    private void drawFunctionHelp(DrawContext context, int helpX, int helpY) {
        // 标题
        context.drawText(textRenderer,
                "§6[" + Text.translatable("gui.extendednoteblock.advanced.functions_title").getString() + "]", helpX,
                helpY, 0xFFFFFF, false);

        // 变量说明
        String[] variables = {
                "§7" + Text.translatable("gui.extendednoteblock.advanced.var_t").getString(),
                "§7" + Text.translatable("gui.extendednoteblock.advanced.var_d").getString()
        };

        for (int i = 0; i < variables.length; i++) {
            context.drawText(textRenderer, variables[i], helpX, helpY + 12 + (i * 10), 0xCCCCCC, false);
        }

        // 函数说明
        String[] functions = {
                "§7" + Text.translatable("gui.extendednoteblock.advanced.func_sin").getString(),
                "§7" + Text.translatable("gui.extendednoteblock.advanced.func_cos").getString(),
                "§7" + Text.translatable("gui.extendednoteblock.advanced.func_tan").getString(),
                "§7" + Text.translatable("gui.extendednoteblock.advanced.func_sqrt").getString(),
                "§7" + Text.translatable("gui.extendednoteblock.advanced.func_abs").getString(),
                "§7" + Text.translatable("gui.extendednoteblock.advanced.func_exp").getString(),
                "§7" + Text.translatable("gui.extendednoteblock.advanced.func_log").getString(),
                "§7" + Text.translatable("gui.extendednoteblock.advanced.func_pi").getString()
        };

        int funcY = helpY + 12 + (variables.length * 10);
        for (int i = 0; i < functions.length; i++) {
            context.drawText(textRenderer, functions[i], helpX, funcY + (i * 10), 0xCCCCCC, false);
        }
    }

    private void loadExistingData() {
        // 直接加载关键点，不再通过采样还原
        if (!entity.getVolumePoints().isEmpty()) {
            List<VisualCurveWidget.DataPoint> points = new ArrayList<>();
            for (CurvePoint p : entity.getVolumePoints()) {
                points.add(new VisualCurveWidget.DataPoint(p.time, p.value));
            }
            volCurve.setPoints(points);
        }

        if (!entity.getPitchBendPoints().isEmpty()) {
            List<VisualCurveWidget.DataPoint> points = new ArrayList<>();
            for (CurvePoint p : entity.getPitchBendPoints()) {
                points.add(new VisualCurveWidget.DataPoint(p.time, p.value));
            }
            pitchCurve.setPoints(points);
        }

        // 加载存储的表达式（从NBT）
        if (!entity.getStoredExpressionX().isEmpty()) {
            exprX.setText(entity.getStoredExpressionX());
            storedExprX = entity.getStoredExpressionX();
            // 验证已加载的表达式
            validateSingleExpression("X", entity.getStoredExpressionX(), () -> errorMessageX,
                    msg -> errorMessageX = msg, () -> errorDisplayTimeX, time -> errorDisplayTimeX = time);
        }
        if (!entity.getStoredExpressionY().isEmpty()) {
            exprY.setText(entity.getStoredExpressionY());
            storedExprY = entity.getStoredExpressionY();
            // 验证已加载的表达式
            validateSingleExpression("Y", entity.getStoredExpressionY(), () -> errorMessageY,
                    msg -> errorMessageY = msg, () -> errorDisplayTimeY, time -> errorDisplayTimeY = time);
        }
        if (!entity.getStoredExpressionZ().isEmpty()) {
            exprZ.setText(entity.getStoredExpressionZ());
            storedExprZ = entity.getStoredExpressionZ();
            // 验证已加载的表达式
            validateSingleExpression("Z", entity.getStoredExpressionZ(), () -> errorMessageZ,
                    msg -> errorMessageZ = msg, () -> errorDisplayTimeZ, time -> errorDisplayTimeZ = time);
        }
    }

    private void save() {
        if (!validateExpressions())
            return;
        int sustain = entity.getSustain(); // 用于 SoundPath 生成，但不再用于曲线采样

        // [修复 1 & 2] 不再采样！直接提取控件上的点
        List<CurvePoint> volumePoints = new ArrayList<>();
        for (VisualCurveWidget.DataPoint dp : volCurve.getPoints()) {
            volumePoints.add(new CurvePoint(dp.timePercent, dp.value));
        }

        List<CurvePoint> pitchBendPoints = new ArrayList<>();
        for (VisualCurveWidget.DataPoint dp : pitchCurve.getPoints()) {
            pitchBendPoints.add(new CurvePoint(dp.timePercent, dp.value));
        }

        // SoundPath 目前还是基于表达式生成的逐帧数据，暂时保持原样，
        // 或者如果以后要做可视化路径编辑，也应该改为关键点。
        List<Vec3d> soundPath = generateSoundPathFromExpressions(sustain);

        // 发送数据包
        ClientModMessages.sendAdvancedSettingsToServer(
                entity.getPos(),
                volumePoints, // 传点列表
                pitchBendPoints, // 传点列表
                soundPath,
                exprX.getText(), exprY.getText(), exprZ.getText());
    }

    private boolean validateExpressions() {
        // 使用 exp4j 验证表达式
        String[] expressions = { exprX.getText(), exprY.getText(), exprZ.getText() };
        String[] labels = { "X", "Y", "Z" };

        for (int i = 0; i < expressions.length; i++) {
            String expr = expressions[i];
            if (!expr.trim().isEmpty()) {
                // 尝试解析表达式以验证语法
                double testResult = evaluateExpression(expr, 0.5);
                if (Double.isNaN(testResult) || Double.isInfinite(testResult)) {
                    showErrorMessage(
                            Text.translatable("gui.extendednoteblock.advanced.error.invalid_syntax", labels[i]));
                    return false;
                }
            }
        }

        return true;
    }

    // 验证单个表达式的语法
    private void validateSingleExpression(String label, String expr,
            java.util.function.Supplier<String> getErrorMsg,
            java.util.function.Consumer<String> setErrorMsg,
            java.util.function.Supplier<Long> getErrorTime,
            java.util.function.Consumer<Long> setErrorTime) {
        if (!expr.trim().isEmpty()) {
            // 尝试解析表达式以验证语法
            double testResult = evaluateExpression(expr, 0.5);
            if (Double.isNaN(testResult) || Double.isInfinite(testResult)) {
                setErrorMsg.accept(
                        Text.translatable("gui.extendednoteblock.advanced.error.invalid_syntax", label).getString());
                setErrorTime.accept(System.currentTimeMillis());
            } else {
                setErrorMsg.accept(null); // 清除错误信息
            }
        } else {
            setErrorMsg.accept(null); // 清除错误信息
        }
    }

    private List<Vec3d> generateSoundPathFromExpressions(int sustain) {
        List<Vec3d> path = new ArrayList<>();

        String exprXStr = exprX.getText();
        String exprYStr = exprY.getText();
        String exprZStr = exprZ.getText();

        // 如果没有表达式，则返回空列表
        if (exprXStr.isEmpty() && exprYStr.isEmpty() && exprZStr.isEmpty()) {
            return path;
        }

        // 根据sustain值采样，每Tick对应一个点
        for (int i = 0; i < sustain; i++) {
            float t = (float) i / (float) sustain; // t从0.0到接近1.0
            double d = i; // d为当前tick值

            double x = evaluateExpression(exprXStr.isEmpty() ? "0" : exprXStr, t, d);
            double y = evaluateExpression(exprYStr.isEmpty() ? "0" : exprYStr, t, d);
            double z = evaluateExpression(exprZStr.isEmpty() ? "0" : exprZStr, t, d);

            // 检查是否有NaN或无穷大值
            if (Double.isNaN(x) || Double.isInfinite(x) ||
                    Double.isNaN(y) || Double.isInfinite(y) ||
                    Double.isNaN(z) || Double.isInfinite(z)) {
                showErrorMessage(Text.translatable("gui.extendednoteblock.advanced.error.invalid_result"));
                return new ArrayList<>(); // 返回空列表
            }

            path.add(new Vec3d(x, y, z));
        }

        return path;
    }

    // 数学表达式解析器 - 使用 exp4j 库
    private double evaluateExpression(String expr, double t, double d) {
        if (expr == null || expr.trim().isEmpty()) {
            return 0.0; // 空表达式返回0
        }

        try {
            // 替换 pi 常数为数值（exp4j 不直接支持 pi，需要替换为数值）
            String processedExpr = expr.trim().replaceAll("(?i)\\bpi\\b", String.valueOf(Math.PI));

            // 使用 exp4j 构建和计算表达式
            // exp4j 内置支持 sin, cos, tan, abs, sqrt, exp, log, ln 等函数
            Expression expression = new ExpressionBuilder(processedExpr)
                    .variable("t") // 定义变量 t (0-1 百分比)
                    .variable("d") // 定义变量 d (当前tick)
                    .build()
                    .setVariable("t", t)
                    .setVariable("d", d);

            double result = expression.evaluate();

            // 检查结果是否有效
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return Double.NaN;
            }

            return result;
        } catch (IllegalArgumentException e) {
            // 表达式语法错误
            return Double.NaN;
        } catch (ArithmeticException e) {
            // 数学运算错误（如除以零）
            return Double.NaN;
        } catch (Exception e) {
            // 其他异常
            return Double.NaN;
        }
    }

    // 重载方法，保持向后兼容
    private double evaluateExpression(String expr, double t) {
        return evaluateExpression(expr, t, 0);
    }

    private void showErrorMessage(Text message) {
        this.errorMessageX = message.getString();
        this.errorDisplayTimeX = System.currentTimeMillis();

        // 同时在聊天框中显示错误消息
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("§c[ExtendedNoteBlock] " + message.getString()), false);
        }
    }

    @Override
    public void close() {
        // 自动保存设置
        save();
        // 返回上一级
        MinecraftClient.getInstance().setScreen(parent);
    }

    // 不暂停游戏
    @Override
    public boolean shouldPause() {
        return false;
    }
}