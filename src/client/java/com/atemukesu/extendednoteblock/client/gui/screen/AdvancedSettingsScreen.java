package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;
import com.atemukesu.extendednoteblock.client.gui.widget.MathExpressionWidget;
import com.atemukesu.extendednoteblock.client.gui.widget.VisualCurveWidget;
import com.atemukesu.extendednoteblock.network.ClientModMessages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
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

    // 错误信息
    private String errorMessage = null;
    private long errorDisplayTime = 0;
    private static final long ERROR_DISPLAY_DURATION = 5000; // 5秒

    public AdvancedSettingsScreen(Screen parent, ExtendedNoteBlockEntity entity) {
        super(Text.translatable("gui.extendednoteblock.advanced.title"));
        this.parent = parent;
        this.entity = entity;
    }

    protected void init() {
        int sidebarWidth = 100;
        int canvasWidth = this.width - sidebarWidth - 30;
        int canvasHeight = (this.height - 110) / 2;

        // 音量曲线: 0.0 -> 2.0
        volCurve = new VisualCurveWidget(20, 35, canvasWidth, canvasHeight,
                "gui.extendednoteblock.advanced.volume_envelope",
                Text.translatable("gui.extendednoteblock.advanced.volume_tooltip_format").getString(),
                0f, 2f, 0xFF55FF55, true);

        // 弯音曲线: -24 -> +24
        pitchCurve = new VisualCurveWidget(20, 45 + canvasHeight, canvasWidth, canvasHeight,
                Text.translatable("gui.extendednoteblock.advanced.pitch_bend_semitones").getString(),
                Text.translatable("gui.extendednoteblock.advanced.pitch_tooltip_format").getString(),
                -24f, 24f, 0xFFFFFF55, false);

        addDrawableChild(volCurve);
        addDrawableChild(pitchCurve);

        // 将输入框放在右侧或下方，排版更像专业DAW
        int editY = 65 + canvasHeight * 2;
        exprX = new MathExpressionWidget(textRenderer, 20, editY, canvasWidth / 3 - 5, 20, Text.literal("X(t)"));
        exprY = new MathExpressionWidget(textRenderer, 20 + canvasWidth / 3, editY, canvasWidth / 3 - 5, 20, Text.literal("Y(t)"));
        exprZ = new MathExpressionWidget(textRenderer, 20 + 2 * canvasWidth / 3, editY, canvasWidth / 3 - 5, 20, Text.literal("Z(t)"));

        addDrawableChild(exprX);
        addDrawableChild(exprY);
        addDrawableChild(exprZ);
        loadExistingData();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. 绘制深色工业风背景
        context.fill(0, 0, width, height, 0xFF111111);
        super.render(context, mouseX, mouseY, delta);

        // 2. 绘制标题栏装饰
        context.fill(0, 0, width, 25, 0xFF222222);
        context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), width / 2, 8, 0xFFAA00);

        // 3. 侧边/底部操作提示
        int tipX = width - 105;
        int tipY = 40;
        context.drawText(textRenderer, "§6[" + Text.translatable("gui.extendednoteblock.advanced.controls").getString() + "]", tipX, tipY, 0xFFFFFF, true);
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

        // 4. 表达式标签
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.extendednoteblock.advanced.spatial_math", "t").getString(), 20, height - 60, 0xAAAAAA);

        // 错误信息气泡
        if (errorMessage != null && System.currentTimeMillis() - errorDisplayTime < 5000) {
            context.fill(width / 2 - 100, 5, width / 2 + 100, 20, 0xCCFF0000);
            context.drawCenteredTextWithShadow(textRenderer, errorMessage, width / 2, 8, 0xFFFFFF);
        }
    }

    private void drawFunctionHelp(DrawContext context, int helpX, int helpY) {
        // 标题
        context.drawText(textRenderer, "§6[" + Text.translatable("gui.extendednoteblock.advanced.functions_title").getString() + "]", helpX, helpY, 0xFFFFFF, false);

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

    // 保持旧的调用方法以向后兼容
    private void drawFunctionHelp(DrawContext context) {
        int helpX = 20;
        int helpY = this.height - 45;
        drawFunctionHelp(context, helpX, helpY);
    }

    private void loadExistingData() {
        // 加载音量曲线数据
        if (!entity.getVolumeCurve().isEmpty()) {
            List<VisualCurveWidget.DataPoint> points = new ArrayList<>();
            List<Float> curve = entity.getVolumeCurve();
            for (int i = 0; i < curve.size(); i++) {
                float timePercent = (float) i / (curve.size() - 1);
                points.add(new VisualCurveWidget.DataPoint(timePercent, curve.get(i)));
            }
            volCurve.setPoints(points);
        }

        // 加载弯音曲线数据
        if (!entity.getPitchBendCurve().isEmpty()) {
            List<VisualCurveWidget.DataPoint> points = new ArrayList<>();
            List<Float> curve = entity.getPitchBendCurve();
            for (int i = 0; i < curve.size(); i++) {
                float timePercent = (float) i / (curve.size() - 1);
                points.add(new VisualCurveWidget.DataPoint(timePercent, curve.get(i)));
            }
            pitchCurve.setPoints(points);
        }

        // 加载存储的表达式（从NBT）
        if (!entity.getStoredExpressionX().isEmpty()) {
            exprX.setText(entity.getStoredExpressionX());
            storedExprX = entity.getStoredExpressionX();
        }
        if (!entity.getStoredExpressionY().isEmpty()) {
            exprY.setText(entity.getStoredExpressionY());
            storedExprY = entity.getStoredExpressionY();
        }
        if (!entity.getStoredExpressionZ().isEmpty()) {
            exprZ.setText(entity.getStoredExpressionZ());
            storedExprZ = entity.getStoredExpressionZ();
        }
    }

    /**
     * 核心插值辅助方法：
     * 根据用户在 Widget 上画的点，计算出百分比 t (0.0~1.0) 对应的具体数值。
     */
    private float interpolateValueFromWidget(VisualCurveWidget widget, float t) {
        // 获取点并按时间排序，防止计算混乱
        List<VisualCurveWidget.DataPoint> pts = new ArrayList<>(widget.getPoints());
        pts.sort(java.util.Comparator.comparingDouble(p -> p.timePercent));

        if (pts.isEmpty()) return 0;

        // 如果查询的时间点在第一个点之前，返回第一个点的值
        if (t <= pts.get(0).timePercent) return pts.get(0).value;

        // 如果查询的时间点在最后一个点之后，返回最后一个点的值
        if (t >= pts.get(pts.size() - 1).timePercent) return pts.get(pts.size() - 1).value;

        // 寻找 t 落在哪个线段之间
        for (int i = 0; i < pts.size() - 1; i++) {
            VisualCurveWidget.DataPoint p1 = pts.get(i);
            VisualCurveWidget.DataPoint p2 = pts.get(i + 1);

            if (t >= p1.timePercent && t <= p2.timePercent) {
                // 计算线性插值百分比 (0.0 ~ 1.0)
                float denominator = p2.timePercent - p1.timePercent;
                // 防止分母为 0 (两个点重合的情况)
                float relT = (denominator == 0) ? 0 : (t - p1.timePercent) / denominator;

                // 线性插值公式：y = y1 + (y2 - y1) * t
                return p1.value + relT * (p2.value - p1.value);
            }
        }

        return pts.get(pts.size() - 1).value;
    }

    private void save() {
        // 验证表达式
        if (!validateExpressions()) {
            return;
        }

        // 采样点数直接等于 Sustain Ticks
        int sustain = entity.getSustain();
        if (sustain <= 0) sustain = 1;

        int sampleCount = sustain; // 2 Ticks 就只采样 2 个点

        List<Float> volumeCurve = new ArrayList<>();
        for (int i = 0; i < sampleCount; i++) {
            // 均匀分布：i=0 对应 t=0.0, i=sustain-1 对应 t=1.0
            float t = (sustain > 1) ? (float) i / (float) (sustain - 1) : 0.0f;
            volumeCurve.add(interpolateValueFromWidget(volCurve, t));
        }

        List<Float> pitchBendCurve = new ArrayList<>();
        for (int i = 0; i < sampleCount; i++) {
            // 均匀分布：i=0 对应 t=0.0, i=sustain-1 对应 t=1.0
            float t = (sustain > 1) ? (float) i / (float) (sustain - 1) : 0.0f;
            pitchBendCurve.add(interpolateValueFromWidget(pitchCurve, t));
        }

        // 计算声源移动路径 - 使用数学表达式生成Vec3d列表，传入sustain参数
        List<Vec3d> soundPath = generateSoundPathFromExpressions(sustain);

        // 保存表达式到NBT（仅用于存储表达式）
        storedExprX = exprX.getText();
        storedExprY = exprY.getText();
        storedExprZ = exprZ.getText();

        // 发送到服务器更新实体数据
        ClientModMessages.sendAdvancedSettingsToServer(entity.getPos(), volumeCurve, pitchBendCurve, soundPath,
                storedExprX, storedExprY, storedExprZ);
    }

    private boolean validateExpressions() {
        // 使用 exp4j 验证表达式
        String[] expressions = {exprX.getText(), exprY.getText(), exprZ.getText()};
        String[] labels = {"X", "Y", "Z"};

        for (int i = 0; i < expressions.length; i++) {
            String expr = expressions[i];
            if (!expr.trim().isEmpty()) {
                // 尝试解析表达式以验证语法
                double testResult = evaluateExpression(expr, 0.5);
                if (Double.isNaN(testResult) || Double.isInfinite(testResult)) {
                    showErrorMessage(Text.translatable("gui.extendednoteblock.advanced.error.invalid_syntax", labels[i]));
                    return false;
                }
            }
        }

        return true;
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
                    .variable("t")  // 定义变量 t (0-1百分比)
                    .variable("d")  // 定义变量 d (当前tick)
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
        this.errorMessage = message.getString();
        this.errorDisplayTime = System.currentTimeMillis();

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
}