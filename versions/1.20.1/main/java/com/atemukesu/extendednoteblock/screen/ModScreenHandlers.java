package com.atemukesu.extendednoteblock.screen;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreenHandlers {
    public static final ScreenHandlerType<ExtendedNoteBlockScreenHandler> EXTENDED_NOTE_BLOCK_SCREEN_HANDLER = Registry
            .register(Registries.SCREEN_HANDLER, new Identifier(ExtendedNoteBlock.MOD_ID, "extended_note_block"),
                    new ExtendedScreenHandlerType<>(ExtendedNoteBlockScreenHandler::new));

    public static void registerScreenHandlers() {
        ExtendedNoteBlock.LOGGER.info("Registering Screen Handlers for " + ExtendedNoteBlock.MOD_ID);
    }
}