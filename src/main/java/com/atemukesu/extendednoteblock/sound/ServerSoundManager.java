package com.atemukesu.extendednoteblock.sound;

import com.atemukesu.extendednoteblock.network.ModMessages;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerSoundManager {

    private static final ConcurrentHashMap<UUID, ActiveSoundFader> activeSounds = new ConcurrentHashMap<>();

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(ServerSoundManager::tick);
    }

    public static void playSound(ServerWorld world, BlockPos pos, int instrumentId, int note, int velocity,
            int sustainTicks, int fadeInTicks, int fadeOutTicks) {
        UUID soundId = UUID.randomUUID();
        ActiveSoundFader fader = new ActiveSoundFader(world, pos, soundId, velocity, sustainTicks, fadeInTicks,
                fadeOutTicks);
        activeSounds.put(soundId, fader);
        ModMessages.sendStartSoundToClients(world, pos, soundId, instrumentId, note, velocity);
    }

    public static void stopSound(ServerWorld world, BlockPos pos) {
        activeSounds.values().stream()
                .filter(fader -> fader.getPos().equals(pos))
                .forEach(ActiveSoundFader::startFadeOut);
    }

    private static void tick(MinecraftServer server) {
        if (activeSounds.isEmpty())
            return;

        activeSounds.forEach((uuid, fader) -> {
            boolean isFinished = fader.tick();
            if (isFinished) {
                activeSounds.remove(uuid);
                ModMessages.sendStopSoundToClients(fader.getWorld(), fader.getPos(), uuid);
            }
        });
    }
}