package com.atemukesu.extendednoteblock.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class ModCommands {
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SmoothMoveCommand.register(dispatcher);
        });
    }
}
