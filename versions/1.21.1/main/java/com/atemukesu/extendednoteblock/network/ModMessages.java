package com.atemukesu.extendednoteblock.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.atemukesu.extendednoteblock.block.ExtendedNoteBlockBlock;
import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;
import com.atemukesu.extendednoteblock.item.ConductorWandItem;
import com.atemukesu.extendednoteblock.map.InstrumentMap;
import com.atemukesu.extendednoteblock.util.CurvePoint;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ModMessages {

    public static void registerC2SPackets() {
        // Register C2S payload types
        PayloadTypeRegistry.playC2S().register(ModPayloads.UpdateNoteBlockPayload.ID, ModPayloads.UpdateNoteBlockPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ModPayloads.AdvancedSettingsPayload.ID, ModPayloads.AdvancedSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ModPayloads.ScanRequestPayload.ID, ModPayloads.ScanRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ModPayloads.BulkUpdatePayload.ID, ModPayloads.BulkUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ModPayloads.SetWandPosPayload.ID, ModPayloads.SetWandPosPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ModPayloads.PreviewRequestPayload.ID, ModPayloads.PreviewRequestPayload.CODEC);

        // ============== Update Note Block ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.UpdateNoteBlockPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                var world = player.getServerWorld();

                int note = MathHelper.clamp(payload.note(), 0, 127);
                int velocity = MathHelper.clamp(payload.velocity(), 0, 127);
                int sustain = MathHelper.clamp(payload.sustain(), 0, 400);
                int delay = MathHelper.clamp(payload.delay(), 0, 5000);
                int fadeIn = MathHelper.clamp(payload.fadeIn(), 0, 400);
                int fadeOut = MathHelper.clamp(payload.fadeOut(), 0, 400);
                int instrumentId = payload.instrumentId();

                if (world.getBlockEntity(payload.pos()) instanceof ExtendedNoteBlockEntity entity) {
                    entity.updateValues(note, velocity, sustain, delay, fadeIn, fadeOut);
                    updateInstrumentBlock(player, world, payload.pos(), instrumentId);
                } else {
                    System.err.println("在位置 " + payload.pos() + " 未找到 ExtendedNoteBlockEntity");
                }
            });
        });

        // ============== Advanced Settings v1.4.0 ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.AdvancedSettingsPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                World world = player.getWorld();
                if (world.getBlockEntity(payload.pos()) instanceof ExtendedNoteBlockEntity entity) {
                    entity.setVolumePoints(payload.volumePoints());
                    entity.setPitchBendPoints(payload.pitchBendPoints());
                    entity.setSoundPath(payload.soundPath());
                    entity.setStoredExpressionX(payload.storedExprX());
                    entity.setStoredExpressionY(payload.storedExprY());
                    entity.setStoredExpressionZ(payload.storedExprZ());
                }
            });
        });

        // ============== Conductor's Wand: Scan Request ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.ScanRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                handleScanRequest(player, payload.pos1(), payload.pos2());
            });
        });

        // ============== Conductor's Wand: Bulk Update ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.BulkUpdatePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                ServerWorld world = player.getServerWorld();
                BlockPos min = new BlockPos(
                        Math.min(payload.p1().getX(), payload.p2().getX()),
                        Math.min(payload.p1().getY(), payload.p2().getY()),
                        Math.min(payload.p1().getZ(), payload.p2().getZ()));
                BlockPos max = new BlockPos(
                        Math.max(payload.p1().getX(), payload.p2().getX()),
                        Math.max(payload.p1().getY(), payload.p2().getY()),
                        Math.max(payload.p1().getZ(), payload.p2().getZ()));

                // Parse updatesJson
                List<Triple<String, Integer, String>> updates = new ArrayList<>();
                try {
                    List<Map<String, Object>> updatesList = new Gson().fromJson(
                            payload.updatesJson(),
                            new TypeToken<List<Map<String, Object>>>() {}.getType());
                    for (Map<String, Object> entry : updatesList) {
                        String path = (String) entry.get("path");
                        int mode = ((Number) entry.get("mode")).intValue();
                        String value = (String) entry.get("value");
                        updates.add(new Triple<>(path, mode, value));
                    }
                } catch (Exception e) {
                    System.err.println("解析 updatesJson 失败: " + e.getMessage());
                }

                NbtCompound advancedPatch = payload.hasAdvanced() ? payload.advancedPatch() : null;

                int updatedCount = 0;
                for (BlockPos p : BlockPos.iterate(min, max)) {
                    net.minecraft.world.chunk.Chunk chunk = world.getChunk(p.getX() >> 4, p.getZ() >> 4,
                            net.minecraft.world.chunk.ChunkStatus.FULL, true);
                    BlockEntity be = chunk.getBlockEntity(p);
                    if (be != null) {
                        String id = Registries.BLOCK.getId(be.getCachedState().getBlock()).toString();
                        if (id.equals(payload.targetBlockId())) {
                            NbtCompound original = be.createNbt(world.getRegistryManager());

                            for (Triple<String, Integer, String> entry : updates) {
                                com.atemukesu.extendednoteblock.util.NbtPathUtil.apply(original, entry.getA(),
                                        entry.getC(), entry.getB());
                            }

                            if (advancedPatch != null && !advancedPatch.isEmpty()) {
                                applyNbtPatch(original, advancedPatch, 0);
                            }

                            recalculateSoundPath(original);

                            be.read(original, world.getRegistryManager());
                            be.markDirty();
                            world.updateListeners(p, be.getCachedState(), be.getCachedState(), Block.NOTIFY_LISTENERS);
                            updatedCount++;
                        }
                    }
                }
                player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.update_result", updatedCount),
                        false);
            });
        });

        // ============== Conductor's Wand: Set Wand Pos ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.SetWandPosPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                ItemStack stack = player.getMainHandStack();
                if (stack.getItem() instanceof ConductorWandItem) {
                    NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
                    if (payload.pointIndex() == 0) {
                        nbt.remove("Pos1");
                        nbt.remove("Pos2");
                        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                        player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.selection_cleared"),
                                true);
                    } else {
                        nbt.put("Pos" + payload.pointIndex(), NbtHelper.fromBlockPos(payload.pos()));
                        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                        player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.pos_set",
                                payload.pointIndex(), payload.pos().toShortString()), true);
                    }
                }
            });
        });

        // ============== Preview Request ==============
        ServerPlayNetworking.registerGlobalReceiver(ModPayloads.PreviewRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                var world = player.getWorld();
                var pos = payload.pos();
                if (world.getBlockState(pos).getBlock() instanceof ExtendedNoteBlockBlock block) {
                    block.previewNote(world, pos);
                }
            });
        });
    }

    public static void registerS2CPackets() {
        PayloadTypeRegistry.playS2C().register(ModPayloads.StartSoundPayload.ID, ModPayloads.StartSoundPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ModPayloads.UpdateVolumePayload.ID, ModPayloads.UpdateVolumePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ModPayloads.StopSoundPayload.ID, ModPayloads.StopSoundPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ModPayloads.SmoothMovePayload.ID, ModPayloads.SmoothMovePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ModPayloads.AdvancedUpdatePayload.ID, ModPayloads.AdvancedUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ModPayloads.StartAdvancedSoundPayload.ID, ModPayloads.StartAdvancedSoundPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ModPayloads.ScanResponsePayload.ID, ModPayloads.ScanResponsePayload.CODEC);
    }

    // Helper class for Triple
    private static class Triple<A, B, C> {
        private final A a;
        private final B b;
        private final C c;

        public Triple(A a, B b, C c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }

        public A getA() {
            return a;
        }

        public B getB() {
            return b;
        }

        public C getC() {
            return c;
        }
    }

    // ============== S2C Send Methods ==============

    public static void sendStartSoundToClients(ServerWorld world, BlockPos pos, UUID soundId, int instrumentId,
            int note, int velocity, float initialVolume) {
        var payload = new ModPayloads.StartSoundPayload(pos, soundId, instrumentId, note, velocity, initialVolume);
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendUpdateVolumeToClients(ServerWorld world, BlockPos pos, UUID soundId, float volume) {
        var payload = new ModPayloads.UpdateVolumePayload(soundId, volume);
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendStopSoundToClients(ServerWorld world, BlockPos pos, UUID soundId) {
        var payload = new ModPayloads.StopSoundPayload(soundId);
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendSmoothMoveToClient(ServerPlayerEntity player, Vec3d pos, boolean isStop) {
        var payload = new ModPayloads.SmoothMovePayload(pos.x, pos.y, pos.z, isStop);
        ServerPlayNetworking.send(player, payload);
    }

    // ============== Advanced Features v1.4.0 ==============
    public static void sendAdvancedUpdateToClients(ServerWorld world, BlockPos pos, UUID soundId, float vol,
            float pitchMul, double x, double y, double z) {
        var payload = new ModPayloads.AdvancedUpdatePayload(soundId, vol, pitchMul, x, y, z);
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendStartAdvancedSoundToClients(ServerWorld world, BlockPos pos, UUID soundId,
            int instrumentId, int note,
            float initialVolume, float initialPitchMul,
            double x, double y, double z) {
        var payload = new ModPayloads.StartAdvancedSoundPayload(pos, soundId, instrumentId, note,
                initialVolume, initialPitchMul, x, y, z);
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    // ============== Conductor's Wand Methods ==============
    public static void sendScanRequest(ServerPlayerEntity player, BlockPos pos1, BlockPos pos2) {
        handleScanRequest(player, pos1, pos2);
    }

    // ============== Internal Handler Methods ==============

    private static void handleScanRequest(ServerPlayerEntity player, BlockPos pos1, BlockPos pos2) {
        ServerWorld world = player.getServerWorld();
        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()));
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        if (volume > Integer.MAX_VALUE) {
            player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.selection_too_large", volume), false);
            return;
        }

        player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.scanning_area"), true);

        java.util.Map<String, Integer> countMap = new java.util.HashMap<>();
        java.util.Map<String, NbtCompound> sampleNbtMap = new java.util.HashMap<>();

        for (BlockPos p : BlockPos.iterate(min, max)) {
            net.minecraft.world.chunk.Chunk chunk = world.getChunk(p.getX() >> 4, p.getZ() >> 4,
                    net.minecraft.world.chunk.ChunkStatus.FULL, true);
            BlockEntity be = chunk.getBlockEntity(p);

            if (be != null) {
                String id = Registries.BLOCK.getId(be.getCachedState().getBlock()).toString();
                countMap.put(id, countMap.getOrDefault(id, 0) + 1);

                if (!sampleNbtMap.containsKey(id)) {
                    sampleNbtMap.put(id, be.createNbt(world.getRegistryManager()));
                }
            }
        }

        if (countMap.isEmpty()) {
            player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.no_entities_found"), false);
            return;
        }

        // Send scan response using CustomPayload
        var payload = new ModPayloads.ScanResponsePayload(min, max, countMap, sampleNbtMap);
        ServerPlayNetworking.send(player, payload);
    }

    private static void updateInstrumentBlock(ServerPlayerEntity player, World world, BlockPos noteBlockPos,
            int instrumentId) {
        BlockPos belowPos = noteBlockPos.down();
        String targetBlockId = InstrumentMap.GM_INSTRUMENT_TO_BLOCK.get(instrumentId);

        if (targetBlockId != null) {
            try {
                Block targetBlock = Registries.BLOCK.get(Identifier.of(targetBlockId));
                Block currentBlockBelow = world.getBlockState(belowPos).getBlock();

                if (targetBlock != null && targetBlock != currentBlockBelow) {
                    if (world.canPlayerModifyAt(player, belowPos)) {
                        world.setBlockState(belowPos, targetBlock.getDefaultState(), 3);
                    } else {
                        player.sendMessage(Text.translatable("gui.extendednoteblock.error.no_permission"), true);
                    }
                }
            } catch (Exception e) {
                System.err.println("更换方块时出错: " + e.getMessage());
            }
        }
    }

    private static void applyNbtPatch(NbtCompound original, NbtCompound patch, int op) {
        for (String key : patch.getKeys()) {
            if (key.equals("AdvancedData") && patch.contains("AdvancedData", 10)) {
                if (!original.contains("AdvancedData", 10))
                    original.put("AdvancedData", new NbtCompound());
                applyNbtPatch(original.getCompound("AdvancedData"), patch.getCompound("AdvancedData"), op);
                continue;
            }

            if (op != 0 && original.contains(key, 99) && patch.contains(key, 99)) {
                net.minecraft.nbt.NbtElement originalElement = original.get(key);
                net.minecraft.nbt.NbtElement patchElement = patch.get(key);

                if (originalElement instanceof net.minecraft.nbt.AbstractNbtNumber origNum &&
                        patchElement instanceof net.minecraft.nbt.AbstractNbtNumber patchNum) {
                    double oldVal = origNum.doubleValue();
                    double patchVal = patchNum.doubleValue();
                    double newVal = oldVal;

                    if (op == 1)
                        newVal += patchVal;
                    else if (op == 2)
                        newVal *= patchVal;
                    else if (op == 3)
                        newVal /= (patchVal == 0 ? 1 : patchVal);
                    else if (op == 4)
                        newVal -= patchVal;

                    if (originalElement instanceof net.minecraft.nbt.NbtInt)
                        original.putInt(key, (int) newVal);
                    else if (originalElement instanceof net.minecraft.nbt.NbtFloat)
                        original.putFloat(key, (float) newVal);
                    else if (originalElement instanceof net.minecraft.nbt.NbtDouble)
                        original.putDouble(key, newVal);
                    else if (originalElement instanceof net.minecraft.nbt.NbtShort)
                        original.putShort(key, (short) newVal);
                    else if (originalElement instanceof net.minecraft.nbt.NbtByte)
                        original.putByte(key, (byte) newVal);
                    else if (originalElement instanceof net.minecraft.nbt.NbtLong)
                        original.putLong(key, (long) newVal);
                    else
                        original.putInt(key, (int) newVal);
                }
            } else {
                original.put(key, patch.get(key));
            }
        }
    }

    private static void recalculateSoundPath(NbtCompound nbt) {
        if (!nbt.contains("AdvancedData", 10))
            return;
        NbtCompound adv = nbt.getCompound("AdvancedData");

        String ex = adv.getString("ExpressionX");
        String ey = adv.getString("ExpressionY");
        String ez = adv.getString("ExpressionZ");

        if (ex.isEmpty() && ey.isEmpty() && ez.isEmpty())
            return;

        int sustain = nbt.getInt("sustainTime");
        if (sustain <= 0)
            sustain = 40;

        net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();
        try {
            net.objecthunter.exp4j.Expression eX = new net.objecthunter.exp4j.ExpressionBuilder(ex.isEmpty() ? "0" : ex)
                    .variables("t", "d").build();
            net.objecthunter.exp4j.Expression eY = new net.objecthunter.exp4j.ExpressionBuilder(ey.isEmpty() ? "0" : ey)
                    .variables("t", "d").build();
            net.objecthunter.exp4j.Expression eZ = new net.objecthunter.exp4j.ExpressionBuilder(ez.isEmpty() ? "0" : ez)
                    .variables("t", "d").build();

            for (int i = 0; i < sustain; i++) {
                double t = (double) i / Math.max(1, sustain);
                NbtCompound pos = new NbtCompound();
                pos.putDouble("x", eX.setVariable("t", t).setVariable("d", i).evaluate());
                pos.putDouble("y", eY.setVariable("t", t).setVariable("d", i).evaluate());
                pos.putDouble("z", eZ.setVariable("t", t).setVariable("d", i).evaluate());
                list.add(pos);
            }
            adv.put("SoundPath", list);
        } catch (Exception e) {
            // If failed, keep old path
        }
    }
}
