package com.atemukesu.extendednoteblock.network;

import com.atemukesu.extendednoteblock.util.CurvePoint;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ModPayloads {

    // ============== C2S Payloads ==============

    // C2S - Update Note Block
    public record UpdateNoteBlockPayload(
            BlockPos pos, int note, int velocity, int sustain, int delay, int fadeIn, int fadeOut, int instrumentId
    ) implements CustomPayload {
        public static final Id<UpdateNoteBlockPayload> ID = new Id<>(Identifier.of("extendednoteblock", "update_note_block"));
        public static final PacketCodec<PacketByteBuf, UpdateNoteBlockPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeInt(payload.note);
                    buf.writeInt(payload.velocity);
                    buf.writeInt(payload.sustain);
                    buf.writeInt(payload.delay);
                    buf.writeInt(payload.fadeIn);
                    buf.writeInt(payload.fadeOut);
                    buf.writeInt(payload.instrumentId);
                },
                buf -> new UpdateNoteBlockPayload(
                        buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()
                )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // C2S - Advanced Settings
    public record AdvancedSettingsPayload(
            BlockPos pos, List<CurvePoint> volumePoints, List<CurvePoint> pitchBendPoints,
            List<Vec3d> soundPath, String storedExprX, String storedExprY, String storedExprZ
    ) implements CustomPayload {
        public static final Id<AdvancedSettingsPayload> ID = new Id<>(Identifier.of("extendednoteblock", "advanced_settings"));
        public static final PacketCodec<PacketByteBuf, AdvancedSettingsPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeBlockPos(payload.pos);

                    // Write volume points
                    List<CurvePoint> volPoints = payload.volumePoints;
                    buf.writeInt(volPoints.size());
                    for (CurvePoint p : volPoints) {
                        buf.writeFloat(p.time);
                        buf.writeFloat(p.value);
                    }

                    // Write pitch bend points
                    List<CurvePoint> pitchPoints = payload.pitchBendPoints;
                    buf.writeInt(pitchPoints.size());
                    for (CurvePoint p : pitchPoints) {
                        buf.writeFloat(p.time);
                        buf.writeFloat(p.value);
                    }

                    // Write sound path
                    List<Vec3d> path = payload.soundPath;
                    buf.writeInt(path.size());
                    for (Vec3d v : path) {
                        buf.writeDouble(v.x);
                        buf.writeDouble(v.y);
                        buf.writeDouble(v.z);
                    }

                    // Write expressions
                    buf.writeString(payload.storedExprX);
                    buf.writeString(payload.storedExprY);
                    buf.writeString(payload.storedExprZ);
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();

                    int volSize = buf.readInt();
                    List<CurvePoint> volumePoints = new ArrayList<>();
                    for (int i = 0; i < volSize; i++) {
                        volumePoints.add(new CurvePoint(buf.readFloat(), buf.readFloat()));
                    }

                    int pitchSize = buf.readInt();
                    List<CurvePoint> pitchBendPoints = new ArrayList<>();
                    for (int i = 0; i < pitchSize; i++) {
                        pitchBendPoints.add(new CurvePoint(buf.readFloat(), buf.readFloat()));
                    }

                    int pathSize = buf.readInt();
                    List<Vec3d> soundPath = new ArrayList<>();
                    for (int i = 0; i < pathSize; i++) {
                        soundPath.add(new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()));
                    }

                    String exprX = buf.readString();
                    String exprY = buf.readString();
                    String exprZ = buf.readString();

                    return new AdvancedSettingsPayload(pos, volumePoints, pitchBendPoints, soundPath, exprX, exprY, exprZ);
                }
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // C2S - Scan Request
    public record ScanRequestPayload(BlockPos pos1, BlockPos pos2) implements CustomPayload {
        public static final Id<ScanRequestPayload> ID = new Id<>(Identifier.of("extendednoteblock", "scan_request"));
        public static final PacketCodec<PacketByteBuf, ScanRequestPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeBlockPos(payload.pos1);
                    buf.writeBlockPos(payload.pos2);
                },
                buf -> new ScanRequestPayload(buf.readBlockPos(), buf.readBlockPos())
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // C2S - Bulk Update
    public record BulkUpdatePayload(
            BlockPos p1, BlockPos p2, String targetBlockId,
            String updatesJson, boolean hasAdvanced, NbtCompound advancedPatch
    ) implements CustomPayload {
        public static final Id<BulkUpdatePayload> ID = new Id<>(Identifier.of("extendednoteblock", "bulk_update"));
        public static final PacketCodec<PacketByteBuf, BulkUpdatePayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeBlockPos(payload.p1);
                    buf.writeBlockPos(payload.p2);
                    buf.writeString(payload.targetBlockId);
                    buf.writeString(payload.updatesJson);
                    buf.writeBoolean(payload.hasAdvanced);
                    if (payload.hasAdvanced) {
                        buf.writeNbt(payload.advancedPatch);
                    }
                },
                buf -> {
                    BlockPos p1 = buf.readBlockPos();
                    BlockPos p2 = buf.readBlockPos();
                    String targetBlockId = buf.readString();
                    String updatesJson = buf.readString();
                    boolean hasAdvanced = buf.readBoolean();
                    NbtCompound advancedPatch = hasAdvanced ? buf.readNbt() : null;
                    return new BulkUpdatePayload(p1, p2, targetBlockId, updatesJson, hasAdvanced, advancedPatch);
                }
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // C2S - Set Wand Pos
    public record SetWandPosPayload(int pointIndex, BlockPos pos) implements CustomPayload {
        public static final Id<SetWandPosPayload> ID = new Id<>(Identifier.of("extendednoteblock", "set_wand_pos"));
        public static final PacketCodec<PacketByteBuf, SetWandPosPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeInt(payload.pointIndex);
                    if (payload.pointIndex != 0) {
                        buf.writeBlockPos(payload.pos);
                    }
                },
                buf -> {
                    int pointIndex = buf.readInt();
                    BlockPos pos = (pointIndex != 0) ? buf.readBlockPos() : null;
                    return new SetWandPosPayload(pointIndex, pos);
                }
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // ============== S2C Payloads ==============

    // S2C - Start Sound
    public record StartSoundPayload(
            BlockPos pos, UUID soundId, int instrumentId, int note, int velocity, float initialVolume
    ) implements CustomPayload {
        public static final Id<StartSoundPayload> ID = new Id<>(Identifier.of("extendednoteblock", "start_sound"));
        public static final PacketCodec<PacketByteBuf, StartSoundPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeUuid(payload.soundId);
                    buf.writeInt(payload.instrumentId);
                    buf.writeInt(payload.note);
                    buf.writeInt(payload.velocity);
                    buf.writeFloat(payload.initialVolume);
                },
                buf -> new StartSoundPayload(
                        buf.readBlockPos(), buf.readUuid(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readFloat()
                )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // S2C - Update Volume
    public record UpdateVolumePayload(UUID soundId, float volume) implements CustomPayload {
        public static final Id<UpdateVolumePayload> ID = new Id<>(Identifier.of("extendednoteblock", "update_volume"));
        public static final PacketCodec<PacketByteBuf, UpdateVolumePayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.soundId);
                    buf.writeFloat(payload.volume);
                },
                buf -> new UpdateVolumePayload(buf.readUuid(), buf.readFloat())
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // S2C - Stop Sound
    public record StopSoundPayload(UUID soundId) implements CustomPayload {
        public static final Id<StopSoundPayload> ID = new Id<>(Identifier.of("extendednoteblock", "stop_sound"));
        public static final PacketCodec<PacketByteBuf, StopSoundPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeUuid(payload.soundId),
                buf -> new StopSoundPayload(buf.readUuid())
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // S2C - Smooth Move
    public record SmoothMovePayload(double x, double y, double z, boolean isStop) implements CustomPayload {
        public static final Id<SmoothMovePayload> ID = new Id<>(Identifier.of("extendednoteblock", "smooth_move"));
        public static final PacketCodec<PacketByteBuf, SmoothMovePayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeDouble(payload.x);
                    buf.writeDouble(payload.y);
                    buf.writeDouble(payload.z);
                    buf.writeBoolean(payload.isStop);
                },
                buf -> new SmoothMovePayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean())
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // S2C - Advanced Update
    public record AdvancedUpdatePayload(
            UUID soundId, float vol, float pitchMul, double x, double y, double z
    ) implements CustomPayload {
        public static final Id<AdvancedUpdatePayload> ID = new Id<>(Identifier.of("extendednoteblock", "adv_update"));
        public static final PacketCodec<PacketByteBuf, AdvancedUpdatePayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.soundId);
                    buf.writeFloat(payload.vol);
                    buf.writeFloat(payload.pitchMul);
                    buf.writeDouble(payload.x);
                    buf.writeDouble(payload.y);
                    buf.writeDouble(payload.z);
                },
                buf -> new AdvancedUpdatePayload(
                        buf.readUuid(), buf.readFloat(), buf.readFloat(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble()
                )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // S2C - Start Advanced Sound
    public record StartAdvancedSoundPayload(
            BlockPos pos, UUID soundId, int instrumentId, int note,
            float initialVolume, float initialPitchMul, double x, double y, double z
    ) implements CustomPayload {
        public static final Id<StartAdvancedSoundPayload> ID = new Id<>(Identifier.of("extendednoteblock", "start_adv_sound"));
        public static final PacketCodec<PacketByteBuf, StartAdvancedSoundPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeUuid(payload.soundId);
                    buf.writeInt(payload.instrumentId);
                    buf.writeInt(payload.note);
                    buf.writeFloat(payload.initialVolume);
                    buf.writeFloat(payload.initialPitchMul);
                    buf.writeDouble(payload.x);
                    buf.writeDouble(payload.y);
                    buf.writeDouble(payload.z);
                },
                buf -> new StartAdvancedSoundPayload(
                        buf.readBlockPos(), buf.readUuid(), buf.readInt(), buf.readInt(),
                        buf.readFloat(), buf.readFloat(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble()
                )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // S2C - Scan Response
    public record ScanResponsePayload(
            BlockPos min, BlockPos max, Map<String, Integer> counts, Map<String, NbtCompound> samples
    ) implements CustomPayload {
        public static final Id<ScanResponsePayload> ID = new Id<>(Identifier.of("extendednoteblock", "scan_response"));
        public static final PacketCodec<PacketByteBuf, ScanResponsePayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeBlockPos(payload.min);
                    buf.writeBlockPos(payload.max);
                    buf.writeInt(payload.counts.size());
                    for (Map.Entry<String, Integer> entry : payload.counts.entrySet()) {
                        buf.writeString(entry.getKey());
                        buf.writeInt(entry.getValue());
                        buf.writeNbt(payload.samples.get(entry.getKey()));
                    }
                },
                buf -> {
                    BlockPos min = buf.readBlockPos();
                    BlockPos max = buf.readBlockPos();
                    int size = buf.readInt();
                    Map<String, Integer> counts = new HashMap<>();
                    Map<String, NbtCompound> samples = new HashMap<>();
                    for (int i = 0; i < size; i++) {
                        String id = buf.readString();
                        int count = buf.readInt();
                        NbtCompound nbt = buf.readNbt();
                        counts.put(id, count);
                        samples.put(id, nbt);
                    }
                    return new ScanResponsePayload(min, max, counts, samples);
                }
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
