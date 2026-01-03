package com.atemukesu.extendednoteblock.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VisualCurveWidget extends ClickableWidget {

    public static class DataPoint {
        public float timePercent, value;

        public DataPoint(float t, float v) {
            this.timePercent = t;
            this.value = v;
        }
    }

    private final List<DataPoint> points = new ArrayList<>();
    private final String label;
    private final String tooltipText;
    private float minY, maxY;
    private final int themeColor;
    @SuppressWarnings("unused")
    private final boolean isVolume;

    private DataPoint draggingPoint = null;
    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_INTERVAL = 250;

    public VisualCurveWidget(int x, int y, int width, int height, String label, String tooltipText, float minY,
            float maxY, int color, boolean isVolume) {
        super(x, y, width, height, Text.translatable(label));
        this.label = Text.translatable(label).getString();
        this.tooltipText = tooltipText;
        this.minY = minY;
        this.maxY = maxY;
        this.themeColor = color;
        this.isVolume = isVolume;

        // 初始化两个端点，默认值根据类型设置
        float defaultValue = isVolume ? 1.0f : 0.0f;
        points.add(new DataPoint(0, defaultValue));
        points.add(new DataPoint(1, defaultValue));
    }

    public void setMinMax(float min, float max) {
        this.minY = min;
        this.maxY = max;
    }

    public void setHeight(int i) {
        this.height = i;
    }

    public List<DataPoint> getPoints() {
        return points;
    }

    public void setPoints(List<DataPoint> newPoints) {
        this.points.clear();
        this.points.addAll(newPoints);
        this.points.sort(Comparator.comparingDouble(p -> p.timePercent));
    }

    // 统一吸附逻辑：全部吸附到 0.1
    private float getSnappedValue(float rawValue) {
        return Math.round(rawValue * 10.0f) / 10.0f;
    }

    // 统一格式化逻辑：全部显示 1 位小数（用于刻度显示）
    @SuppressWarnings("unused")
    private String formatValue(float value) {
        return String.format("%.1f", value);
    }

    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. 背景与边框
        context.fill(getX(), getY(), getX() + width, getY() + height, 0xEE050505);
        context.drawBorder(getX(), getY(), width, height, 0xFF444444);

        // 2. 绘制网格与刻度
        drawGridAndLabels(context);

        // 3. 绘制曲线
        context.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);
        renderCurveLines(context);
        renderPoints(context, mouseX, mouseY);
        context.disableScissor();

        // 4. 标题
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, "§l" + label, getX() + 5, getY() + 5,
                0xFFFFFF);
    }

    private void drawGridAndLabels(DrawContext context) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int gridColor = 0x20FFFFFF;
        int textColor = 0x88FFFFFF;

        // X轴刻度 (底部，4个点)
        for (int i = 0; i <= 3; i++) {
            float t = i / 3.0f;
            int px = valToScreenX(t);

            context.fill(px, getY(), px + 1, getY() + height, gridColor);
            String labelX = (int) (t * 100) + "%";
            int tw = tr.getWidth(labelX);
            // 确保文字不超出控件边界
            int tx = MathHelper.clamp(px - tw / 2, getX() + 2, getX() + width - tw - 2);
            context.drawText(tr, labelX, tx, getY() + height - 9, textColor, false);
        }
    }

    private void renderCurveLines(DrawContext context) {
        if (points.size() < 2)
            return;
        points.sort(Comparator.comparingDouble(p -> p.timePercent));

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        float r = (float) (themeColor >> 16 & 0xFF) / 255.0F;
        float g = (float) (themeColor >> 8 & 0xFF) / 255.0F;
        float b = (float) (themeColor & 0xFF) / 255.0F;
        float a = 0.8f;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.lineWidth(2.5F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

        for (DataPoint p : points) {
            bufferBuilder.vertex(matrix, valToScreenX(p.timePercent), valToScreenY(p.value), 0).color(r, g, b, a)
                    .next();
        }

        tessellator.draw();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0F);
    }

    private void renderPoints(DrawContext context, int mouseX, int mouseY) {
        for (DataPoint p : points) {
            int px = valToScreenX(p.timePercent);
            int py = valToScreenY(p.value);

            boolean hovered = Math.abs(mouseX - px) < 4 && Math.abs(mouseY - py) < 4;
            int color = (p == draggingPoint) ? 0xFFFFFF00 : (hovered ? 0xFFFF0000 : 0xFFFFFFFF);

            context.fill(px - 2, py - 2, px + 2, py + 2, color);

            if (hovered) {
                // 提示
                String tip = String.format(tooltipText, p.value);
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.literal(tip), mouseX, mouseY);
            }
        }
    }

    private int valToScreenX(float t) {
        return getX() + (int) (t * (width));
    }

    private float screenToValX(double mouseX) {
        return (float) (mouseX - getX()) / (width);
    }

    private int valToScreenY(float v) {
        float relY = (v - minY) / (maxY - minY);
        return getY() + (int) ((1.0f - relY) * (height));
    }

    private float screenToValY(double mouseY) {
        float relY = 1.0f - (float) (mouseY - getY()) / (height);
        return relY * (maxY - minY) + minY;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY))
            return false;

        long now = System.currentTimeMillis();
        boolean isDoubleClick = (now - lastClickTime < DOUBLE_CLICK_INTERVAL);
        lastClickTime = now;

        if (button == 1) { // 右键删除
            for (DataPoint p : points) {
                if (Math.abs(mouseX - valToScreenX(p.timePercent)) < 5
                        && Math.abs(mouseY - valToScreenY(p.value)) < 5) {
                    if (p.timePercent > 0.0f && p.timePercent < 1.0f && points.size() > 2) {
                        points.remove(p);
                        return true;
                    }
                }
            }
        }

        if (button == 0 && isDoubleClick) { // 双击添加
            float newTime = MathHelper.clamp(screenToValX(mouseX), 0.001f, 0.999f);
            float newVal = getSnappedValue(MathHelper.clamp(screenToValY(mouseY), minY, maxY));
            points.add(new DataPoint(newTime, newVal));
            points.sort(Comparator.comparingDouble(p -> p.timePercent));
            return true;
        }

        if (button == 0) { // 单击抓取
            for (DataPoint p : points) {
                if (Math.abs(mouseX - valToScreenX(p.timePercent)) < 5
                        && Math.abs(mouseY - valToScreenY(p.value)) < 5) {
                    draggingPoint = p;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingPoint != null) {
            // 只有非边缘点可以左右移动
            if (draggingPoint.timePercent > 0.0f && draggingPoint.timePercent < 1.0f) {
                draggingPoint.timePercent = MathHelper.clamp(screenToValX(mouseX), 0.0f, 1.0f);
            }

            // 垂直方向统一吸附到 0.1
            float rawVal = screenToValY(mouseY);
            draggingPoint.value = MathHelper.clamp(getSnappedValue(rawVal), minY, maxY);

            points.sort(Comparator.comparingDouble(p -> p.timePercent));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingPoint = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void appendClickableNarrations(
            net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
    }
}