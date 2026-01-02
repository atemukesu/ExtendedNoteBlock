package com.atemukesu.extendednoteblock;

import com.atemukesu.extendednoteblock.item.ConductorWandItem;
import com.atemukesu.extendednoteblock.network.ModMessages;
import com.atemukesu.extendednoteblock.screen.ModScreenHandlers;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import com.atemukesu.extendednoteblock.client.gui.screen.ExtendedNoteBlockScreen;
import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.atemukesu.extendednoteblock.network.ClientModMessages;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ExtendedNoteBlockClient implements ClientModInitializer {

    private static KeyBinding openWandGuiKey;
    private static KeyBinding clearSelectionKey;

    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.EXTENDED_NOTE_BLOCK_SCREEN_HANDLER, ExtendedNoteBlockScreen::new);
        ConfigManager.initialize();
        SoundPackManager.getInstance().scanPacks();
        ClientModMessages.registerS2CPackets();

        // Register KeyBindings
        openWandGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.extendednoteblock.open_wand_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_ENTER,
                "category.extendednoteblock"));

        clearSelectionKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.extendednoteblock.clear_selection",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_BACKSPACE,
                "category.extendednoteblock"));

        // Register Client Tick Event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null)
                return;

            while (openWandGuiKey.wasPressed()) {
                if (client.player.getMainHandStack().getItem() instanceof ConductorWandItem) {
                    NbtCompound nbt = client.player.getMainHandStack().getNbt();
                    if (nbt != null && nbt.contains("Pos1") && nbt.contains("Pos2")) {
                        BlockPos p1 = NbtHelper.toBlockPos(nbt.getCompound("Pos1"));
                        BlockPos p2 = NbtHelper.toBlockPos(nbt.getCompound("Pos2"));
                        ClientModMessages.sendScanRequestToServer(p1, p2);
                    } else {
                        client.player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.incomplete"),
                                true);
                    }
                }
            }

            while (clearSelectionKey.wasPressed()) {
                if (client.player.getMainHandStack().getItem() instanceof ConductorWandItem) {
                    // 1. Send Clear Packet to Server
                    ClientModMessages.sendClearSelectionToServer();

                    // 2. Clear Client Side NBT for instant visual feedback
                    NbtCompound nbt = client.player.getMainHandStack().getOrCreateNbt();
                    nbt.remove("Pos1");
                    nbt.remove("Pos2");
                    client.player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.selection_cleared"),
                            true);
                }
            }
        });

        // Register attack block callback for ConductorWand
        AttackBlockCallback.EVENT.register(this::onAttackBlock);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.getStackInHand(hand).getItem() instanceof ConductorWandItem) {
                // 修改为 PASS，让 Item 类的 useOnBlock 方法能够执行
                // Item.useOnBlock 返回 SUCCESS 会自动阻止方块的默认交互（如打开 NoteBlock GUI）
                return ActionResult.PASS;
            }
            return ActionResult.PASS;
        });

        // Register visualizer
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST
                .register(com.atemukesu.extendednoteblock.client.renderer.ConductorWandRenderer::onLastRender);

    }

    private ActionResult onAttackBlock(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
        ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() instanceof ConductorWandItem) {
            if (world.isClient) {
                // 发包给服务端设置 Pos1
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(1); // 1 代表 Pos1
                buf.writeBlockPos(pos);
                ClientPlayNetworking.send(ModMessages.SET_WAND_POS_ID, buf);
            }
            // 返回 SUCCESS 会阻止方块被挖掘
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

}