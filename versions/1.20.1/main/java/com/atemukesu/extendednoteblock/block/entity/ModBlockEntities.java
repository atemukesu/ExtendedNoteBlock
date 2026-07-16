package com.atemukesu.extendednoteblock.block.entity;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import com.atemukesu.extendednoteblock.block.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {
    public static BlockEntityType<ExtendedNoteBlockEntity> EXTENDED_NOTE_BLOCK_ENTITY;

    public static void registerBlockEntities() {
        ExtendedNoteBlock.LOGGER.info("Registering Block Entities for " + ExtendedNoteBlock.MOD_ID);
        EXTENDED_NOTE_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ExtendedNoteBlock.MOD_ID, "extended_note_block_entity"),
                FabricBlockEntityTypeBuilder.<ExtendedNoteBlockEntity>create(
                        ExtendedNoteBlockEntity::new,
                        ModBlocks.EXTENDED_NOTE_BLOCK).build());
    }
}