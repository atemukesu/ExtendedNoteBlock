package com.atemukesu.extendednoteblock.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.atemukesu.extendednoteblock.config.TickFixConfig;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.TickCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TickCommand.class)
public class TickCommandMixin {

    @Inject(method = "register", at = @At("TAIL"))
    private static void onRegister(CommandDispatcher<ServerCommandSource> dispatcher, CallbackInfo ci) {
        if (!TickFixConfig.isEnabled()) return;

        var tickNode = dispatcher.getRoot().getChild("tick");
        if (tickNode != null) {
            @SuppressWarnings("unchecked")
            var accessor = (CommandNodeAccessor<ServerCommandSource>) tickNode;
            accessor.setRequirement(source -> source.hasPermissionLevel(2));
        }
    }
}
