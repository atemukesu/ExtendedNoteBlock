package com.atemukesu.extendednoteblock.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.client.MinecraftClient;
import java.io.File;
import java.nio.file.Files;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import com.atemukesu.extendednoteblock.sound.SoundPackInfo;
import com.atemukesu.extendednoteblock.sound.SoundPackManager;
import com.atemukesu.extendednoteblock.util.SoundfontRenderer;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MOD_ID = ExtendedNoteBlock.MOD_ID;
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID + " Config");
    private static File configFile;
    private static ModConfig config;

    /**
     * 初始化配置管理器。
     * <p>
     * 定位配置文件。如果文件存在，则从中加载设置。
     * 如果文件不存在，则使用默认值创建一个新的配置文件。
     * 此方法应在模组启动时调用一次。
     */
    public static void initialize() {
        File configDir = FabricLoader.getInstance().getConfigDir().toFile();
        configFile = new File(configDir, MOD_ID + ".json");
        if (configFile.exists()) {
            loadConfig();
        } else {
            LOGGER.info("Config file not found, creating a new one.");
            config = new ModConfig();
            saveConfig();
        }
    }

    private static void loadConfig() {
        try (FileReader reader = new FileReader(configFile)) {
            config = GSON.fromJson(reader, ModConfig.class);
            if (config == null) {
                throw new IOException("Config file is empty or corrupted.");
            }
            LOGGER.info("Successfully loaded config file.");
            saveConfig();
        } catch (IOException e) {
            LOGGER.error("Failed to load config file, using default values.", e);
            config = new ModConfig();
            saveConfig();
        }
    }

    /**
     * 将当前配置保存到文件。
     * <p>
     * 将当前的 {@link ModConfig} 对象序列化为 JSON 格式，并将其写入配置文件，
     * 覆盖任何现有内容。这对于持久化在运行时所做的更改非常有用。
     */
    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config file.", e);
        }
    }

    /**
     * 获取已加载的配置对象。
     * <p>
     * 返回当前的 {@link ModConfig} 实例。如果配置尚未初始化，
     * 此方法将首先触发初始化过程。
     *
     * @return 包含所有配置设置的单例 {@link ModConfig} 实例。
     */
    public static ModConfig getConfig() {
        if (config == null) {
            initialize();
        }
        return config;
    }

    /**
     * 检查声音资源包是否已完全生成。
     * <p>
     * 通过检查主资源包目录、{@code pack.mcmeta} 文件、声音目录以及
     * 基于 {@link SoundfontRenderer} 的所有预期声音文件的存在性，来验证生成的资源包的完整性。
     *
     * @return 如果资源包及其所有必需的声音文件都存在，则返回 {@code true}，否则返回 {@code false}。
     */
    public static boolean isSoundPackGenerated() {
        try {
            Path resourcePackPath = MinecraftClient.getInstance().runDirectory.toPath()
                    .resolve("resourcepacks/ExtendedNoteBlockSounds");
            if (!Files.isDirectory(resourcePackPath))
                return false;
            if (!Files.isRegularFile(resourcePackPath.resolve("pack.mcmeta")))
                return false;
            List<String> expectedFiles = SoundfontRenderer.getExpectedSoundFiles();
            if (expectedFiles.isEmpty()) {
                return true;
            }
            Path soundsPath = resourcePackPath.resolve("assets").resolve(SoundfontRenderer.MOD_ID).resolve("sounds")
                    .resolve("notes");
            if (!Files.isDirectory(soundsPath))
                return false;
            for (String fileName : expectedFiles) {
                if (!Files.isRegularFile(soundsPath.resolve(fileName))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查当前激活的声音包是否已准备好使用。
     * <p>
     * 扫描可用的声音包，然后检查在配置中标记为激活的那个包的状态。
     * 如果一个包存在并且其状态为 {@link SoundPackInfo.Status#OK}，则认为该包已“准备好”。
     *
     * @return 如果存在一个激活的声音包且其状态为 {@code OK}，则返回 {@code true}，否则返回 {@code false}。
     */
    public static boolean isActiveSoundPackReady() {
        SoundPackManager manager = SoundPackManager.getInstance();
        manager.scanPacks();
        SoundPackInfo activePack = manager.getActivePackInfo();
        if (activePack == null) {
            return false;
        }
        return activePack.status() == SoundPackInfo.Status.OK;
    }
}