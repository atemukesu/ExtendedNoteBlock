package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.util.SoundfontRenderer;
import com.google.common.collect.Lists;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 一个 GUI 屏幕，用于显示从 SoundFont 文件渲染声音的进度。
 * <p>
 * 该屏幕提供了一个用户友好的界面来跟踪后台渲染任务的状态。它包括：
 * <ul>
 * <li>一个标题和当前状态文本。</li>
 * <li>一个可视化的进度条，显示已完成任务的百分比。</li>
 * <li>一个预计剩余时间 (ETA) 的估算。</li>
 * <li>一个可滚动的日志框，显示来自渲染过程的详细输出。</li>
 * </ul>
 * 渲染过程本身在
 * 一个单独的线程中执行，以防止游戏客户端冻结。该线程通过回调与此屏幕通信，
 * 以安全地更新 GUI 元素。
 */
public class RenderingScreen extends Screen {
    private final Screen parentScreen;
    private final String fluidSynthPath, ffmpegPath, sf2Path;
    private final Path outputPackDir;
    private int progress = 0;
    private int maxProgress = 1;
    private Text status = Text.translatable("gui.extendednoteblock.rendering.status.initializing");
    private final List<Text> logs = Collections.synchronizedList(Lists.newArrayList());
    private boolean finished = false;
    private boolean success = false;
    private long startTime;
    private Text etaText = Text.empty();
    private final List<OrderedText> wrappedLogLines = new ArrayList<>();
    private int lastLogSize = 0;
    private int logScrollOffset = 0;
    private boolean renderingStarted = false;

    /**
     * 构造渲染进度屏幕。
     *
     * @param parent         完成或取消后返回的父屏幕。
     * @param fluidSynthPath FluidSynth 可执行文件的路径。
     * @param ffmpegPath     FFmpeg 可执行文件的路径。
     * @param sf2Path        要渲染的 SoundFont (.sf2) 文件的路径。
     * @param outputPackDir  输出资源包目录的路径。
     */
    public RenderingScreen(Screen parent, String fluidSynthPath, String ffmpegPath, String sf2Path,
            Path outputPackDir) {
        super(Text.translatable("gui.extendednoteblock.rendering.title"));
        this.parentScreen = parent;
        this.fluidSynthPath = fluidSynthPath;
        this.ffmpegPath = ffmpegPath;
        this.sf2Path = sf2Path;
        this.outputPackDir = outputPackDir;
    }

    /**
     * 初始化屏幕。如果渲染尚未开始，则启动后台渲染线程。
     * <p>
     * {@code renderingStarted} 标志确保即使在屏幕因调整大小等原因被重新初始化时，
     * 渲染线程也只启动一次。如果渲染已完成，此方法还会重新添加完成按钮。
     */
    @Override
    protected void init() {
        super.init();
        if (!this.renderingStarted) {
            this.renderingStarted = true;
            this.startTime = System.currentTimeMillis();
            runRenderer();
        }
        if (this.finished) {
            addFinishButton();
        }
    }

    /**
     * 设置并启动用于声音渲染过程的后台线程。
     * <p>
     * 此方法创建一个新的 {@link SoundfontRenderer} 实例，并为其提供回调函数
     * 以便将进度、状态和日志消息从工作线程传递回 GUI 线程。
     * 为了线程安全，所有对 GUI 状态的更新都通过 {@code client.execute()} 在主客户端线程上调度。
     */
    private void runRenderer() {
        new Thread(() -> {
            SoundfontRenderer renderer = new SoundfontRenderer(
                    this.fluidSynthPath, this.ffmpegPath, this.sf2Path, this.outputPackDir,
                    (newStatus) -> client.execute(() -> this.status = newStatus),
                    (current, max) -> client.execute(() -> {
                        this.progress = current;
                        this.maxProgress = max;
                    }),
                    (logMessage) -> client.execute(() -> {
                        synchronized (this.logs) {
                            this.logs.add(logMessage);
                        }
                    }));
            this.success = renderer.run();
            if (this.client != null) {
                this.client.execute(this::onRenderFinished);
            }
        }, "ENB-SoundfontRenderer").start();
    }

    /**
     * 当渲染过程完成时在客户端线程上执行的回调方法。
     * <p>
     * 此方法将屏幕状态更新为“完成”或“错误”，设置 {@code finished} 标志，
     * 并调用 {@link #addFinishButton()} 来显示相应的退出按钮。
     */
    private void onRenderFinished() {
        this.finished = true;
        if (this.success) {
            this.status = Text.translatable("gui.extendednoteblock.rendering.status.complete")
                    .formatted(Formatting.GREEN);
        } else {
            this.status = Text.translatable("gui.extendednoteblock.rendering.status.error").formatted(Formatting.RED);
        }
        addFinishButton();
    }

    /**
     * 在渲染完成后，向屏幕添加最终的按钮。
     * <p>
     * 如果渲染成功，则添加一个“完成”按钮。如果失败，则添加一个“返回”按钮。
     * 两个按钮都会将用户导航回父屏幕。
     */
    private void addFinishButton() {
        if (this.success) {
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> {
                if (this.client != null)
                    this.client.setScreen(this.parentScreen);
            }).dimensions(this.width / 2 - 100, this.height - 30, 200, 20).build());
        } else {
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> {
                if (this.client != null)
                    this.client.setScreen(this.parentScreen);
            }).dimensions(this.width / 2 - 100, this.height - 30, 200, 20).build());
        }
    }

    /**
     * 渲染屏幕上的所有视觉组件。
     * <p>
     * 此方法每帧被调用，负责绘制背景、标题、状态文本、进度条、ETA 以及可滚动的日志框。
     * 为了提高性能，仅当有新的日志消息被添加时，日志文本才会重新进行换行处理。
     * 日志框使用剪裁（scissor）来确保文本不会绘制到其边界之外。
     *
     * @param context 绘制上下文。
     * @param mouseX  鼠标的 X 坐标。
     * @param mouseY  鼠标的 Y 坐标。
     * @param delta   自上一帧以来的时间。
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        int contentY = 15;
        // 渲染标题和状态
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, contentY, 0xFFFFFF);
        contentY += 15;
        context.drawCenteredTextWithShadow(this.textRenderer, this.status, this.width / 2, contentY, 0xFFFFFF);
        contentY += 15;

        // 计算并更新 ETA
        if (!finished && progress > 2 && startTime > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            double timePerTask = (double) elapsed / progress;
            long remainingMillis = (long) (timePerTask * (maxProgress - progress));
            etaText = Text.translatable("gui.extendednoteblock.rendering.eta", formatDuration(remainingMillis));
        } else if (finished) {
            etaText = Text.empty();
        }

        // 渲染进度条
        int barWidth = 300;
        int barX = (this.width - barWidth) / 2;
        context.fill(barX - 1, contentY - 1, barX + barWidth + 1, contentY + 11, 0xFF000000);
        context.fill(barX, contentY, barX + barWidth, contentY + 10, 0xFF555555);
        int progressWidth = (maxProgress > 0) ? (int) (((double) this.progress / this.maxProgress) * barWidth) : 0;
        context.fill(barX, contentY, barX + progressWidth, contentY + 10, 0xFF55FF55);
        String progressText = String.format("(%d/%d) %.1f%%", progress, maxProgress,
                (maxProgress > 0 ? (double) progress / maxProgress * 100.0 : 0.0));
        context.drawCenteredTextWithShadow(textRenderer, progressText, this.width / 2, contentY + 1, 0xFFFFFF);


        int etaTextWidth = this.textRenderer.getWidth(etaText);
        // 新的X坐标 = 进度条右边缘 - ETA文本宽度
        int etaX = barX + barWidth - etaTextWidth;
        // 新的Y坐标 = 进度条下方 (进度条高度10 + 2像素间距)
        int etaY = contentY + 12;
        context.drawTextWithShadow(this.textRenderer, etaText, etaX, etaY, 0xFFFFFF);

        // 为了给ETA文本留出空间，我们稍微增加Y坐标的增量
        contentY += 30; // 原来是 20，现在增加到 30 以容纳下方的ETA文本

        // 渲染日志框
        int logBoxTop = contentY;
        int logBoxBottom = this.height - 40;
        int logBoxWidth = this.width - 100;
        int logBoxHeight = logBoxBottom - logBoxTop;
        context.fill(49, logBoxTop - 1, this.width - 49, logBoxBottom + 1, 0xFF000000);
        context.fill(50, logBoxTop, this.width - 50, logBoxBottom, 0x80000000);

        // 如果日志有更新，则重新进行文本换行处理
        if (logs.size() != lastLogSize) {
            lastLogSize = logs.size();
            wrappedLogLines.clear();
            synchronized (this.logs) {
                for (Text log : this.logs) {
                    wrappedLogLines.addAll(textRenderer.wrapLines(log, logBoxWidth - 20));
                }
            }
            // 自动滚动到最新的日志
            logScrollOffset = Math.max(0, wrappedLogLines.size() - (logBoxHeight / textRenderer.fontHeight));
        }

        // 使用剪裁区域渲染日志内容
        context.getMatrices().push();
        context.enableScissor(50, logBoxTop, this.width - 50, logBoxBottom);
        for (int i = 0; i < wrappedLogLines.size(); i++) {
            int lineY = logBoxTop + 5 + (i - logScrollOffset) * textRenderer.fontHeight;
            if (lineY >= logBoxTop && lineY < logBoxBottom) {
                context.drawTextWithShadow(this.textRenderer, wrappedLogLines.get(i), 60, lineY, 0xFFFFFF);
            }
        }
        context.disableScissor();
        context.getMatrices().pop();

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * 处理鼠标滚轮事件以滚动日志显示。
     *
     * @param mouseX 鼠标的 X 坐标。
     * @param mouseY 鼠标的 Y 坐标。
     * @param amount 滚动的量。
     * @return 总是返回 {@code true}，表示事件已被处理。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // 更新 logBoxTop 的计算以匹配 render 方法中的新布局
        int logBoxTop = 15 + 15 + 15 + 30; // 标题(15) + 状态(15) + 进度条区域(30)
        int logBoxBottom = this.height - 40;
        int logBoxHeight = logBoxBottom - logBoxTop;
        int maxScroll = Math.max(0, wrappedLogLines.size() - (logBoxHeight / textRenderer.fontHeight));
        this.logScrollOffset = (int) Math.max(0, Math.min(maxScroll, this.logScrollOffset - amount));
        return true;
    }

    /**
     * 一个工具方法，用于将以毫秒为单位的时长格式化为人类可读的字符串。
     *
     * @param millis 要格式化的时长（毫秒）。
     * @return 格式化后的字符串，形式为 "HH:MM:SS" 或 "MM:SS"。
     */
    private String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    /**
     * 控制用户是否可以通过按 Escape 键关闭屏幕。
     *
     * @return 仅当渲染过程完成后返回 {@code true}；否则返回 {@code false} 以防止意外取消。
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return this.finished;
    }
}