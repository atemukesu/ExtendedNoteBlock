package com.atemukesu.extendednoteblock.sound;

import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.atemukesu.extendednoteblock.util.SoundfontRenderer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Set;
import java.util.Objects;

/**
 * 声音包管理器（Sound Pack Manager）。
 * <p>
 * 这是一个单例类，负责管理模组所有与声音包相关的操作。
 * 主要职责包括：
 * <ul>
 * <li>扫描、加载和解析 Minecraft 资源包目录中的声音包（包括.zip和文件夹格式）。</li>
 * <li>管理音源文件（.sf2），并确保默认音源可用。</li>
 * <li>创建新的声音包结构。</li>
 * <li>设置、激活和停用声音包，并与 Minecraft 的 {@link ResourcePackManager} 交互。</li>
 * <li>检查声音包的完整性状态（例如，是否已渲染、音源是否缺失等）。</li>
 * </ul>
 */
public class SoundPackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SoundPackManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /**
     * 存放 .sf2 音源文件的目录名称。
     */
    public static final String SOURCES_DIR_NAME = "extendednoteblock_sources";
    /**
     * 每个声音包中用于描述元数据的配置文件名。
     */
    private static final String PACK_CONFIG_FILE = "pack.json";
    /**
     * Minecraft 资源包管理器中用于标识文件系统资源包的前缀。
     */
    private static final String RESOURCE_PACK_PREFIX = "file/";
    /**
     * 默认声音包的唯一ID。
     */
    public static final String DEFAULT_PACK_ID = "extendednoteblock_default";
    /**
     * 默认音源文件的名称。
     */
    public static final String DEFAULT_SF2_NAME = "default.sf2";

    /**
     * 内存中缓存的可用声音包信息列表。
     */
    private final List<SoundPackInfo> availablePacks = new ArrayList<>();
    /**
     * 当前激活的声音包ID。该值从配置文件中读取和保存。
     */
    private String activePackId = null;

    /**
     * SoundPackManager 的单例实例。
     */
    private static final SoundPackManager INSTANCE = new SoundPackManager();

    /**
     * 私有构造函数，防止外部实例化。
     */
    private SoundPackManager() {
    }

    /**
     * 获取 SoundPackManager 的唯一实例。
     *
     * @return SoundPackManager 的单例实例。
     */
    public static SoundPackManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取 Minecraft 的资源包目录路径。
     *
     * @return 资源包目录的 {@link Path} 对象。
     */
    public Path getPacksDirectory() {
        return MinecraftClient.getInstance().getResourcePackDir();
    }

    /**
     * 获取存放 .sf2 音源文件的自定义目录路径。
     *
     * @return 音源目录的 {@link Path} 对象。
     */
    public Path getSourcesDirectory() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve(SOURCES_DIR_NAME);
    }

    /**
     * 扫描并加载所有可用的声音包。
     * 此方法会清空现有列表，然后重新扫描资源包目录和音源目录。
     * 它还会确保默认的音源和声音包结构存在。
     */
    public void scanPacks() {
        this.availablePacks.clear();
        Path packsDir = getPacksDirectory();
        Path sourcesDir = getSourcesDirectory();
        createDirectoryIfNotExists(packsDir);
        createDirectoryIfNotExists(sourcesDir);
        ensureDefaultSourceIsAvailable(); // 确保默认sf2文件存在

        try (Stream<Path> stream = Files.list(packsDir)) {
            stream.forEach(packPath -> {
                if (Files.isDirectory(packPath)) {
                    loadPackFromDirectory(packPath, sourcesDir);
                } else if (packPath.toString().toLowerCase().endsWith(".zip")) {
                    loadPackFromZip(packPath, sourcesDir);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to scan for sound packs in {}", packsDir, e);
        }

        ensureDefaultPackStructureExists(); // 确保默认声音包结构存在
        this.activePackId = ConfigManager.getConfig().activeSoundPackId;
    }

    /**
     * 从一个文件夹加载声音包信息。
     *
     * @param packDir    声音包的目录路径。
     * @param sourcesDir 音源文件目录路径，用于定位关联的 .sf2 文件。
     */
    private void loadPackFromDirectory(Path packDir, Path sourcesDir) {
        Path packConfigFile = packDir.resolve(PACK_CONFIG_FILE);
        if (!Files.exists(packConfigFile))
            return;
        try (FileReader reader = new FileReader(packConfigFile.toFile())) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String id = packDir.getFileName().toString();
            addPackInfo(id, packDir, json, sourcesDir);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            LOGGER.warn("Failed to load sound pack from directory {}", packDir, e);
        }
    }

    /**
     * 从一个 .zip 压缩文件加载声音包信息。
     *
     * @param zipPath    声音包的 .zip 文件路径。
     * @param sourcesDir 音源文件目录路径，用于定位关联的 .sf2 文件。
     */
    private void loadPackFromZip(Path zipPath, Path sourcesDir) {
        try (FileSystem fs = FileSystems.newFileSystem(zipPath, (ClassLoader) null)) {
            Path packConfigFile = fs.getPath(PACK_CONFIG_FILE);
            if (!Files.exists(packConfigFile))
                return;
            try (BufferedReader reader = Files.newBufferedReader(packConfigFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String fileName = zipPath.getFileName().toString();
                String id = fileName.substring(0, fileName.length() - 4); // 移除 .zip 后缀
                addPackInfo(id, zipPath, json, sourcesDir);
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            LOGGER.warn("Failed to load sound pack from zip {}", zipPath, e);
        }
    }

    /**
     * 根据解析的信息，创建一个 {@link SoundPackInfo} 对象并添加到可用列表中。
     *
     * @param id         声音包的唯一ID。
     * @param packPath   声音包的路径（文件夹或.zip）。
     * @param json       从 pack.json 解析出的 JsonObject。
     * @param sourcesDir 音源文件目录路径。
     */
    private void addPackInfo(String id, Path packPath, JsonObject json, Path sourcesDir) {
        String displayName = id.equals(DEFAULT_PACK_ID)
                ? Text.translatable("gui.extendednoteblock.pack_manager.default_pack_name").getString()
                : json.get("displayName").getAsString();
        String sourceSf2Name = json.get("sourceSf2Name").getAsString();

        boolean isFull = false;
        if (json.has("full")) {
            isFull = json.get("full").getAsBoolean();
        }

        Path sourceSf2Path = sourcesDir.resolve(sourceSf2Name);

        SoundPackInfo.Status status = checkPackCompleteness(packPath, isFull);
        if (!Files.exists(sourceSf2Path)) {
            status = SoundPackInfo.Status.SOURCE_MISSING;
        }

        // 在构造函数中传入 isFull
        availablePacks.add(new SoundPackInfo(id, displayName, packPath, sourceSf2Path, status, isFull));
        LOGGER.info("Successfully loaded sound pack: '{}' (ID: {}), Full-Render: {}", displayName, id, isFull);
    }

    /**
     * 检查一个声音包内的音频文件是否完整。
     *
     * @param packPath 声音包的路径（文件夹或.zip）。
     * @return 声音包的状态（OK, INCOMPLETE, NOT_RENDERED, ERROR）。
     */
    /**
     * 检查一个声音包内的音频文件是否完整。
     * 如果包被标记为 'full'，则直接返回 OK。
     *
     * @param packPath 声音包的路径（文件夹或.zip）。
     * @param isFull   该声音包是否为全渲染模式。
     * @return 声音包的状态（OK, INCOMPLETE, NOT_RENDERED, ERROR）。
     */
    public SoundPackInfo.Status checkPackCompleteness(Path packPath, boolean isFull) {
        // 如果 pack.json 中 "full" 为 true，我们直接信任它并返回 OK
        if (isFull) {
            return SoundPackInfo.Status.OK;
        }
        List<String> expectedFiles = SoundfontRenderer.getExpectedSoundFiles();
        String soundsBasePath = "assets/" + SoundfontRenderer.MOD_ID + "/sounds/notes/";
        try {
            if (Files.isDirectory(packPath)) {
                Path soundsDir = packPath.resolve(soundsBasePath);
                if (!Files.exists(soundsDir))
                    return SoundPackInfo.Status.NOT_RENDERED;
                for (String fileName : expectedFiles) {
                    if (!Files.exists(soundsDir.resolve(fileName))) {
                        return SoundPackInfo.Status.INCOMPLETE;
                    }
                }
            } else if (packPath.toString().toLowerCase().endsWith(".zip")) {
                try (FileSystem fs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
                    Path soundsDir = fs.getPath(soundsBasePath);
                    if (!Files.exists(soundsDir))
                        return SoundPackInfo.Status.NOT_RENDERED;
                    for (String fileName : expectedFiles) {
                        if (!Files.exists(soundsDir.resolve(fileName))) {
                            return SoundPackInfo.Status.INCOMPLETE;
                        }
                    }
                }
            }
            return SoundPackInfo.Status.OK;
        } catch (IOException e) {
            LOGGER.error("Error checking pack completeness for {}", packPath, e);
            return SoundPackInfo.Status.ERROR;
        }
    }

    /**
     * 确保默认的 .sf2 音源文件存在于音源目录中。如果不存在，则从模组 JAR 文件中提取。
     */
    private void ensureDefaultSourceIsAvailable() {
        Path defaultSourcePath = getSourcesDirectory().resolve(DEFAULT_SF2_NAME);
        if (!Files.exists(defaultSourcePath)) {
            LOGGER.info("Default source file '{}' not found in sources directory. Extracting from mod JAR...",
                    DEFAULT_SF2_NAME);
            try (InputStream is = SoundfontRenderer.class.getClassLoader()
                    .getResourceAsStream(SoundfontRenderer.DEFAULT_SF2_RESOURCE_PATH)) {
                if (is == null) {
                    LOGGER.error("Could not find the built-in default.sf2 resource in JAR!");
                    return;
                }
                Files.copy(is, defaultSourcePath);
                LOGGER.info("Successfully extracted default.sf2 to sources directory.");
            } catch (IOException e) {
                LOGGER.error("Failed to extract default.sf2", e);
            }
        }
    }

    /**
     * 确保默认的声音包结构（文件夹和pack.json）存在。如果不存在，则创建它。
     */
    private void ensureDefaultPackStructureExists() {
        boolean defaultPackExists = availablePacks.stream().anyMatch(p -> p.id().equals(DEFAULT_PACK_ID));
        if (!defaultPackExists) {
            LOGGER.info("Default sound pack structure not found. Creating...");
            String defaultPackName = Text.translatable("gui.extendednoteblock.pack_manager.default_pack_name")
                    .getString();
            Path defaultSourcePath = getSourcesDirectory().resolve(DEFAULT_SF2_NAME);
            if (!Files.exists(defaultSourcePath)) {
                LOGGER.error("Cannot create default pack structure because default source file is missing!");
                return;
            }
            createNewPackInternal(defaultPackName, DEFAULT_PACK_ID, defaultSourcePath, true);
        }
    }

    /**
     * 设置并激活指定ID的声音包。
     * <p>
     * 此方法会与 Minecraft 的资源包系统交互，将指定的声音包添加到已启用的资源包列表中，
     * 并移除其他由本模组管理的声音包。操作完成后会触发资源重载。
     * 如果传入的 packId 为 null 或空，则会停用所有本模组管理的声音包。
     * 该操作被包装在 `client.execute` 中以确保在主线程上执行。
     * </p>
     * 
     * @param packId 要激活的声音包的ID，或 null 以停用。
     */
    public void setActivePack(String packId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            LOGGER.error("Cannot set active pack: MinecraftClient is not available.");
            return;
        }

        client.execute(() -> {
            LOGGER.info("--- [setActivePack] Starting on main thread ---");
            LOGGER.info("Attempting to set active pack to ID: '{}'", packId);
            ResourcePackManager resourcePackManager = client.getResourcePackManager();
            if (resourcePackManager == null) {
                LOGGER.error("ResourcePackManager is null! Cannot proceed.");
                return;
            }

            resourcePackManager.scanPacks(); // 确保获取最新的资源包列表
            Collection<String> allAvailablePackNames = resourcePackManager.getProfiles()
                    .stream()
                    .map(ResourcePackProfile::getName)
                    .collect(Collectors.toSet());

            List<String> originalEnabledPacks = new ArrayList<>(resourcePackManager.getEnabledNames());
            LOGGER.info("Current enabled resource packs (Before): {}", originalEnabledPacks);

            // 移除所有本模组管理的包，以便稍后只添加需要激活的那个
            Set<String> allMyPackIds = getAvailablePacks().stream()
                    .map(pack -> RESOURCE_PACK_PREFIX + pack.id())
                    .collect(Collectors.toSet());
            List<String> tempList = new ArrayList<>(originalEnabledPacks);
            tempList.removeAll(allMyPackIds);

            // 重新组织资源包列表，以维持加载顺序
            List<String> vanillaPacks = new ArrayList<>();
            List<String> modPacks = new ArrayList<>();
            List<String> userPacks = new ArrayList<>();
            for (String packName : tempList) {
                if (packName.equals("vanilla") || packName.equals("programmer_art")) {
                    vanillaPacks.add(packName);
                } else if (packName.equals("fabric") || packName.startsWith("mod_resources")) {
                    modPacks.add(packName);
                } else {
                    userPacks.add(packName);
                }
            }

            this.activePackId = null;
            // 如果提供了有效的 packId，则将其添加到启用列表
            if (packId != null && !packId.isBlank()) {
                String myPackName = RESOURCE_PACK_PREFIX + packId;
                if (allAvailablePackNames.contains(myPackName) ||
                        allAvailablePackNames.contains(myPackName + ".zip")) {
                    this.activePackId = packId;
                    List<String> newEnabledPacks = new ArrayList<>();
                    newEnabledPacks.addAll(vanillaPacks);
                    newEnabledPacks.addAll(modPacks);
                    newEnabledPacks.addAll(userPacks);
                    newEnabledPacks.add(myPackName);
                    applyPackChanges(resourcePackManager, originalEnabledPacks, newEnabledPacks);
                } else {
                    LOGGER.error("Pack '{}' not found in available packs list. Aborting activation. Available: {}",
                            myPackName, allAvailablePackNames);
                }
            } else { // 否则，只保留其他资源包，实现停用
                LOGGER.info("No pack ID provided. Deactivating all managed packs.");
                List<String> newEnabledPacks = new ArrayList<>();
                newEnabledPacks.addAll(vanillaPacks);
                newEnabledPacks.addAll(modPacks);
                newEnabledPacks.addAll(userPacks);
                applyPackChanges(resourcePackManager, originalEnabledPacks, newEnabledPacks);
            }
            LOGGER.info("--- [setActivePack] Finished ---");
        });
    }

    /**
     * 应用资源包更改。如果新的列表与旧的不同，则设置新的启用配置并触发资源重载。
     *
     * @param manager 资源包管理器。
     * @param oldList 旧的已启用资源包列表。
     * @param newList 新的已启用资源包列表。
     */
    private void applyPackChanges(ResourcePackManager manager, List<String> oldList, List<String> newList) {
        if (!new HashSet<>(oldList).equals(new HashSet<>(newList))) {
            LOGGER.info("Change detected! Applying new profile: {}", newList);
            manager.setEnabledProfiles(newList);
            ConfigManager.getConfig().activeSoundPackId = this.activePackId;
            ConfigManager.saveConfig();
            LOGGER.info("Saved active pack ID '{}' to config.", this.activePackId);

            MinecraftClient.getInstance().reloadResources().whenComplete((v, throwable) -> {
                if (throwable != null) {
                    LOGGER.error("Resource reload FAILED.", throwable);
                } else {
                    LOGGER.info("Resource reload completed successfully.");
                }
            });
            LOGGER.info("Resource reload triggered.");
        } else {
            LOGGER.info("No change in resource pack list. Skipping reload.");
            if (!Objects.equals(ConfigManager.getConfig().activeSoundPackId, this.activePackId)) {
                ConfigManager.getConfig().activeSoundPackId = this.activePackId;
                ConfigManager.saveConfig();
                LOGGER.info("Pack list unchanged, but saved active pack ID '{}' to config.", this.activePackId);
            }
        }
    }

    /**
     * 根据给定的显示名称和音源文件路径创建一个新的声音包。
     *
     * @param displayName   声音包的显示名称。
     * @param sourceSf2Path 关联的 .sf2 音源文件路径。
     * @return 如果创建成功，返回新的 {@link SoundPackInfo} 对象；否则返回 null。
     */
    public SoundPackInfo createNewPack(String displayName, Path sourceSf2Path) {
        // 将显示名称转换为一个安全的文件名作为ID
        String id = displayName.replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase();
        if (availablePacks.stream().anyMatch(p -> p.id().equals(id))) {
            LOGGER.error("A pack with ID '{}' already exists.", id);
            return null;
        }
        return createNewPackInternal(displayName, id, sourceSf2Path, true);
    }

    /**
     * 创建新声音包的内部实现。
     *
     * @param displayName    显示名称。
     * @param id             唯一ID。
     * @param sourceSf2Path  音源路径。
     * @param addToAvailable 是否将新创建的包添加到内存中的可用列表。
     * @return 新的 {@link SoundPackInfo} 对象，或在失败时返回 null。
     */
    private SoundPackInfo createNewPackInternal(String displayName, String id, Path sourceSf2Path,
            boolean addToAvailable) {
        Path packDir = getPacksDirectory().resolve(id);
        try {
            Files.createDirectories(packDir);
            JsonObject json = new JsonObject();
            json.addProperty("displayName", displayName);
            json.addProperty("sourceSf2Name", sourceSf2Path.getFileName().toString());
            json.addProperty("full", false);

            try (FileWriter writer = new FileWriter(packDir.resolve(PACK_CONFIG_FILE).toFile())) {
                GSON.toJson(json, writer);
            }

            SoundPackInfo newPackInfo = new SoundPackInfo(id, displayName, packDir, sourceSf2Path,
                    SoundPackInfo.Status.NOT_RENDERED, false); // full 设置为 false
            if (addToAvailable) {
                this.availablePacks.add(newPackInfo);
            }
            return newPackInfo;
        } catch (IOException e) {
            LOGGER.error("Failed to create new pack structure for '{}'", displayName, e);
            return null;
        }
    }

    /**
     * 在音源目录中查找尚未与任何声音包关联的新 .sf2 文件。
     *
     * @return 一个包含新发现的 .sf2 文件路径的列表。
     */
    public List<Path> findNewSoundfonts() {
        Path sourcesDir = getSourcesDirectory();
        Set<String> existingSourceNames = this.availablePacks.stream()
                .map(pack -> pack.sourceSf2Path().getFileName().toString())
                .collect(Collectors.toSet());

        try (Stream<Path> stream = Files.list(sourcesDir)) {
            return stream.filter(path -> path.toString().toLowerCase().endsWith(".sf2"))
                    .filter(path -> !existingSourceNames.contains(path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            LOGGER.error("Failed to scan for new source soundfonts", e);
            return Collections.emptyList();
        }
    }

    /**
     * 如果目录不存在，则创建它。
     *
     * @param dir 要创建的目录路径。
     */
    private void createDirectoryIfNotExists(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                LOGGER.error("Failed to create directory: {}", dir, e);
            }
        }
    }

    /**
     * 获取当前激活的声音包的信息。
     *
     * @return 当前激活的 {@link SoundPackInfo}，如果没有激活的包则返回 null。
     */
    public SoundPackInfo getActivePackInfo() {
        if (activePackId == null || activePackId.isBlank())
            return null;
        return availablePacks.stream().filter(p -> p.id().equals(activePackId)).findFirst().orElse(null);
    }

    /**
     * 根据ID获取声音包信息。
     *
     * @param id 声音包的ID。
     * @return 对应的 {@link SoundPackInfo}，如果未找到则返回 null。
     */
    public SoundPackInfo getPackInfoById(String id) {
        return availablePacks.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * 获取当前激活的声音包的ID。
     *
     * @return 激活的声音包ID。
     */
    public String getActivePackId() {
        return activePackId;
    }

    /**
     * 获取所有扫描到的可用声音包列表。
     *
     * @return 一个不可修改的声音包信息列表。
     */
    public List<SoundPackInfo> getAvailablePacks() {
        return Collections.unmodifiableList(availablePacks);
    }

    /**
     * 检查当前在配置文件中标记为“活动”的声音包是否真的在 Minecraft 的已启用资源包列表中。
     * 这用于在游戏启动时进行预检查，以警告用户配置不匹配的情况。
     *
     * @return 如果活动包确实已启用，或者没有设置活动包，则返回 true。否则返回 false。
     */
    public boolean isCurrentPackActuallyEnabled() {
        String activePackId = getActivePackId();
        // 如果没有设置活动包，则认为状态是正常的
        if (activePackId == null || activePackId.isBlank()) {
            return true;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourcePackManager() == null) {
            LOGGER.warn("Cannot check if pack is enabled: ResourcePackManager not available yet.");
            return true; // 无法检查时，暂时假设为 true 以免误报
        }

        ResourcePackManager resourcePackManager = client.getResourcePackManager();
        Collection<String> enabledPacks = resourcePackManager.getEnabledNames();
        String expectedResourceName = RESOURCE_PACK_PREFIX + activePackId;
        boolean isEnabled = enabledPacks.contains(expectedResourceName);

        if (!isEnabled) {
            LOGGER.warn(
                    "Pre-launch check FAIL: Pack '{}' is set as active in config, but not enabled in Minecraft's resource packs!",
                    activePackId);
        }
        return isEnabled;
    }
}