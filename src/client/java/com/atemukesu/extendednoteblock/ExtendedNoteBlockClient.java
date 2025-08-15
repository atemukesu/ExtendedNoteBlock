package com.atemukesu.extendednoteblock;

import com.atemukesu.extendednoteblock.screen.ModScreenHandlers;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

import com.atemukesu.extendednoteblock.client.gui.screen.ExtendedNoteBlockScreen;
import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.atemukesu.extendednoteblock.network.ClientModMessages;

public class ExtendedNoteBlockClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModScreenHandlers.EXTENDED_NOTE_BLOCK_SCREEN_HANDLER, ExtendedNoteBlockScreen::new);
		ConfigManager.initialize();
		SoundPackManager.getInstance().scanPacks();
		ClientModMessages.registerS2CPackets();
	}
}