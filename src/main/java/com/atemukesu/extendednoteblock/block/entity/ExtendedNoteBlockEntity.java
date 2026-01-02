package com.atemukesu.extendednoteblock.block.entity;

import com.atemukesu.extendednoteblock.block.ExtendedNoteBlockBlock;
import com.atemukesu.extendednoteblock.map.InstrumentMap;
import com.atemukesu.extendednoteblock.screen.ExtendedNoteBlockScreenHandler;
import com.atemukesu.extendednoteblock.util.CurvePoint;
import com.atemukesu.extendednoteblock.util.NotePitch;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ScheduledFuture;

/**
 * 扩展音符盒的方块实体 (Block Entity)。
 *
 * 这个类负责存储和管理扩展音符盒的自定义数据，例如 MIDI 音高、力度和持续时间。
 * 它也实现了 {@link ExtendedScreenHandlerFactory} 接口，使其能够打开一个自定义的 GUI 界面。
 *
 * 主要职责:
 * - 存储音符属性 (note, sustainTime, velocity)。
 * - 将数据序列化到 NBT 标签中，用于保存和网络同步。
 * - 提供一个 {@link PropertyDelegate} 来同步整数属性到 GUI ScreenHandler。
 * - 根据下方的方块动态确定当前乐器ID。
 * - 作为创建 {@link ExtendedNoteBlockScreenHandler} 的工厂。
 */
public class ExtendedNoteBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {

    /**
     * MIDI 音高值 (0-127)。
     * 60 代表中央C。
     */
    private int note = 60;
    /**
     * 音符持续时间，单位为游戏刻 (ticks)。
     * 20 ticks = 1 秒。
     */
    private int sustainTime = 40;
    /**
     * MIDI 力度值 (0-127)，影响音符的音量。
     */
    private int velocity = 100;
    /**
     * 延迟播放时间 (0-5000)，决定了音符将在接受到红石信号的何时开始播放。
     */
    private int delayedPlayingTime = 0;
    /**
     * 淡入播放时间 (0-?)，决定音符的淡入。
     */
    private int fadeInTime = 0;
    /**
     * 淡出播放时间 (0-?)，决定音符淡出。
     */
    private int fadeOutTime = 0;

    // ============== Advanced Features v1.4.0 ==============
    /**
     * 弯音轮数据 (Pitch Bend Points)。
     * 存储一系列音高偏移值（单位：音分 cents，1 半音 = 100 cents）。
     * 这些值会在声音持续时间内进行插值。
     * 如果为空或 null，则使用基础 note 值。
     */
    private List<CurvePoint> pitchBendPoints = new ArrayList<>();

    /**
     * 音量曲线数据 (Volume Points)。
     * 存储一系列音量值 (0.0 - 2.0)，用于覆盖基础 velocity 计算的音量。
     * 这些值会在声音持续时间内进行插值。
     * 如果为空或 null，则使用基础 velocity 值。
     */
    private List<CurvePoint> volumePoints = new ArrayList<>();

    /**
     * 声源移动路径 (Sound Path)。
     * 存储一系列相对位置偏移量 (相对于音符盒位置)。
     * 声音位置会在持续时间内在这些点之间进行插值。
     * 如果为空或 null，则声音位置固定在音符盒位置。
     */
    private List<Vec3d> soundPath = new ArrayList<>();

    /**
     * 存储数学表达式X(t)的字符串，仅用于在NBT中保存表达式本身
     */
    private String storedExpressionX = "";

    /**
     * 存储数学表达式Y(t)的字符串，仅用于在NBT中保存表达式本身
     */
    private String storedExpressionY = "";

    /**
     * 存储数学表达式Z(t)的字符串，仅用于在NBT中保存表达式本身
     */
    private String storedExpressionZ = "";

    /**
     * 标记是否启用高级功能。这简化了兼容性检查。
     * 如果任何高级曲线数据非空，则自动设置为 true。
     */
    private boolean advancedModeEnabled = false;

    // [新增] 用于跟踪延迟播放任务，以便在需要时可以取消它。
    // transient 关键字确保它不会被序列化到NBT中。
    @Nullable
    private transient ScheduledFuture<?> scheduledSoundFuture;

    public ExtendedNoteBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXTENDED_NOTE_BLOCK_ENTITY, pos, state);
    }

    /**
     * 属性委托，用于在服务器和客户端之间同步整数数据，供 ScreenHandler 使用。
     *
     * 索引映射:
     * - 0: note (音高)
     * - 1: velocity (力度)
     * - 2: sustainTime (持续时间)
     * - 3: instrumentId (乐器ID, 只读)
     */
    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> ExtendedNoteBlockEntity.this.note;
                case 1 -> ExtendedNoteBlockEntity.this.velocity;
                case 2 -> ExtendedNoteBlockEntity.this.sustainTime;
                case 3 -> ExtendedNoteBlockEntity.this.delayedPlayingTime;
                case 4 -> ExtendedNoteBlockEntity.this.fadeInTime;
                case 5 -> ExtendedNoteBlockEntity.this.fadeOutTime;
                case 6 -> ExtendedNoteBlockEntity.this.getInstrumentId();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> ExtendedNoteBlockEntity.this.note = value;
                case 1 -> ExtendedNoteBlockEntity.this.velocity = value;
                case 2 -> ExtendedNoteBlockEntity.this.sustainTime = value;
                case 3 -> ExtendedNoteBlockEntity.this.delayedPlayingTime = value;
                case 4 -> ExtendedNoteBlockEntity.this.fadeInTime = value;
                case 5 -> ExtendedNoteBlockEntity.this.fadeOutTime = value;
            }
            ExtendedNoteBlockEntity.this.markDirty();
        }

        @Override
        public int size() {
            return 7;
        }
    };

    // [新增] 设置并管理延迟声音任务
    public void setScheduledFuture(@Nullable ScheduledFuture<?> future) {
        // 在设置新的任务之前，先确保取消任何旧的、还未完成的任务
        cancelScheduledSound();
        this.scheduledSoundFuture = future;
    }

    // [新增] 取消当前预定的声音播放任务
    public void cancelScheduledSound() {
        if (this.scheduledSoundFuture != null && !this.scheduledSoundFuture.isDone()) {
            // false 表示不中断正在执行的任务，对于调度器来说这通常是正确的选择
            this.scheduledSoundFuture.cancel(false);
            this.scheduledSoundFuture = null;
        }
    }

    /**
     * 将方块实体的数据写入 NBT 标签，用于世界保存。
     *
     * @param nbt 要写入的 NBT 化合物。
     */
    @Override
    protected void writeNbt(NbtCompound nbt) {
        // Legacy data - always written for backward compatibility
        nbt.putInt("note", note);
        nbt.putInt("sustainTime", sustainTime);
        nbt.putInt("velocity", velocity);
        nbt.putInt("delayedPlayingTime", delayedPlayingTime);
        nbt.putInt("fadeInTime", fadeInTime);
        nbt.putInt("fadeOutTime", fadeOutTime);

        // Advanced Features v1.4.0
        NbtCompound advancedData = new NbtCompound();

        // Write Pitch Bend Points
        if (!pitchBendPoints.isEmpty()) {
            NbtList list = new NbtList();
            for (CurvePoint p : pitchBendPoints) {
                NbtCompound pointTag = new NbtCompound();
                pointTag.putFloat("t", p.time);
                pointTag.putFloat("v", p.value);
                list.add(pointTag);
            }
            advancedData.put("PitchBendPoints", list);
        }

        // Write Volume Points
        if (!volumePoints.isEmpty()) {
            NbtList list = new NbtList();
            for (CurvePoint p : volumePoints) {
                NbtCompound pointTag = new NbtCompound();
                pointTag.putFloat("t", p.time);
                pointTag.putFloat("v", p.value);
                list.add(pointTag);
            }
            advancedData.put("VolumePoints", list);
        }

        // Write Sound Path
        if (!soundPath.isEmpty()) {
            NbtList pathList = new NbtList();
            for (Vec3d pos : soundPath) {
                NbtCompound posNbt = new NbtCompound();
                posNbt.putDouble("x", pos.x);
                posNbt.putDouble("y", pos.y);
                posNbt.putDouble("z", pos.z);
                pathList.add(posNbt);
            }
            advancedData.put("SoundPath", pathList);
        }

        // Write stored expressions
        if (!storedExpressionX.isEmpty()) {
            advancedData.putString("ExpressionX", storedExpressionX);
        }
        if (!storedExpressionY.isEmpty()) {
            advancedData.putString("ExpressionY", storedExpressionY);
        }
        if (!storedExpressionZ.isEmpty()) {
            advancedData.putString("ExpressionZ", storedExpressionZ);
        }

        // Only save AdvancedData if it contains something
        if (!advancedData.isEmpty()) {
            nbt.put("AdvancedData", advancedData);
        }

        super.writeNbt(nbt);
    }

    /**
     * 从 NBT 标签中读取数据，用于从存档加载方块实体。
     *
     * @param nbt 包含数据的 NBT 化合物。
     */
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        // Read legacy data
        this.note = nbt.getInt("note");
        this.sustainTime = nbt.getInt("sustainTime");
        this.velocity = nbt.getInt("velocity");
        this.delayedPlayingTime = nbt.getInt("delayedPlayingTime");
        this.fadeInTime = nbt.getInt("fadeInTime");
        this.fadeOutTime = nbt.getInt("fadeOutTime");

        // Read Advanced Features v1.4.0
        this.pitchBendPoints.clear();
        this.volumePoints.clear();
        this.soundPath.clear();
        this.storedExpressionX = "";
        this.storedExpressionY = "";
        this.storedExpressionZ = "";
        this.advancedModeEnabled = false;

        if (nbt.contains("AdvancedData")) {
            NbtCompound advancedData = nbt.getCompound("AdvancedData");

            // Read Pitch Bend Points
            if (advancedData.contains("PitchBendPoints")) {
                NbtList list = advancedData.getList("PitchBendPoints", 10); // 10 = Compound
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound p = list.getCompound(i);
                    this.pitchBendPoints.add(new CurvePoint(p.getFloat("t"), p.getFloat("v")));
                }
            }
            
            // Read Volume Points
            if (advancedData.contains("VolumePoints")) {
                NbtList list = advancedData.getList("VolumePoints", 10);
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound p = list.getCompound(i);
                    this.volumePoints.add(new CurvePoint(p.getFloat("t"), p.getFloat("v")));
                }
            }

            // Read Sound Path
            if (advancedData.contains("SoundPath")) {
                NbtList pathList = advancedData.getList("SoundPath", 10); // 10 = Compound type
                for (int i = 0; i < pathList.size(); i++) {
                    NbtCompound posNbt = pathList.getCompound(i);
                    double x = posNbt.getDouble("x");
                    double y = posNbt.getDouble("y");
                    double z = posNbt.getDouble("z");
                    this.soundPath.add(new Vec3d(x, y, z));
                }
            }

            // Read stored expressions
            if (advancedData.contains("ExpressionX")) {
                this.storedExpressionX = advancedData.getString("ExpressionX");
            }
            if (advancedData.contains("ExpressionY")) {
                this.storedExpressionY = advancedData.getString("ExpressionY");
            }
            if (advancedData.contains("ExpressionZ")) {
                this.storedExpressionZ = advancedData.getString("ExpressionZ");
            }

            // Enable advanced mode if any advanced data exists
            this.advancedModeEnabled = !this.pitchBendPoints.isEmpty() ||
                    !this.volumePoints.isEmpty() ||
                    !this.soundPath.isEmpty();
        }
    }

    /**
     * 创建一个数据同步数据包，当方块在客户端上需要更新时调用 (例如通过 {@code world.updateListeners})。
     *
     * @return 用于更新客户端方块实体的 S2C 数据包。
     */
    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    /**
     * 获取初始区块加载时发送到客户端的 NBT 数据。
     * 这确保了当玩家进入一个新区块时，方块实体的数据能正确同步。
     *
     * @return 包含初始数据的 NBT 化合物。
     */
    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    /**
     * 获取当前设置的 MIDI 音高。
     *
     * @return 音高值 (0-127)。
     */
    public int getNote() {
        return this.note;
    }

    /**
     * 获取当前设置的音符持续时间。
     *
     * @return 持续时间 (游戏刻)。
     */
    public int getSustain() {
        return this.sustainTime;
    }

    /**
     * 获取当前设置的 MIDI 力度。
     *
     * @return 力度值 (0-127)。
     */
    public int getVelocity() {
        return this.velocity;
    }

    /**
     * 根据音符盒下方的方块动态获取乐器ID。
     *
     * @return GM 乐器ID (0-128)。如果下方没有对应乐器或世界未加载，则返回0 (钢琴)。
     */
    public int getInstrumentId() {
        if (this.world != null) {
            Block blockBelow = this.world.getBlockState(pos.down()).getBlock();
            String blockId = Registries.BLOCK.getId(blockBelow).toString();
            // 从映射中查找乐器ID，如果找不到则使用默认值0。
            return InstrumentMap.BLOCK_TO_GM_INSTRUMENT.getOrDefault(blockId, 0);
        }
        return 0;
    }

    /**
     * 获取当前设置的延迟播放时间。
     *
     * @return 延迟时间 (毫秒)。
     */
    public int getDelayedPlayingTime() {
        return this.delayedPlayingTime;
    }

    /**
     * 获取当前设置的淡入时间。
     *
     * @return 淡入时间 (刻)。
     */
    public int getFadeInTime() {
        return this.fadeInTime;
    }

    /**
     * 获取当前设置的淡出时间。
     *
     * @return 淡出时间 (刻)。
     */
    public int getFadeOutTime() {
        return this.fadeOutTime;
    }

    // ============== Advanced Features Getters v1.4.0 ==============

    /**
     * 获取弯音轮关键点数据。
     *
     * @return 弯音轮关键点列表，可能为空列表。
     */
    public List<CurvePoint> getPitchBendPoints() {
        return this.pitchBendPoints;
    }

    /**
     * 获取音量关键点数据。
     *
     * @return 音量关键点列表，可能为空列表。
     */
    public List<CurvePoint> getVolumePoints() {
        return this.volumePoints;
    }

    /**
     * 获取声源移动路径数据。
     *
     * @return 声源移动路径 (相对位置偏移列表)，可能为空列表。
     */
    public List<Vec3d> getSoundPath() {
        return this.soundPath;
    }

    /**
     * 获取存储的X轴表达式字符串
     *
     * @return X轴表达式字符串，可能为空字符串
     */
    public String getStoredExpressionX() {
        return this.storedExpressionX;
    }

    /**
     * 获取存储的Y轴表达式字符串
     *
     * @return Y轴表达式字符串，可能为空字符串
     */
    public String getStoredExpressionY() {
        return this.storedExpressionY;
    }

    /**
     * 获取存储的Z轴表达式字符串
     *
     * @return Z轴表达式字符串，可能为空字符串
     */
    public String getStoredExpressionZ() {
        return this.storedExpressionZ;
    }

    /**
     * 检查是否启用了高级模式。
     *
     * @return 如果任何高级曲线数据非空则返回 true。
     */
    public boolean isAdvancedModeEnabled() {
        return this.advancedModeEnabled;
    }

    /**
     * 设置弯音轮关键点数据。
     *
     * @param points 新的弯音轮关键点。
     */
    public void setPitchBendPoints(List<CurvePoint> points) {
        this.pitchBendPoints = new ArrayList<>(points);
        updateAdvancedModeStatus();
        markDirty();
    }

    /**
     * 设置音量关键点数据。
     *
     * @param points 新的音量关键点。
     */
    public void setVolumePoints(List<CurvePoint> points) {
        this.volumePoints = new ArrayList<>(points);
        updateAdvancedModeStatus();
        markDirty();
    }

    /**
     * 设置声源移动路径数据。
     *
     * @param path 新的声源移动路径。
     */
    public void setSoundPath(List<Vec3d> path) {
        this.soundPath = new ArrayList<>(path);
        updateAdvancedModeStatus();
        markDirty();
    }

    /**
     * 设置存储的X轴表达式字符串
     *
     * @param expression X轴表达式字符串
     */
    public void setStoredExpressionX(String expression) {
        this.storedExpressionX = expression;
        markDirty();
    }

    /**
     * 设置存储的Y轴表达式字符串
     *
     * @param expression Y轴表达式字符串
     */
    public void setStoredExpressionY(String expression) {
        this.storedExpressionY = expression;
        markDirty();
    }

    /**
     * 设置存储的Z轴表达式字符串
     *
     * @param expression Z轴表达式字符串
     */
    public void setStoredExpressionZ(String expression) {
        this.storedExpressionZ = expression;
        markDirty();
    }

    /**
     * 更新高级模式状态。
     */
    private void updateAdvancedModeStatus() {
        // 只要有自定义点（除了默认的首尾点），就视为高级模式
        // 这里简单判断非空即可，具体业务逻辑可根据需求调整
        this.advancedModeEnabled = !this.pitchBendPoints.isEmpty() ||
                !this.volumePoints.isEmpty() ||
                !this.soundPath.isEmpty();
    }

    /**
     * 从服务器更新方块实体的数值，通常由数据包调用。
     * 会对输入值进行范围检查，确保它们在有效范围内。
     *
     * @param note     新的音高 (0-127)。
     * @param velocity 新的力度 (0-127)。
     * @param sustain  新的持续时间 (0-400)。
     * @param delay    新的延迟时间 (0-5000)。
     */
    public void updateValues(int note, int velocity, int sustain, int delay, int fadeIn, int fadeOut) {
        this.note = Math.max(0, Math.min(127, note));
        this.velocity = Math.max(0, Math.min(127, velocity));
        this.sustainTime = Math.max(0, Math.min(400, sustain));
        this.delayedPlayingTime = Math.max(0, Math.min(5000, delay));
        this.fadeInTime = Math.max(0, Math.min(5000, fadeIn));
        this.fadeOutTime = Math.max(0, Math.min(5000, fadeOut));
        markDirty();
        if (world != null && !world.isClient()) {
            BlockState currentState = world.getBlockState(pos);
            NotePitch newPitch = NotePitch.fromMidiNote(this.note);
            if (currentState.getBlock() instanceof ExtendedNoteBlockBlock
                    && currentState.get(ExtendedNoteBlockBlock.PITCH) != newPitch) {
                // 更新 PITCH 属性
                world.setBlockState(pos, currentState.with(ExtendedNoteBlockBlock.PITCH, newPitch),
                        Block.NOTIFY_LISTENERS);
            }
        }
    }

    /**
     * 标记方块实体为"脏数据"，这会导致它被保存到磁盘，
     * 并通过调用 {@code world.updateListeners} 将更新同步到客户端。
     */
    @Override
    public void markDirty() {
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
        super.markDirty();
    }

    /**
     * 获取在 GUI 界面中显示的标题。
     *
     * @return GUI 标题的文本。
     */
    @Override
    public Text getDisplayName() {
        return Text.translatable("gui.extendednoteblock.title");
    }

    /**
     * 当玩家与方块交互以打开 GUI 时，在服务器端创建 ScreenHandler 实例。
     *
     * @param syncId          窗口同步ID。
     * @param playerInventory 玩家物品栏。
     * @param player          交互的玩家。
     * @return 新的 {@link ExtendedNoteBlockScreenHandler} 实例。
     */
    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new ExtendedNoteBlockScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }

    /**
     * (由 ExtendedScreenHandlerFactory 接口要求)
     * 在打开 GUI 屏幕之前，向客户端写入额外的数据。
     *
     * 这里我们将方块实体的所有重要数据写入缓冲区，
     * 客户端的 ScreenHandler 构造函数会读取这些数据，确保 GUI 初始状态正确。
     *
     * @param player 打开 GUI 的玩家。
     * @param buf    要写入的网络数据包缓冲区。
     */
    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.note);
        buf.writeInt(this.velocity);
        buf.writeInt(this.sustainTime);
        buf.writeInt(this.delayedPlayingTime);
        buf.writeInt(this.fadeInTime);
        buf.writeInt(this.fadeOutTime);
        buf.writeInt(this.getInstrumentId());
        
        // ============== Advanced Features v1.4.0 ==============
        // Write advanced settings data
        // 写入弯音点
        buf.writeInt(this.pitchBendPoints.size());
        for (CurvePoint p : this.pitchBendPoints) {
            buf.writeFloat(p.time);
            buf.writeFloat(p.value);
        }
        
        // 写入音量点
        buf.writeInt(this.volumePoints.size());
        for (CurvePoint p : this.volumePoints) {
            buf.writeFloat(p.time);
            buf.writeFloat(p.value);
        }
        
        buf.writeInt(this.soundPath.size());
        for (Vec3d vec : this.soundPath) {
            buf.writeDouble(vec.x);
            buf.writeDouble(vec.y);
            buf.writeDouble(vec.z);
        }
        
        // Write stored expressions
        buf.writeString(this.storedExpressionX);
        buf.writeString(this.storedExpressionY);
        buf.writeString(this.storedExpressionZ);
    }
}