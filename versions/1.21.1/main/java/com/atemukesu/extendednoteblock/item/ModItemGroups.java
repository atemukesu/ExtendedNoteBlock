package com.atemukesu.extendednoteblock.item;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import com.atemukesu.extendednoteblock.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static com.atemukesu.extendednoteblock.ExtendedNoteBlock.CONDUCTOR_WAND;

public class ModItemGroups {

    public static final ItemGroup EXTENDED_NOTE_BLOCK_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(ExtendedNoteBlock.MOD_ID, "extended_note_block_group"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemgroup.extendednoteblock"))
                    .icon(() -> new ItemStack(ModBlocks.EXTENDED_NOTE_BLOCK))
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.EXTENDED_NOTE_BLOCK);
                        entries.add(ModBlocks.GLOBAL_REDSTONE_TRANSMITTER);
                        entries.add(ModBlocks.GLOBAL_REDSTONE_RECEIVER);
                        entries.add(CONDUCTOR_WAND);
                    })
                    .build());

    public static void registerItemGroups() {
        ExtendedNoteBlock.LOGGER.info("Registering Item Groups for " + ExtendedNoteBlock.MOD_ID);
    }
}