package com.atemukesu.extendednoteblock.sound;

import com.atemukesu.extendednoteblock.network.ModMessages;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

public class ActiveSoundFader {

    enum State {
        FADING_IN, SUSTAINING, FADING_OUT, DONE
    }

    private final ServerWorld world;
    private final BlockPos pos;
    private final UUID soundId;
    private final int fadeInTicks, fadeOutTicks, sustainTicks;
    private final float baseVolume;

    private int ticksInState = 0;
    private float lastSentVolume = -1.0f;
    private State currentState = State.FADING_IN;

    public ActiveSoundFader(ServerWorld world, BlockPos pos, UUID soundId, int velocity, int sustain, int fadeIn,
            int fadeOut) {
        this.world = world;
        this.pos = pos;
        this.soundId = soundId;
        this.baseVolume = velocity / 127.0f;
        this.sustainTicks = sustain;
        this.fadeInTicks = Math.max(1, fadeIn);
        this.fadeOutTicks = Math.max(1, fadeOut);
    }

    public boolean tick() {
        if (currentState == State.DONE)
            return true;

        ticksInState++;
        float currentVolume = calculateCurrentVolume();

        if (Math.abs(currentVolume - lastSentVolume) > 0.01f || (currentVolume == 0 && lastSentVolume != 0)
                || (currentVolume == baseVolume && lastSentVolume != baseVolume)) {
            ModMessages.sendUpdateVolumeToClients(world, pos, soundId, currentVolume);
            lastSentVolume = currentVolume;
        }

        updateState();
        return currentState == State.DONE;
    }

    private void updateState() {
        switch (currentState) {
            case FADING_IN:
                if (ticksInState >= fadeInTicks) {
                    currentState = State.SUSTAINING;
                    ticksInState = 0;
                }
                break;
            case SUSTAINING:
                int sustainDuration = sustainTicks - fadeInTicks - fadeOutTicks;
                if (sustainDuration > 0 && ticksInState >= sustainDuration) {
                    startFadeOut();
                }
                break;
            case FADING_OUT:
                if (ticksInState >= fadeOutTicks) {
                    currentState = State.DONE;
                }
                break;
            case DONE:
                break;
        }
    }

    private float calculateCurrentVolume() {
        switch (currentState) {
            case FADING_IN:
                return baseVolume * ((float) ticksInState / fadeInTicks);
            case SUSTAINING:
                return baseVolume;
            case FADING_OUT:
                return baseVolume * (1.0f - ((float) ticksInState / fadeOutTicks));
            default:
                return 0.0f;
        }
    }

    public void startFadeOut() {
        if (currentState != State.FADING_OUT && currentState != State.DONE) {
            this.currentState = State.FADING_OUT;
            this.ticksInState = 0;
        }
    }

    public BlockPos getPos() {
        return pos;
    }

    public ServerWorld getWorld() {
        return world;
    }

    public boolean isSustaining() {
        return currentState == State.SUSTAINING;
    }
}