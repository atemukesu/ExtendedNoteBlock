package com.atemukesu.extendednoteblock.client.gui.screen;

import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;

public class PreLaunchCheckScreen extends Screen {
    private enum State {
        CHECKING, SUCCESS, FAILURE
    }

    private State currentState = State.CHECKING;
    private int ticks = 40;
    private final Text checkingText = Text.translatable("gui.extendednoteblock.pre_check.status.checking");
    private final Text successText = Text.translatable("gui.extendednoteblock.pre_check.status.success");
    private final Text failureText = Text.translatable("gui.extendednoteblock.pre_check.status.failure");

    public PreLaunchCheckScreen() {
        super(Text.translatable("gui.extendednoteblock.pre_check.title"));
    }

    @Override
    protected void init() {
        super.init();
        runCheck();
    }

    private void runCheck() {
        this.currentState = State.CHECKING;
        new Thread(() -> {
            boolean packFilesReady = ConfigManager.isActiveSoundPackReady();
            boolean packIsEnabled = SoundPackManager.getInstance().isCurrentPackActuallyEnabled();
            if (!packIsEnabled) {
                SoundPackManager.getInstance().setActivePack((SoundPackManager.getInstance().getActivePackId()));
                packIsEnabled = true;
            }
            final boolean finalCheckResult = packFilesReady && packIsEnabled;
            if (this.client != null) {
                this.client.execute(() -> {
                    this.currentState = finalCheckResult ? State.SUCCESS : State.FAILURE;
                    this.ticks = 0;
                });
            }
        }, "ENB-PreLaunchCheck").start();
    }

    @Override
    public void tick() {
        super.tick();
        this.ticks++;
        if (this.currentState == State.SUCCESS && this.ticks > 40) {
            if (this.client != null) {
                this.client.setScreen(new TitleScreen());
            }
        } else if (this.currentState == State.FAILURE && this.ticks > 40) {
            if (this.client != null) {
                boolean dependenciesConfigured = StringUtils.isNotBlank(ConfigManager.getConfig().fluidSynthPath)
                        && StringUtils.isNotBlank(ConfigManager.getConfig().ffmpegPath);
                if (dependenciesConfigured) {
                    this.client.setScreen(new SoundPackManagerScreen(new TitleScreen()));
                } else {
                    this.client.setScreen(new ConfigScreen(new TitleScreen()));
                }
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 50,
                0xFFFFFF);
        Text statusText;
        int statusColor;
        switch (this.currentState) {
            case SUCCESS:
                statusText = this.successText;
                statusColor = 0x55FF55;
                break;
            case FAILURE:
                statusText = this.failureText;
                statusColor = 0xFF5555;
                break;
            default:
                statusText = this.checkingText;
                statusColor = 0xFFFF55;
                break;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, statusText, this.width / 2, this.height / 2, statusColor);
        renderProgressBar(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderProgressBar(DrawContext context) {
        int barWidth = 200;
        int barHeight = 8;
        int barX = (this.width - barWidth) / 2;
        int barY = this.height / 2 + 20;
        context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
        if (this.currentState == State.CHECKING) {
            int sliderWidth = 50;
            float progress = (Math.abs(System.currentTimeMillis() % 2000L - 1000L)) / 1000.0f;
            int sliderPos = (int) (progress * (barWidth - sliderWidth));
            context.fill(barX + sliderPos, barY, barX + sliderPos + sliderWidth, barY + barHeight, 0xFF55FF55);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}