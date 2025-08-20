package com.atemukesu.extendednoteblock.block.entity;

import com.atemukesu.extendednoteblock.map.InstrumentMap;
import com.atemukesu.extendednoteblock.screen.ExtendedNoteBlockScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
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
        nbt.putInt("note", note);
        nbt.putInt("sustainTime", sustainTime);
        nbt.putInt("velocity", velocity);
        nbt.putInt("delayedPlayingTime", delayedPlayingTime);
        nbt.putInt("fadeInTime", fadeInTime);
        nbt.putInt("fadeOutTime", fadeOutTime);
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
        this.note = nbt.getInt("note");
        this.sustainTime = nbt.getInt("sustainTime");
        this.velocity = nbt.getInt("velocity");
        this.delayedPlayingTime = nbt.getInt("delayedPlayingTime");
        this.fadeInTime = nbt.getInt("fadeInTime");
        this.fadeOutTime = nbt.getInt("fadeOutTime");
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
    }

    /**
     * 标记方块实体为“脏数据”，这会导致它被保存到磁盘，
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
    }
}