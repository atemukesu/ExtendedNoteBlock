package com.atemukesu.extendednoteblock.sound;

import com.atemukesu.extendednoteblock.util.CurvePoint;
import com.atemukesu.extendednoteblock.network.ModMessages;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
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
        float initialVolume = (fadeInTicks <= 1) ? (velocity / 127.0f) : 0.001f; // 音量大小
        ModMessages.sendStartSoundToClients(world, pos, soundId, instrumentId, note, velocity, initialVolume); // 传递初始音量
    }

    // ============== Advanced Features v1.4.0 ==============
    public static void playAdvancedSound(ServerWorld world, BlockPos pos, int instrumentId, int note, int velocity,
                                         int sustainTicks, int fadeInTicks, int fadeOutTicks,
                                         List<CurvePoint> pitchBendPoints, List<CurvePoint> volumePoints, List<Vec3d> soundPath) {

        UUID soundId = UUID.randomUUID();
        ActiveSoundFader fader = new ActiveSoundFader(world, pos, soundId, velocity, sustainTicks, fadeInTicks, fadeOutTicks);

        fader.setPitchBendPoints(pitchBendPoints);
        fader.setVolumePoints(volumePoints);
        fader.setSoundPath(soundPath);

        activeSounds.put(soundId, fader);

        // 计算 t=0 的状态
        ActiveSoundFader.SoundState initial = fader.calculateStateAt(0.0f);

        // [修改] 调用新的发送方法 START_ADVANCED_SOUND_ID
        ModMessages.sendStartAdvancedSoundToClients(
                world, pos, soundId, instrumentId, note,
                initial.volume, // 如果曲线t=0是0，这里发过去就是0
                initial.pitch,  // 这里发过去的是倍率 (例如 1.0)
                initial.x, initial.y, initial.z
        );
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