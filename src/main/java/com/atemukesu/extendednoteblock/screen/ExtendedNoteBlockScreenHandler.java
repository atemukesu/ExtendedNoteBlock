package com.atemukesu.extendednoteblock.screen;

import com.atemukesu.extendednoteblock.block.ExtendedNoteBlockBlock;
import com.atemukesu.extendednoteblock.block.entity.ExtendedNoteBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Block;

public class ExtendedNoteBlockScreenHandler extends ScreenHandler {
    public final ExtendedNoteBlockEntity blockEntity;
    private final PropertyDelegate propertyDelegate;
    public final BlockPos blockPos;

    public ExtendedNoteBlockScreenHandler(int syncId, PlayerInventory inventory, ExtendedNoteBlockEntity entity,
            PropertyDelegate delegate) {
        super(ModScreenHandlers.EXTENDED_NOTE_BLOCK_SCREEN_HANDLER, syncId);
        this.blockEntity = entity;
        this.propertyDelegate = delegate;
        this.blockPos = entity.getPos();
        addProperties(delegate);
    }

    public ExtendedNoteBlockScreenHandler(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
        this(syncId, inventory,
                (ExtendedNoteBlockEntity) inventory.player.getWorld().getBlockEntity(buf.readBlockPos()),
                new ArrayPropertyDelegate(4));
        this.propertyDelegate.set(0, buf.readInt());
        this.propertyDelegate.set(1, buf.readInt());
        this.propertyDelegate.set(2, buf.readInt());
        this.propertyDelegate.set(3, buf.readInt());
    }

    public int getNote() {
        return this.propertyDelegate.get(0);
    }

    public int getVelocity() {
        return this.propertyDelegate.get(1);
    }

    public int getSustain() {
        return this.propertyDelegate.get(2);
    }

    public int getInstrumentId() {
        return this.propertyDelegate.get(3);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.blockEntity.getWorld().getBlockEntity(this.blockPos) == this.blockEntity &&
                player.squaredDistanceTo(this.blockPos.toCenterPos()) < 64.0;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!this.blockEntity.getWorld().isClient()) {
            Block block = this.blockEntity.getCachedState().getBlock();
            if (block instanceof ExtendedNoteBlockBlock noteBlock) {
                noteBlock.previewNote(this.blockEntity.getWorld(), this.blockPos);
            }
        }
    }
}