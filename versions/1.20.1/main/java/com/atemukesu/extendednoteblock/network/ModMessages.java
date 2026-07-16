package com.atemukesu.extendednoteblock.network;

import java.util.UUID;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import com.atemukesu.extendednoteblock.item.ConductorWandItem;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 负责注册和发送所有网络数据包的中心类。
 * <p>
 * 这个类定义了所有数据包的唯一标识符 (Identifier)，并提供了用于注册接收器和发送数据包的静态方法。
 * 它处理服务器与客户端之间的双向通信。
 */
public class ModMessages {
    /**
     * C2S (Client to Server) 数据包ID：用于从客户端GUI更新服务器上的扩展音符盒数据。
     */
    public static final Identifier UPDATE_NOTE_BLOCK_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "update_note_block");

    public static final Identifier START_SOUND_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "start_sound");
    public static final Identifier UPDATE_VOLUME_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "update_volume");
    public static final Identifier STOP_SOUND_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "stop_sound");
    public static final Identifier SMOOTH_MOVE_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "smooth_move");

    // ============== Advanced Features v1.4.0 ==============
    public static final Identifier ADVANCED_UPDATE_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "adv_update");

    // ============== Advanced Settings v1.4.0 ==============
    public static final Identifier ADVANCED_SETTINGS_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "advanced_settings");
    public static final Identifier START_ADVANCED_SOUND_ID = new Identifier(ExtendedNoteBlock.MOD_ID,
            "start_adv_sound");

    // ============== Conductor's Wand ==============
    public static final Identifier SCAN_REQUEST = new Identifier(ExtendedNoteBlock.MOD_ID, "scan_request");
    public static final Identifier SCAN_RESPONSE = new Identifier(ExtendedNoteBlock.MOD_ID, "scan_response");
    public static final Identifier BULK_UPDATE = new Identifier(ExtendedNoteBlock.MOD_ID, "bulk_update");
    public static final Identifier SET_WAND_POS_ID = new Identifier(ExtendedNoteBlock.MOD_ID, "set_wand_pos");

    /**
     * 在服务器端注册所有 C2S (客户端到服务器) 数据包的接收器。
     * 这个方法应该在模组的服务器端初始化阶段被调用。
     */
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_NOTE_BLOCK_ID, UpdateNoteBlockPacket::receive);
        // ============== Advanced Settings v1.4.0 ==============
        ServerPlayNetworking.registerGlobalReceiver(ADVANCED_SETTINGS_ID, AdvancedSettingsPacket::receive);
        // ============== Conductor's Wand ==============
        ServerPlayNetworking.registerGlobalReceiver(SCAN_REQUEST, (server, player, handler, buf, responseSender) -> {
            BlockPos pos1 = buf.readBlockPos();
            BlockPos pos2 = buf.readBlockPos();
            server.execute(() -> handleScanRequest(player, pos1, pos2));
        });

        ServerPlayNetworking.registerGlobalReceiver(BULK_UPDATE, (server, player, handler, buf, responseSender) -> {
            BlockPos p1 = buf.readBlockPos();
            BlockPos p2 = buf.readBlockPos();
            String targetBlockId = buf.readString();

            // Read updates list: {Path, Mode, Value}
            int updateCount = buf.readInt();
            final java.util.List<Triple<String, Integer, String>> updates = new java.util.ArrayList<>();
            for (int i = 0; i < updateCount; i++) {
                String path = buf.readString();
                int mode = buf.readInt(); // 0=Set, 1=Add, 2=Mult
                String value = buf.readString();
                updates.add(new Triple<>(path, mode, value));
            }

            // Read Advanced Data Patch (Always Merged/Set)
            boolean hasAdvanced = buf.readBoolean();
            final NbtCompound advancedPatch = hasAdvanced ? buf.readNbt() : null;

            server.execute(() -> {
                ServerWorld world = player.getServerWorld();
                BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()),
                        Math.min(p1.getZ(), p2.getZ()));
                BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()),
                        Math.max(p1.getZ(), p2.getZ()));

                int updatedCount = 0;
                for (BlockPos p : BlockPos.iterate(min, max)) {
                    // Force load chunk
                    net.minecraft.world.chunk.Chunk chunk = world.getChunk(p.getX() >> 4, p.getZ() >> 4,
                            net.minecraft.world.chunk.ChunkStatus.FULL, true);
                    BlockEntity be = chunk.getBlockEntity(p);
                    if (be != null) {
                        String id = Registries.BLOCK.getId(be.getCachedState().getBlock()).toString();
                        if (id.equals(targetBlockId)) {
                            NbtCompound original = be.createNbt();

                            // 1. Apply Per-Field Updates
                            for (Triple<String, Integer, String> entry : updates) {
                                com.atemukesu.extendednoteblock.util.NbtPathUtil.apply(original, entry.getA(),
                                        entry.getC(), entry.getB());
                            }

                            // 2. Apply Advanced Data Patch (Recursive Merge)
                            if (advancedPatch != null && !advancedPatch.isEmpty()) {
                                applyNbtPatch(original, advancedPatch, 0); // Mode 0 = SET/MERGE
                            }

                            // 3. Recalculate Sound Path if needed (Server Side Calc)
                            recalculateSoundPath(original);

                            be.readNbt(original);
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

        ServerPlayNetworking.registerGlobalReceiver(SET_WAND_POS_ID, (server, player, handler, buf, responseSender) -> {
            int pointIndex = buf.readInt(); // 1=Pos1, 2=Pos2, 0=Clear
            // BlockPos is read only if pointIndex != 0
            BlockPos pos = (pointIndex != 0) ? buf.readBlockPos() : null;

            server.execute(() -> {
                ItemStack stack = player.getMainHandStack();
                if (stack.getItem() instanceof ConductorWandItem) {
                    NbtCompound nbt = stack.getOrCreateNbt();
                    if (pointIndex == 0) {
                        nbt.remove("Pos1");
                        nbt.remove("Pos2");
                        player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.selection_cleared"),
                                true);
                    } else {
                        nbt.put("Pos" + pointIndex, NbtHelper.fromBlockPos(pos));
                        player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.pos_set", pointIndex,
                                pos.toShortString()), true);
                    }
                }
            });
        });
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

    public static void sendStartSoundToClients(ServerWorld world, BlockPos pos, UUID soundId, int instrumentId,
            int note, int velocity, float initialVolume) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeUuid(soundId);
        buf.writeInt(instrumentId);
        buf.writeInt(note);
        buf.writeInt(velocity);
        buf.writeFloat(initialVolume); // 初始音量
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, START_SOUND_ID, buf);
        }
    }

    public static void sendUpdateVolumeToClients(ServerWorld world, BlockPos pos, UUID soundId, float volume) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(soundId);
        buf.writeFloat(volume); // 使用 writeFloat
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, UPDATE_VOLUME_ID, buf);
        }
    }

    public static void sendStopSoundToClients(ServerWorld world, BlockPos pos, UUID soundId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(soundId);
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, STOP_SOUND_ID, buf);
        }
    }

    public static void sendSmoothMoveToClient(ServerPlayerEntity player, Vec3d pos, boolean isStop) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeBoolean(isStop);
        ServerPlayNetworking.send(player, SMOOTH_MOVE_ID, buf);
    }

    // ============== Advanced Features v1.4.0 ==============
    public static void sendAdvancedUpdateToClients(ServerWorld world, BlockPos pos, UUID soundId, float vol,
            float pitchMul, double x, double y, double z) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(soundId);
        buf.writeFloat(vol);
        buf.writeFloat(pitchMul);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);

        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, ADVANCED_UPDATE_ID, buf);
        }
    }

    public static void sendStartAdvancedSoundToClients(ServerWorld world, BlockPos pos, UUID soundId,
            int instrumentId, int note,
            float initialVolume, float initialPitchMul,
            double x, double y, double z) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeUuid(soundId);
        buf.writeInt(instrumentId);
        buf.writeInt(note);

        // 关键数据：t=0 时的状态
        buf.writeFloat(initialVolume);
        buf.writeFloat(initialPitchMul);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);

        for (ServerPlayerEntity player : PlayerLookup.tracking(world, pos)) {
            ServerPlayNetworking.send(player, START_ADVANCED_SOUND_ID, buf);
        }
    }

    // ============== Conductor's Wand Methods ==============
    public static void sendScanRequest(ServerPlayerEntity player, BlockPos pos1, BlockPos pos2) {
        // 直接在服务端逻辑中调用（如果是物品直接触发），或者通过客户端包发送
        // 这里假设物品逻辑在服务端运行，直接调用处理逻辑
        handleScanRequest(player, pos1, pos2);
    }

    // 实际处理扫描逻辑
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
        // 防止过大选区卡死 -> 修改为 INT_MAX (实际上不做限制，或者限制很大)
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        if (volume > Integer.MAX_VALUE) {
            // 即使是 MAX_VALUE 也是非常大的，这里只是形式上的检查
            player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.selection_too_large", volume), false);
            return;
        }

        player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.scanning_area"), true);

        java.util.Map<String, Integer> countMap = new java.util.HashMap<>();
        java.util.Map<String, NbtCompound> sampleNbtMap = new java.util.HashMap<>();

        // 遍历区域
        for (BlockPos p : BlockPos.iterate(min, max)) {
            // 强制加载区块以获取BlockEntity
            // data processing in unloaded chunks
            net.minecraft.world.chunk.Chunk chunk = world.getChunk(p.getX() >> 4, p.getZ() >> 4,
                    net.minecraft.world.chunk.ChunkStatus.FULL, true);
            BlockEntity be = chunk.getBlockEntity(p);

            if (be != null) {
                String id = Registries.BLOCK.getId(be.getCachedState().getBlock()).toString();
                countMap.put(id, countMap.getOrDefault(id, 0) + 1);

                // 只保存第一个遇到的该类型NBT作为样本用于GUI生成
                if (!sampleNbtMap.containsKey(id)) {
                    sampleNbtMap.put(id, be.createNbt());
                }
            }
        }

        if (countMap.isEmpty()) {
            player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.no_entities_found"), false);
            return;
        }

        // 发送结果回客户端
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(min);
        buf.writeBlockPos(max);
        buf.writeInt(countMap.size());

        for (String id : countMap.keySet()) {
            buf.writeString(id);
            buf.writeInt(countMap.get(id));
            buf.writeNbt(sampleNbtMap.get(id));
        }

        ServerPlayNetworking.send(player, SCAN_RESPONSE, buf);
    }

    private static void applyNbtPatch(NbtCompound original, NbtCompound patch, int op) {
        // 递归合并 NBT
        for (String key : patch.getKeys()) {
            // 特殊处理 ExtendedNoteBlock 的 AdvancedData
            if (key.equals("AdvancedData") && patch.contains("AdvancedData", 10)) {
                if (!original.contains("AdvancedData", 10))
                    original.put("AdvancedData", new NbtCompound());
                applyNbtPatch(original.getCompound("AdvancedData"), patch.getCompound("AdvancedData"), op);
                continue;
            }

            // 处理普通数值操作
            if (op != 0 && original.contains(key, 99) && patch.contains(key, 99)) { // 99 = Any Number
                // 获取旧值和新值
                net.minecraft.nbt.NbtElement originalElement = original.get(key);
                net.minecraft.nbt.NbtElement patchElement = patch.get(key);

                if (originalElement instanceof net.minecraft.nbt.AbstractNbtNumber origNum &&
                        patchElement instanceof net.minecraft.nbt.AbstractNbtNumber patchNum) {
                    double oldVal = origNum.doubleValue();
                    double patchVal = patchNum.doubleValue();
                    double newVal = oldVal;

                    if (op == 1)
                        newVal += patchVal; // ADD
                    else if (op == 2)
                        newVal *= patchVal; // MULTIPLY
                    else if (op == 3)
                        newVal /= (patchVal == 0 ? 1 : patchVal); // DIVIDE
                    else if (op == 4)
                        newVal -= patchVal; // SUBTRACT

                    // 根据原类型存回
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
                        original.putInt(key, (int) newVal); // fallback
                }
            } else {
                // Set / Replace mode or non-numeric
                original.put(key, patch.get(key));
            }
        }
    }

    // Server-side path generation
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
            sustain = 40; // Default fallback

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
            // If failed, maybe clear path? Or keep old?
            // Keep old or do nothing to allow user to fix expression
        }
    }
}