package com.atemukesu.extendednoteblock.sound;

import com.atemukesu.extendednoteblock.ExtendedNoteBlock;
import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 声音包管理器（Sound Pack Manager）。
 * 识别标准：资源包的根目录下必须存在一个 `pack.json` 文件。
 * 它会自动处理 `pack.mcmeta` 的创建和 `sounds.json` 的生成。
 */
public class SoundPackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SoundPackManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String PACK_CONFIG_FILE = "pack.json";
    private static final String PACK_MCMETA_FILE = "pack.mcmeta"; // 新增常量
    private static final String SOUNDS_JSON_FILE = "sounds.json";
    private static final String RESOURCE_PACK_PREFIX = "file/";

    public static final String DEFAULT_PACK_ID = "extendednoteblock_default";
    public static final String DEFAULT_PACK_ZIP_NAME = DEFAULT_PACK_ID + ".zip";
    private static final String DEFAULT_PACK_RESOURCE_PATH = "assets/" + ExtendedNoteBlock.MOD_ID + "/"
            + DEFAULT_PACK_ZIP_NAME;

    private final List<SoundPackInfo> availablePacks = new ArrayList<>();
    private String activePackId = null;

    private static final SoundPackManager INSTANCE = new SoundPackManager();

    private SoundPackManager() {
    }

    public static SoundPackManager getInstance() {
        return INSTANCE;
    }

    public Path getPacksDirectory() {
        return MinecraftClient.getInstance().getResourcePackDir();
    }

    public void scanPacks() {
        this.availablePacks.clear();
        Path packsDir = getPacksDirectory();
        createDirectoryIfNotExists(packsDir);

        boolean defaultPackReady = ensureDefaultPackIsAvailable();

        try (Stream<Path> stream = Files.list(packsDir)) {
            stream.forEach(packPath -> {
                boolean isZip = packPath.toString().toLowerCase().endsWith(".zip");
                if (isZip || Files.isDirectory(packPath)) {
                    if (isExtendedNoteBlockPack(packPath, isZip)) {
                        loadOrUpdatePack(packPath, isZip);
                    }
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to scan for sound packs in {}", packsDir, e);
        }

        Path defaultPackPath = packsDir.resolve(DEFAULT_PACK_ZIP_NAME);
        if (defaultPackReady && availablePacks.stream().noneMatch(p -> p.id().equals(DEFAULT_PACK_ID))) {
            loadOrUpdatePack(defaultPackPath, true);
        }

        this.activePackId = ConfigManager.getConfig().activeSoundPackId;
    }

    private boolean isExtendedNoteBlockPack(Path packPath, boolean isZip) {
        if (isZip) {
            try (FileSystem fs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
                // 对于zip包，它必须同时包含 pack.mcmeta 和 pack.json
                return Files.exists(fs.getPath(PACK_MCMETA_FILE)) && Files.exists(fs.getPath(PACK_CONFIG_FILE));
            } catch (IOException e) {
                return false;
            }
        } else {
            // 对于文件夹，我们只要求 pack.json 存在，因为我们可以自动生成 pack.mcmeta
            return Files.exists(packPath.resolve(PACK_CONFIG_FILE));
        }
    }

    private boolean ensureDefaultPackIsAvailable() {
        Path defaultPackPath = getPacksDirectory().resolve(DEFAULT_PACK_ZIP_NAME);
        if (Files.exists(defaultPackPath)) {
            return true;
        }

        LOGGER.info("Default sound pack '{}' not found. Attempting to extract from mod JAR...", DEFAULT_PACK_ZIP_NAME);
        try (InputStream is = SoundPackManager.class.getClassLoader().getResourceAsStream(DEFAULT_PACK_RESOURCE_PATH)) {
            if (is == null) {
                LOGGER.error(
                        "FATAL: Default pack not found in JAR at path: {}. The mod will not function correctly without it.",
                        DEFAULT_PACK_RESOURCE_PATH);
                return false;
            }
            Files.copy(is, defaultPackPath);
            LOGGER.info("Successfully extracted default sound pack to resourcepacks folder.");
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to extract default sound pack from JAR.", e);
            return false;
        }
    }

    public void loadOrUpdatePack(Path packPath, boolean isZip) {
        String fileName = packPath.getFileName().toString();
        String id = isZip ? fileName.substring(0, fileName.length() - 4) : fileName;

        availablePacks.removeIf(p -> p.id().equals(id));

        try {
            if (isZip) {
                loadPackFromZip(packPath, id);
            } else {
                updatePackFromDirectory(packPath, id);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load or update sound pack '{}'", id, e);
            availablePacks.add(
                    new SoundPackInfo(id, id, packPath, SoundPackInfo.Status.INVALID, isZip, Collections.emptyMap()));
        }
    }

    private void loadPackFromZip(Path zipPath, String id) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(zipPath, (ClassLoader) null)) {
            Path packConfigFile = fs.getPath(PACK_CONFIG_FILE);

            JsonObject json;
            try (Reader reader = Files.newBufferedReader(packConfigFile)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }

            String displayName = json.has("displayName") ? json.get("displayName").getAsString() : id;
            if (id.equals(DEFAULT_PACK_ID)) {
                displayName = Text.translatable("gui.extendednoteblock.pack_manager.default_pack_name").getString();
            }

            Map<Integer, List<Integer>> notesMap = readNotesFromPackJson(json, id);

            SoundPackInfo.Status status = notesMap.isEmpty() ? SoundPackInfo.Status.EMPTY : SoundPackInfo.Status.OK;
            availablePacks.add(
                    new SoundPackInfo(id, displayName, zipPath, status, true, Collections.unmodifiableMap(notesMap)));
            LOGGER.info("Loaded sound pack from zip: '{}' (ID: {})", displayName, id);
        }
    }

    private void updatePackFromDirectory(Path packPath, String id) throws IOException {
        // *** 核心修改: 确保 pack.mcmeta 存在 ***
        generatePackMcMeta(packPath);

        Map<Integer, List<Integer>> foundNotes = scanOggFiles(packPath);

        Path packJsonPath = packPath.resolve(PACK_CONFIG_FILE);
        JsonObject packJson;
        try (Reader reader = new FileReader(packJsonPath.toFile())) {
            packJson = JsonParser.parseReader(reader).getAsJsonObject();
        }

        String displayName = packJson.has("displayName") ? packJson.get("displayName").getAsString() : id;
        packJson.addProperty("displayName", displayName);

        Type mapType = new TypeToken<Map<Integer, List<Integer>>>() {
        }.getType();
        JsonElement instrumentsElement = GSON.toJsonTree(foundNotes, mapType);
        packJson.add("available_instruments", instrumentsElement);

        JsonArray flatNotesArray = new JsonArray();
        foundNotes.values().stream().flatMap(List::stream).distinct().sorted().forEach(flatNotesArray::add);
        packJson.add("available_notes", flatNotesArray);

        try (Writer writer = new FileWriter(packJsonPath.toFile())) {
            GSON.toJson(packJson, writer);
        }

        generateSoundsJson(packPath, foundNotes);

        SoundPackInfo.Status status = foundNotes.isEmpty() ? SoundPackInfo.Status.EMPTY : SoundPackInfo.Status.OK;
        availablePacks.add(
                new SoundPackInfo(id, displayName, packPath, status, false, Collections.unmodifiableMap(foundNotes)));
        LOGGER.info("Updated/Loaded sound pack from directory: '{}' (ID: {})", displayName, id);
    }

    /**
     * 创建一个新的、空的音色包文件夹结构，包括 pack.mcmeta 和 pack.json。
     */
    public SoundPackInfo createNewPack(String displayName) {
        String baseId = displayName.replaceAll("[^a-zA-Z0-9\\s_.-]", "").replace(" ", "_").toLowerCase();
        String finalId = "extendednoteblock_" + baseId;
        int counter = 1;
        while (isIdTaken(finalId)) {
            finalId = "extendednoteblock_" + baseId + "_" + counter++;
        }

        Path packDir = getPacksDirectory().resolve(finalId);
        try {
            Files.createDirectories(
                    packDir.resolve("assets").resolve(ExtendedNoteBlock.MOD_ID).resolve("sounds").resolve("notes"));

            // *** 核心修改: 调用生成 pack.mcmeta 的方法 ***
            generatePackMcMeta(packDir);

            // 创建 pack.json
            JsonObject json = new JsonObject();
            json.addProperty("displayName", displayName);
            json.add("available_instruments", new JsonObject());
            try (FileWriter writer = new FileWriter(packDir.resolve(PACK_CONFIG_FILE).toFile())) {
                GSON.toJson(json, writer);
            }

            // 创建空的 sounds.json
            generateSoundsJson(packDir, Collections.emptyMap());

            scanPacks();
            return getPackInfoById(finalId);
        } catch (IOException e) {
            LOGGER.error("Failed to create new pack structure for '{}'", displayName, e);
            return null;
        }
    }

    /**
     * 辅助方法，如果 pack.mcmeta 不存在，则创建一个标准的。
     * 
     * @param packDir 资源包的根目录。
     */
    private void generatePackMcMeta(Path packDir) throws IOException {
        Path mcMetaPath = packDir.resolve(PACK_MCMETA_FILE);
        if (!Files.exists(mcMetaPath)) {
            JsonObject packMeta = new JsonObject();
            JsonObject packSection = new JsonObject();
            // pack_format 15 对应 1.20.1。可以根据你的目标版本调整。
            packSection.addProperty("pack_format", 15);
            packSection.addProperty("description", "Generated sounds for Extended Note Block Mod");
            packMeta.add("pack", packSection);

            try (FileWriter writer = new FileWriter(mcMetaPath.toFile())) {
                GSON.toJson(packMeta, writer);
            }
            LOGGER.info("Generated missing pack.mcmeta for pack '{}'", packDir.getFileName().toString());
        }
    }

    private boolean isIdTaken(String id) {
        if (id.equals(DEFAULT_PACK_ID))
            return true;
        if (Files.exists(getPacksDirectory().resolve(id)))
            return true;
        return availablePacks.stream().anyMatch(p -> p.id().equals(id));
    }

    // 以下方法是为了完整性而包含的，它们没有改动
    private Map<Integer, List<Integer>> readNotesFromPackJson(JsonObject json, String packId) {
        Map<Integer, List<Integer>> notesMap = new HashMap<>();
        if (json.has("available_instruments") && json.get("available_instruments").isJsonObject()) {
            JsonObject instrumentsObj = json.getAsJsonObject("available_instruments");
            for (String key : instrumentsObj.keySet()) {
                try {
                    int instrumentId = Integer.parseInt(key);
                    JsonArray notesArray = instrumentsObj.getAsJsonArray(key);
                    List<Integer> notes = new ArrayList<>();
                    notesArray.forEach(element -> notes.add(element.getAsInt()));
                    Collections.sort(notes);
                    notesMap.put(instrumentId, notes);
                } catch (Exception e) {
                    LOGGER.warn("Invalid entry for instrument key '{}' in pack.json for pack '{}'", key, packId);
                }
            }
        } else {
            LOGGER.warn(
                    "Pack '{}' is missing 'available_instruments' object in pack.json. It will be treated as empty.",
                    packId);
        }
        return notesMap;
    }

    public Map<Integer, List<Integer>> scanOggFiles(Path rootPath) {
        Map<Integer, List<Integer>> instrumentNotes = new HashMap<>();
        Path notesDir = rootPath.resolve("assets").resolve(ExtendedNoteBlock.MOD_ID).resolve("sounds").resolve("notes");

        if (!Files.isDirectory(notesDir)) {
            return instrumentNotes;
        }

        try (Stream<Path> stream = Files.walk(notesDir)) {
            stream.filter(p -> p.toString().endsWith(".ogg") && Files.isRegularFile(p))
                    .map(p -> notesDir.relativize(p).toString().replace(".ogg", "").replace(File.separatorChar, '.'))
                    .forEach(name -> {
                        try {
                            String[] parts = name.split("\\.");
                            if (parts.length == 2) {
                                int instrumentId = Integer.parseInt(parts[0]);
                                int noteId = Integer.parseInt(parts[1]);
                                instrumentNotes.computeIfAbsent(instrumentId, k -> new ArrayList<>()).add(noteId);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Error while scanning ogg files in {}", notesDir, e);
        }
        instrumentNotes.values().forEach(Collections::sort);
        return instrumentNotes;
    }

    public void generateSoundsJson(Path packPath, Map<Integer, List<Integer>> instrumentNotes) throws IOException {
        JsonObject soundsJson = new JsonObject();
        instrumentNotes.forEach((instrumentId, notes) -> {
            for (int note : notes) {
                String eventName = String.format("notes.%d.%d", instrumentId, note);
                String soundPath = String.format("%s:notes/%d.%d", ExtendedNoteBlock.MOD_ID, instrumentId, note);

                JsonObject soundEvent = new JsonObject();
                soundEvent.addProperty("category", "record");
                JsonArray sounds = new JsonArray();
                JsonObject soundEntry = new JsonObject();
                soundEntry.addProperty("name", soundPath);
                soundEntry.addProperty("stream", true);
                sounds.add(soundEntry);
                soundEvent.add("sounds", sounds);

                soundsJson.add(eventName, soundEvent);
            }
        });

        Path soundsJsonPath = packPath.resolve("assets").resolve(ExtendedNoteBlock.MOD_ID).resolve(SOUNDS_JSON_FILE);
        Files.createDirectories(soundsJsonPath.getParent());
        try (Writer writer = new FileWriter(soundsJsonPath.toFile())) {
            GSON.toJson(soundsJson, writer);
        }
    }

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
     * 设置并激活指定ID的声音包。
     * 
     * @param packId 要激活的声音包的ID，或 null 以停用。
     */
    public void setActivePack(String packId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null)
            return;

        client.execute(() -> {
            ResourcePackManager resourcePackManager = client.getResourcePackManager();
            if (resourcePackManager == null)
                return;

            resourcePackManager.scanPacks();
            Collection<String> allAvailablePackNames = resourcePackManager.getProfiles()
                    .stream()
                    .map(ResourcePackProfile::getName)
                    .collect(Collectors.toSet());

            List<String> originalEnabledPacks = new ArrayList<>(resourcePackManager.getEnabledNames());

            // *** 核心修改: 构建正确的包名列表以进行移除 ***
            Set<String> allMyPackResourceNames = getAvailablePacks().stream()
                    .map(pack -> {
                        String baseName = RESOURCE_PACK_PREFIX + pack.id();
                        // 如果是 zip 包，则在资源包管理器中的名称会包含 .zip 后缀
                        return pack.isZip() ? baseName + ".zip" : baseName;
                    })
                    .collect(Collectors.toSet());
            List<String> newEnabledPacks = new ArrayList<>(originalEnabledPacks);
            newEnabledPacks.removeAll(allMyPackResourceNames);

            this.activePackId = null;
            if (packId != null && !packId.isBlank()) {
                // *** 核心修改: 根据包类型构建正确的待激活包名 ***
                SoundPackInfo packToActivate = getPackInfoById(packId);
                if (packToActivate != null) {
                    String myPackResourceName = RESOURCE_PACK_PREFIX + packId;
                    if (packToActivate.isZip()) {
                        myPackResourceName += ".zip";
                    }

                    if (allAvailablePackNames.contains(myPackResourceName)) {
                        this.activePackId = packId;
                        newEnabledPacks.add(myPackResourceName);
                    } else {
                        LOGGER.error(
                                "Pack '{}' not found in available packs list provided by Minecraft. Aborting activation. Available: {}",
                                myPackResourceName, allAvailablePackNames);
                    }
                } else {
                    LOGGER.error("Pack with ID '{}' not found in SoundPackManager's internal list.", packId);
                }
            }

            if (!new HashSet<>(originalEnabledPacks).equals(new HashSet<>(newEnabledPacks))) {
                resourcePackManager.setEnabledProfiles(newEnabledPacks);
                ConfigManager.getConfig().activeSoundPackId = this.activePackId;
                ConfigManager.saveConfig();
                client.reloadResources().whenComplete((v, throwable) -> {
                    if (throwable != null)
                        LOGGER.error("Resource reload FAILED.", throwable);
                    else
                        LOGGER.info("Resource reload completed successfully.");
                });
            } else {
                if (!Objects.equals(ConfigManager.getConfig().activeSoundPackId, this.activePackId)) {
                    ConfigManager.getConfig().activeSoundPackId = this.activePackId;
                    ConfigManager.saveConfig();
                }
            }
        });
    }

    public SoundPackInfo getActivePackInfo() {
        if (activePackId == null || activePackId.isBlank())
            return null;
        return availablePacks.stream().filter(p -> p.id().equals(activePackId)).findFirst().orElse(null);
    }

    public SoundPackInfo getPackInfoById(String id) {
        return availablePacks.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    public String getActivePackId() {
        return activePackId;
    }

    public List<SoundPackInfo> getAvailablePacks() {
        return Collections.unmodifiableList(availablePacks);
    }

    /**
     * 检查当前在配置文件中标记为“活动”的声音包是否真的在 Minecraft 的已启用资源包列表中。
     * 这用于在游戏启动时进行预检查。
     */
    public boolean isCurrentPackActuallyEnabled() {
        String activePackId = getActivePackId();
        if (activePackId == null || activePackId.isBlank())
            return true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourcePackManager() == null)
            return true;

        // *** 核心修改: 根据包类型构建正确的期望名称 ***
        SoundPackInfo activePackInfo = getPackInfoById(activePackId);
        // 如果因为某些原因找不到包信息（例如文件被删除），则认为未启用
        if (activePackInfo == null) {
            return false;
        }

        String expectedResourceName = RESOURCE_PACK_PREFIX + activePackId;
        if (activePackInfo.isZip()) {
            expectedResourceName += ".zip";
        }

        ResourcePackManager resourcePackManager = client.getResourcePackManager();
        Collection<String> enabledPacks = resourcePackManager.getEnabledNames();

        return enabledPacks.contains(expectedResourceName);
    }
}