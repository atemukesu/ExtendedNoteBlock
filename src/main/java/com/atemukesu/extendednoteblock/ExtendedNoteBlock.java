package com.atemukesu.extendednoteblock;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atemukesu.extendednoteblock.block.ModBlocks;
import com.atemukesu.extendednoteblock.block.entity.ModBlockEntities;
import com.atemukesu.extendednoteblock.item.ModItemGroups;
import com.atemukesu.extendednoteblock.network.ModMessages;
import com.atemukesu.extendednoteblock.screen.ModScreenHandlers;
import com.atemukesu.extendednoteblock.sound.ServerSoundManager;

public class ExtendedNoteBlock implements ModInitializer {
	public static final String MOD_ID = "extendednoteblock";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		ModBlocks.registerModBlocks();
		ModItemGroups.registerItemGroups();
		ModBlockEntities.registerBlockEntities();
		ModScreenHandlers.registerScreenHandlers();
        ModMessages.registerC2SPackets();
		ServerSoundManager.initialize();
		LOGGER.info("Extended Note Block Loaded.");
	}
}