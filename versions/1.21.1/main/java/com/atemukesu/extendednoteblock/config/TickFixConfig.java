package com.atemukesu.extendednoteblock.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TickFixConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("extendednoteblock-tickfix.json");
    private static boolean enabled = false;

    static {
        load();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    private static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                var data = GSON.fromJson(Files.readString(CONFIG_PATH), Data.class);
                if (data != null) {
                    enabled = data.enabled;
                }
            } catch (IOException e) {
                enabled = false;
            }
        }
    }

    private static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(new Data(enabled)));
        } catch (IOException e) {
            // ignore
        }
    }

    private static class Data {
        boolean enabled;
        Data(boolean enabled) { this.enabled = enabled; }
    }
}
