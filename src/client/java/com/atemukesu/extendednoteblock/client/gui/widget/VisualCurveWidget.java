package com.atemukesu.extendednoteblock.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
    private final float minY, maxY;
    private final int themeColor;
    private final boolean isVolume;

    private DataPoint draggingPoint = null;
    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_INTERVAL = 250;


    /**
     * 构造一个可视化曲线控件
     *
     * @param x 控件的x坐标
     * @param y 控件的y坐标
     * @param width 控件的宽度
     * @param height 控件的高度
     * @param label 控件的标签文本
     * @param tooltipText 鼠标悬停时显示的提示文本
     * @param minY 曲线y轴的最小值
     * @param maxY 曲线y轴的最大值
     * @param color 控件的主题颜色
     * @param isVolume 指示是否为音量曲线的布尔值
     */
    public VisualCurveWidget(int x, int y, int width, int height, String label, String tooltipText, float minY, float maxY, int color, boolean isVolume) {
        super(x, y, width, height, Text.translatable(label));
        this.label = Text.translatable(label).getString();
        this.tooltipText = tooltipText;
        this.minY = minY;
        this.maxY = maxY;
        this.themeColor = color;
        this.isVolume = isVolume;
        // 初始化两个端点 - 对于音量曲线默认为1，对于其他曲线默认为0
        if (isVolume) {
            points.add(new DataPoint(0, 1));
            points.add(new DataPoint(1, 1));
        } else {
            points.add(new DataPoint(0, 0));
            points.add(new DataPoint(1, 0));
        }
    }

    public List<DataPoint> getPoints() {
        return points;
    }

    public void setPoints(List<DataPoint> newPoints) {
        this.points.clear();
        this.points.addAll(newPoints);
        this.points.sort(Comparator.comparingDouble(p -> p.timePercent));
    }

    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. 背景与边框
        context.fill(getX(), getY(), getX() + width, getY() + height, 0xEE050505);
        context.drawBorder(getX(), getY(), width, height, 0xFF444444);

        // 2. 绘制网格线
        drawGrid(context);

        // 3. 绘制曲线 (使用 Scissor 限制在区域内)
        context.enableScissor(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1);
        renderCurveLines(context);
        renderPoints(context, mouseX, mouseY);
        context.disableScissor();

        // 4. 标题
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        context.drawTextWithShadow(tr, "§l" + label, getX() + 5, getY() + 5, 0xFFFFFF);
    }

    private void drawGrid(DrawContext context) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // 绘制 Y=0 的基准线
        int zeroY = valToScreenY(0);
        if (zeroY > getY() && zeroY < getY() + height) {
            context.fill(getX(), zeroY, getX() + width, zeroY + 1, 0x44FFFFFF);
        }

        // 绘制时间轴百分比 (25%, 50%, 75%)
        for (float t = 0.25f; t < 1.0f; t += 0.25f) {
            int px = valToScreenX(t);
            context.fill(px, getY(), px + 1, getY() + height, 0x15FFFFFF);
        }
    }

    private void renderCurveLines(DrawContext context) {
        if (points.size() < 2) return;

        // 确保排序
        points.sort(Comparator.comparingDouble(p -> p.timePercent));

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        // 准备颜色
        float r = (float) (themeColor >> 16 & 0xFF) / 255.0F;
        float g = (float) (themeColor >> 8 & 0xFF) / 255.0F;
        float b = (float) (themeColor & 0xFF) / 255.0F;
        float a = (float) (themeColor >> 24 & 0xFF) / 255.0F;
        if (a == 0) a = 1.0f;

        // 使用 Tessellator 手动绘制线段
        RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        // 设置线宽（注意：现代 OpenGL 线宽受限，但在 GUI 中通常有效）
        RenderSystem.lineWidth(2.5F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();

        // 使用 DEBUG_LINE_STRIP 绘制连续线段
        bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

        for (DataPoint p : points) {
            float x = valToScreenX(p.timePercent);
            float y = valToScreenY(p.value);
            bufferBuilder.vertex(matrix, x, y, 0).color(r, g, b, a).next();
        }

        tessellator.draw();
        RenderSystem.disableBlend();
        // 恢复线宽默认值
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
                // 格式化数值到一位小数
                float formattedValue = Math.round(p.value * 10.0f) / 10.0f;
                String tip = String.format(tooltipText, formattedValue);
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
        if (!isMouseOver(mouseX, mouseY)) return false;

        long now = System.currentTimeMillis();
        boolean isDoubleClick = (now - lastClickTime < DOUBLE_CLICK_INTERVAL);
        lastClickTime = now;

        // 1. 右键逻辑：删除点
        if (button == 1) {
            for (DataPoint p : points) {
                if (Math.abs(mouseX - valToScreenX(p.timePercent)) < 5 && Math.abs(mouseY - valToScreenY(p.value)) < 5) {
                    if (points.size() > 2) { // 保持至少两个点
                        points.remove(p);
                        return true;
                    }
                }
            }
            return false;
        }

        // 2. 左键双击逻辑：添加点
        if (button == 0 && isDoubleClick) {
            float newTimePercent = Math.round(screenToValX(mouseX) * 10.0f) / 10.0f;
            float newValue = Math.round(screenToValY(mouseY) * 10.0f) / 10.0f;
            
            // 音高吸附逻辑
            if ((label.contains("Pitch") || label.contains("pitch") || label.contains("音高") || label.contains("ピッチ")) && !Screen.hasControlDown()) {
                newValue = Math.round(screenToValY(mouseY));
            }
            
            points.add(new DataPoint(
                    MathHelper.clamp(newTimePercent, 0, 1),
                    MathHelper.clamp(newValue, minY, maxY)
            ));
            points.sort(Comparator.comparingDouble(p -> p.timePercent));
            return true;
        }

        // 3. 左键单击逻辑：抓取点
        if (button == 0) {
            for (DataPoint p : points) {
                if (Math.abs(mouseX - valToScreenX(p.timePercent)) < 5 && Math.abs(mouseY - valToScreenY(p.value)) < 5) {
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
            // 吸附到一位小数
            float newTimePercent = Math.round(screenToValX(mouseX) * 10.0f) / 10.0f;
            draggingPoint.timePercent = MathHelper.clamp(newTimePercent, 0, 1);
            
            float val = screenToValY(mouseY);

            // 吸附逻辑：如果是音高(Pitch)且没有按住Ctrl，吸附到半音
            if ((label.contains("Pitch") || label.contains("pitch") || label.contains("音高") || label.contains("ピッチ")) && !Screen.hasControlDown()) {
                val = Math.round(val);
            } else {
                // 对于其他曲线，吸附到一位小数
                val = Math.round(val * 10.0f) / 10.0f;
            }

            draggingPoint.value = MathHelper.clamp(val, minY, maxY);
            // 实时排序，防止点跨越彼此导致渲染混乱
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
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
    }
}