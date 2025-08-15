package com.atemukesu.extendednoteblock.block;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block EXTENDED_NOTE_BLOCK = registerBlock("extended_note_block",
            new ExtendedNoteBlockBlock(FabricBlockSettings.copyOf(Blocks.NOTE_BLOCK)
                    .luminance(state -> state.get(net.minecraft.state.property.Properties.POWERED) ? 15 : 0)));

    /**
     * 辅助方法，用于注册方块
     * 
     * @param name  方块的名称
     * @param block 方块实例
     * @return 注册后的方块
     */
    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(Registries.BLOCK, new Identifier(ExtendedNoteBlock.MOD_ID, name), block);
    }

    /**
     * 辅助方法，用于注册方块对应的物品
     * 
     * @param name  物品的名称
     * @param block 对应的方块
     */
    private static void registerBlockItem(String name, Block block) {
        Registry.register(Registries.ITEM, new Identifier(ExtendedNoteBlock.MOD_ID, name),
                new BlockItem(block, new FabricItemSettings()));
    }

    /**
     * 在主类中调用的初始化方法
     */
    public static void registerModBlocks() {
        ExtendedNoteBlock.LOGGER.info("Registering ModBlocks for " + ExtendedNoteBlock.MOD_ID);
    }
}