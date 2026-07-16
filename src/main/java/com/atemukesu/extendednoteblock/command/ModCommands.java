package com.atemukesu.extendednoteblock.command;

import com.atemukesu.extendednoteblock.mixin.CommandNodeAccessor;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;

public class ModCommands {
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SmoothMoveCommand.register(dispatcher);

            // 修改原版 /tick 命令权限，从 level 3 降到 level 2
            // 使得命令方块（level 2）也能使用 /tick 指令
            var tickNode = dispatcher.getRoot().getChild("tick");
            if (tickNode != null) {
                @SuppressWarnings("unchecked")
                var accessor = (CommandNodeAccessor<ServerCommandSource>) tickNode;
                accessor.setRequirement(source -> source.hasPermissionLevel(2));
            }
        });
    }
}
