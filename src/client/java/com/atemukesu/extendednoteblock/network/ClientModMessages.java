package com.atemukesu.extendednoteblock.network;

import com.atemukesu.extendednoteblock.client.gui.screen.ConductorScreen;
import com.atemukesu.extendednoteblock.sound.ClientSoundManager;
import com.atemukesu.extendednoteblock.util.CurvePoint;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClientModMessages {
    public static void registerS2CPackets() {
        // ============== Sound Packets ==============
        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.StartSoundPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientSoundManager.playSound(
                    payload.pos(), payload.soundId(), payload.instrumentId(),
                    payload.note(), payload.velocity(), payload.initialVolume()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.UpdateVolumePayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientSoundManager.updateVolume(payload.soundId(), payload.volume()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.StopSoundPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientSoundManager.stopSound(payload.soundId()));
        });

        // ============== Advanced Sound Packets ==============
        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.StartAdvancedSoundPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientSoundManager.playAdvancedSound(
                    payload.pos(), payload.soundId(), payload.instrumentId(), payload.note(),
                    payload.initialVolume(), payload.initialPitchMul(),
                    payload.x(), payload.y(), payload.z()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.AdvancedUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientSoundManager.updateAdvanced(
                    payload.soundId(), payload.vol(), payload.pitchMul(),
                    payload.x(), payload.y(), payload.z()));
        });

        // ============== Conductor's Wand ==============
        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.ScanResponsePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new ConductorScreen(
                        payload.min(), payload.max(), payload.counts(), payload.samples()));
            });
        });

        // ============== Smooth Move ==============
        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.SmoothMovePayload.ID, (payload, context) -> {
            context.client().execute(() -> com.atemukesu.extendednoteblock.util.ClientSmoothMoveManager
                    .updateMove(new Vec3d(payload.x(), payload.y(), payload.z()), payload.isStop()));
        });
    }

    // ============== Conductor's Wand Methods ==============

    public static class BulkUpdateEntry {
        public final String path;
        public final int mode;
        public final String value;

        public BulkUpdateEntry(String path, int mode, String value) {
            this.path = path;
            this.mode = mode;
            this.value = value;
        }
    }

    public static void sendSmartBulkUpdateToServer(BlockPos min, BlockPos max, String targetBlockId,
            List<BulkUpdateEntry> updates, NbtCompound advancedPatch) {
        // Serialize updates list to JSON
        List<Map<String, Object>> updatesList = new ArrayList<>();
        for (BulkUpdateEntry entry : updates) {
            Map<String, Object> map = new HashMap<>();
            map.put("path", entry.path);
            map.put("mode", entry.mode);
            map.put("value", entry.value);
            updatesList.add(map);
        }
        String updatesJson = new Gson().toJson(updatesList);

        boolean hasAdvanced = advancedPatch != null && !advancedPatch.isEmpty();
        var payload = new ModPayloads.BulkUpdatePayload(min, max, targetBlockId, updatesJson, hasAdvanced,
                hasAdvanced ? advancedPatch : null);
        ClientPlayNetworking.send(payload);
    }

    public static void sendScanRequestToServer(BlockPos pos1, BlockPos pos2) {
        ClientPlayNetworking.send(new ModPayloads.ScanRequestPayload(pos1, pos2));
    }

    public static void sendClearSelectionToServer() {
        ClientPlayNetworking.send(new ModPayloads.SetWandPosPayload(0, null));
    }

    // ============== Advanced Settings v1.4.0 ==============
    public static void sendAdvancedSettingsToServer(BlockPos pos, List<CurvePoint> volumePoints,
            List<CurvePoint> pitchBendPoints, List<Vec3d> soundPath,
            String storedExprX, String storedExprY, String storedExprZ) {
        var payload = new ModPayloads.AdvancedSettingsPayload(
                pos, volumePoints, pitchBendPoints, soundPath,
                storedExprX, storedExprY, storedExprZ);
        ClientPlayNetworking.send(payload);
    }
}
