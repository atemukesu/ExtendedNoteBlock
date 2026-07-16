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
 * 负责扫描、加载、创建和管理用于扩展音符盒的音色包。
 * 它能处理文件夹格式和.zip压缩包格式的音色包，并自动生成必要的Minecraft资源包元数据。
 */
public class SoundPackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SoundPackManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- 核心常量定义 ---
    /** 音色包配置文件的名称，是识别我们音色包的关键。 */
    public static final String PACK_CONFIG_FILE = "pack.json";
    /** Minecraft资源包元数据文件名。 */
    private static final String PACK_MCMETA_FILE = "pack.mcmeta";
    /** Minecraft声音定义文件名。 */
    private static final String SOUNDS_JSON_FILE = "sounds.json";
    /** Minecraft资源包管理器中，文件系统资源包的名称前缀。 */
    private static final String RESOURCE_PACK_PREFIX = "file/";

    // --- 默认音色包相关常量 ---
    /** 默认音色包的ID。 */
    public static final String DEFAULT_PACK_ID = "extendednoteblock_default";
    /** 默认音色包的zip文件名。 */
    public static final String DEFAULT_PACK_ZIP_NAME = DEFAULT_PACK_ID + ".zip";
    /** 默认音色包在Mod JAR文件内的资源路径。 */
    private static final String DEFAULT_PACK_RESOURCE_PATH = "assets/" + ExtendedNoteBlock.MOD_ID + "/"
            + DEFAULT_PACK_ZIP_NAME;

    /** 存储所有扫描到的可用音色包信息。 */
    private final List<SoundPackInfo> availablePacks = new ArrayList<>();
    /** 当前激活的音色包的ID。该ID与文件名/目录名一致。 */
    private String activePackId = null;

    /** SoundPackManager的单例实例。 */
    private static final SoundPackManager INSTANCE = new SoundPackManager();

    private SoundPackManager() {
    }

    /**
     * 获取SoundPackManager的单例实例。
     * 
     * @return 单例实例。
     */
    public static SoundPackManager getInstance() {
        return INSTANCE;
    }

    /**
     * 根据乐器可用的采样音符列表，为每个乐器预计算一个查找表。
     * 这个查找表将每一个MIDI音高(0-127)映射到该乐器最接近的可用采样音高。
     * 预计算可以极大地提高运行时查找最近音符的效率。
     *
     * @param availableNotesPerInstrument 一个Map，键是乐器ID，值是该乐器所有可用采样音符的列表。
     * @return 一个包含所有乐器查找表的Map。
     */
    private static Map<Integer, Map<Integer, Integer>> createLookupTables(
            Map<Integer, List<Integer>> availableNotesPerInstrument) {
        Map<Integer, Map<Integer, Integer>> allLookupTables = new HashMap<>();

        for (Map.Entry<Integer, List<Integer>> entry : availableNotesPerInstrument.entrySet()) {
            int instrumentId = entry.getKey();
            List<Integer> availableNotes = entry.getValue();

            if (availableNotes.isEmpty()) {
                continue; // 跳过没有采样的乐器
            }

            // 为单个乐器创建查找表
            Map<Integer, Integer> singleLookupTable = new HashMap<>();
            for (int i = 0; i < 128; i++) {
                singleLookupTable.put(i, findClosestValue(i, availableNotes));
            }
            allLookupTables.put(instrumentId, Collections.unmodifiableMap(singleLookupTable));
        }
        return Collections.unmodifiableMap(allLookupTables);
    }

    /**
     * 在一个有序列表中使用二分查找算法找到最接近目标值的数。
     * 
     * @param target     目标值。
     * @param sortedList 一个已排序的整数列表。
     * @return 列表中最接近目标值的整数。
     */
    private static int findClosestValue(int target, List<Integer> sortedList) {
        if (sortedList.isEmpty())
            return target; // 极端情况处理

        int i = Collections.binarySearch(sortedList, target);
        if (i >= 0)
            return sortedList.get(i); // 精确匹配

        int insertionPoint = -i - 1;
        // 处理边界情况
        if (insertionPoint == 0)
            return sortedList.get(0);
        if (insertionPoint == sortedList.size())
            return sortedList.get(sortedList.size() - 1);

        // 比较插入点两侧的值，返回更近的一个
        int lower = sortedList.get(insertionPoint - 1);
        int upper = sortedList.get(insertionPoint);
        return (target - lower < upper - target) ? lower : upper;
    }

    /**
     * 获取Minecraft的资源包目录路径。
     * 
     * @return 资源包目录的Path对象。
     */
    public Path getPacksDirectory() {
        return MinecraftClient.getInstance().getResourcePackDir();
    }

    /**
     * 扫描资源包目录，发现并加载所有兼容的音色包。
     * 此方法会清空现有列表，并重新填充。
     */
    public void scanPacks() {
        this.availablePacks.clear();
        Path packsDir = getPacksDirectory();
        createDirectoryIfNotExists(packsDir);

        // 确保默认音色包存在于资源包目录中，如果不存在则从JAR中提取。
        boolean defaultPackReady = ensureDefaultPackIsAvailable();

        try (Stream<Path> stream = Files.list(packsDir)) {
            stream.forEach(packPath -> {
                boolean isZip = packPath.toString().toLowerCase().endsWith(".zip");
                // 只处理文件夹和.zip文件
                if (isZip || Files.isDirectory(packPath)) {
                    // 检查是否为我们的音色包格式
                    if (isExtendedNoteBlockPack(packPath, isZip)) {
                        loadOrUpdatePack(packPath, isZip);
                    }
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to scan for sound packs in {}", packsDir, e);
        }

        // 如果默认包已准备好但未被加载（例如首次运行），则强制加载它。
        Path defaultPackPath = packsDir.resolve(DEFAULT_PACK_ZIP_NAME);
        if (defaultPackReady && availablePacks.stream().noneMatch(p -> p.id().equals(DEFAULT_PACK_ZIP_NAME))) {
            loadOrUpdatePack(defaultPackPath, true);
        }

        // 从配置文件加载当前激活的音色包ID。
        this.activePackId = ConfigManager.getConfig().activeSoundPackId;
    }

    /**
     * 检查给定的路径是否是一个有效的扩展音符盒音色包。
     * 
     * @param packPath 资源包的路径（文件夹或.zip）。
     * @param isZip    路径是否指向一个.zip文件。
     * @return 如果是有效的音色包，返回true。
     */
    private boolean isExtendedNoteBlockPack(Path packPath, boolean isZip) {
        if (isZip) {
            try (FileSystem fs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
                // zip包必须同时包含 pack.mcmeta 和我们自定义的 pack.json
                return Files.exists(fs.getPath(PACK_MCMETA_FILE)) && Files.exists(fs.getPath(PACK_CONFIG_FILE));
            } catch (IOException e) {
                return false;
            }
        } else {
            // 文件夹包只需要 pack.json，因为 pack.mcmeta 可以自动生成。
            return Files.exists(packPath.resolve(PACK_CONFIG_FILE));
        }
    }

    /**
     * 确保默认音色包存在于资源包目录中。如果不存在，则尝试从Mod的JAR文件中提取。
     * 
     * @return 如果默认包已就绪，返回true。
     */
    private boolean ensureDefaultPackIsAvailable() {
        Path defaultPackPath = getPacksDirectory().resolve(DEFAULT_PACK_ZIP_NAME);
        if (Files.exists(defaultPackPath)) {
            return true; // 文件已存在
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

    /**
     * 加载或更新单个音色包的信息。
     * 此方法会处理ID生成、文件扫描和元数据更新。
     * 
     * @param packPath 资源包的路径。
     * @param isZip    路径是否指向.zip文件。
     */
    public void loadOrUpdatePack(Path packPath, boolean isZip) {
        String fileName = packPath.getFileName().toString();

        // [ID管理方案] 采用最简洁的ID方案：ID就是文件名/目录名。
        // 这自然地解决了文件夹和.zip包同名时的冲突问题。
        // 例：文件夹 "MyPack" 的 ID 是 "MyPack"
        // 压缩包 "MyPack.zip" 的 ID 是 "MyPack.zip"
        String id = fileName;

        // 在添加新信息前，先移除任何具有相同ID的旧信息。
        availablePacks.removeIf(p -> p.id().equals(id));

        try {
            SoundPackInfo packInfo;
            if (isZip) {
                packInfo = loadPackFromZip(packPath, id);
            } else {
                packInfo = updateAndLoadPackFromDirectory(packPath, id);
            }
            availablePacks.add(packInfo);
        } catch (Exception e) {
            LOGGER.error("Failed to load or update sound pack '{}'", id, e);
            // 即使加载失败，也添加一个INVALID状态的条目，以便在UI中向用户显示错误。
            String displayName = isZip ? id.substring(0, id.length() - 4) : id;
            availablePacks.add(
                    new SoundPackInfo(id, displayName, packPath, SoundPackInfo.Status.INVALID, isZip,
                            Collections.emptyMap(),
                            Collections.emptyMap()));
        }
    }

    /**
     * 从.zip压缩包加载音色包信息。
     * 
     * @param zipPath .zip文件的路径。
     * @param id      音色包的唯一ID (即文件名)。
     * @return 一个包含音色包信息的SoundPackInfo对象。
     * @throws IOException 如果文件读取失败。
     */
    private SoundPackInfo loadPackFromZip(Path zipPath, String id) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(zipPath, (ClassLoader) null)) {
            Path packConfigFile = fs.getPath(PACK_CONFIG_FILE);

            JsonObject json;
            try (Reader reader = Files.newBufferedReader(packConfigFile)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }

            // 从pack.json中读取显示名称，如果不存在，则使用不带.zip后缀的文件名作为后备。
            String baseName = id.substring(0, id.length() - 4);
            String displayName = json.has("displayName") ? json.get("displayName").getAsString() : baseName;

            // 为默认音色包提供一个可翻译的名称。
            if (id.startsWith(DEFAULT_PACK_ID)) {
                displayName = Text.translatable("gui.extendednoteblock.pack_manager.default_pack_name").getString();
            }

            // 读取并解析pack.json中定义的可用音符。
            Map<Integer, List<Integer>> notesMap = readNotesFromPackJson(json, id);
            Map<Integer, Map<Integer, Integer>> lookupTables = createLookupTables(notesMap);

            SoundPackInfo.Status status = notesMap.isEmpty() ? SoundPackInfo.Status.EMPTY : SoundPackInfo.Status.OK;
            LOGGER.info("Loaded sound pack from zip: '{}' (ID: {})", displayName, id);
            return new SoundPackInfo(id, displayName, zipPath, status, true, Collections.unmodifiableMap(notesMap),
                    lookupTables);
        }
    }

    /**
     * 从文件夹加载音色包，并在此过程中更新其元数据文件。
     * 
     * @param packPath 文件夹的路径。
     * @param id       音色包的唯一ID (即目录名)。
     * @return 一个包含音色包信息的SoundPackInfo对象。
     * @throws IOException 如果文件操作失败。
     */
    private SoundPackInfo updateAndLoadPackFromDirectory(Path packPath, String id) throws IOException {
        // 确保pack.mcmeta文件存在，如果不存在则自动生成一个。
        generatePackMcMeta(packPath);

        // 扫描文件夹中的.ogg文件，以确定实际可用的音符。
        Map<Integer, List<Integer>> foundNotes = scanOggFiles(packPath);

        Path packJsonPath = packPath.resolve(PACK_CONFIG_FILE);
        JsonObject packJson;
        try (Reader reader = new FileReader(packJsonPath.toFile())) {
            packJson = JsonParser.parseReader(reader).getAsJsonObject();
        }

        // 更新pack.json中的显示名称和可用音符列表。
        String displayName = packJson.has("displayName") ? packJson.get("displayName").getAsString() : id;
        packJson.addProperty("displayName", displayName);

        Type mapType = new TypeToken<Map<Integer, List<Integer>>>() {
        }.getType();
        JsonElement instrumentsElement = GSON.toJsonTree(foundNotes, mapType);
        packJson.add("available_instruments", instrumentsElement);
        // 为了兼容性或未来使用，也添加一个扁平化的音符列表。
        JsonArray flatNotesArray = new JsonArray();
        foundNotes.values().stream().flatMap(List::stream).distinct().sorted().forEach(flatNotesArray::add);
        packJson.add("available_notes", flatNotesArray);

        try (Writer writer = new FileWriter(packJsonPath.toFile())) {
            GSON.toJson(packJson, writer);
        }

        // 根据扫描到的音符，重新生成sounds.json文件。
        generateSoundsJson(packPath, foundNotes);

        Map<Integer, Map<Integer, Integer>> lookupTables = createLookupTables(foundNotes);

        SoundPackInfo.Status status = foundNotes.isEmpty() ? SoundPackInfo.Status.EMPTY : SoundPackInfo.Status.OK;
        LOGGER.info("Updated/Loaded sound pack from directory: '{}' (ID: {})", displayName, id);
        return new SoundPackInfo(id, displayName, packPath, status, false, Collections.unmodifiableMap(foundNotes),
                lookupTables);
    }

    /**
     * 创建一个新的、空的音色包文件夹结构。
     * 
     * @param displayName 用户为新音色包指定的名称。
     * @return 如果创建成功，返回新音色包的SoundPackInfo对象，否则返回null。
     */
    public SoundPackInfo createNewPack(String displayName) {
        // 根据显示名称生成一个安全的文件系统ID。
        String baseId = displayName.replaceAll("[^a-zA-Z0-9\\s_.-]", "").replace(" ", "_").toLowerCase();
        String finalId = "extendednoteblock_" + baseId;
        int counter = 1;
        // 确保ID不重复
        while (isIdTaken(finalId)) {
            finalId = "extendednoteblock_" + baseId + "_" + counter++;
        }

        Path packDir = getPacksDirectory().resolve(finalId);
        try {
            // 创建必要的目录结构。
            Files.createDirectories(
                    packDir.resolve("assets").resolve(ExtendedNoteBlock.MOD_ID).resolve("sounds").resolve("notes"));

            generatePackMcMeta(packDir);

            // 创建初始的pack.json。
            JsonObject json = new JsonObject();
            json.addProperty("displayName", displayName);
            json.add("available_instruments", new JsonObject());
            try (FileWriter writer = new FileWriter(packDir.resolve(PACK_CONFIG_FILE).toFile())) {
                GSON.toJson(json, writer);
            }

            // 创建空的sounds.json。
            generateSoundsJson(packDir, Collections.emptyMap());

            // 重新扫描以将新包加入列表。
            scanPacks();
            return getPackInfoById(finalId);
        } catch (IOException e) {
            LOGGER.error("Failed to create new pack structure for '{}'", displayName, e);
            return null;
        }
    }

    /**
     * 辅助方法，如果pack.mcmeta不存在，则创建一个标准的。
     * 
     * @param packDir 资源包的根目录。
     * @throws IOException 如果文件写入失败。
     */
    private void generatePackMcMeta(Path packDir) throws IOException {
        Path mcMetaPath = packDir.resolve(PACK_MCMETA_FILE);
        if (!Files.exists(mcMetaPath)) {
            JsonObject packMeta = new JsonObject();
            JsonObject packSection = new JsonObject();
            // pack_format 15 对应 1.20.1。可以根据目标Minecraft版本调整。
            packSection.addProperty("pack_format", 15);
            packSection.addProperty("description", "Generated sounds for Extended Note Block Mod");
            packMeta.add("pack", packSection);

            try (FileWriter writer = new FileWriter(mcMetaPath.toFile())) {
                GSON.toJson(packMeta, writer);
            }
            LOGGER.info("Generated missing pack.mcmeta for pack '{}'", packDir.getFileName().toString());
        }
    }

    /**
     * 检查给定的ID是否已被占用。
     * 
     * @param id 要检查的ID。
     * @return 如果ID已被占用，返回true。
     */
    private boolean isIdTaken(String id) {
        if (id.equals(DEFAULT_PACK_ID))
            return true;
        if (Files.exists(getPacksDirectory().resolve(id)))
            return true;
        if (Files.exists(getPacksDirectory().resolve(id + ".zip")))
            return true;
        return availablePacks.stream().anyMatch(p -> p.id().equals(id) || p.id().equals(id + ".zip"));
    }

    /**
     * 从pack.json的JsonObject中读取并解析乐器和音符信息。
     * 
     * @param json   pack.json文件的JsonObject表示。
     * @param packId 音色包的ID，用于日志记录。
     * @return 一个包含乐器->音符列表的Map。
     */
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

    /**
     * 扫描音色包目录下的.ogg文件，并构建一个乐器到音符列表的映射。
     * 文件名格式应为 "乐器ID.音符ID.ogg"。
     * 
     * @param rootPath 音色包的根目录。
     * @return 一个包含扫描到的乐器和音符的Map。
     */
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

    /**
     * 根据扫描到的音符信息，生成sounds.json文件。
     * 
     * @param packPath        音色包根目录。
     * @param instrumentNotes 包含乐器和音符信息的Map。
     * @throws IOException 如果文件写入失败。
     */
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

                soundEntry.addProperty("stream", false); // 解决性能问题

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

    /**
     * 辅助方法，确保目录存在。
     * 
     * @param dir 要检查或创建的目录。
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
     * 设置并激活指定ID的声音包。
     * 此方法会与Minecraft的ResourcePackManager交互，启用/禁用相应的资源包，并触发资源重载。
     * 
     * @param packId 要激活的音色包的ID (即文件名/目录名)，或 null 以停用所有音色包。
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

            // 1. 从当前启用的资源包列表中，移除所有由本Mod管理的音色包。
            Set<String> allMyPackResourceNames = getAvailablePacks().stream()
                    .map(pack -> RESOURCE_PACK_PREFIX + pack.id()) // ID就是文件名，直接拼接
                    .collect(Collectors.toSet());
            List<String> newEnabledPacks = new ArrayList<>(originalEnabledPacks);
            newEnabledPacks.removeAll(allMyPackResourceNames);

            // 2. 如果指定了要激活的包，则将其添加到启用列表中。
            this.activePackId = null;
            if (packId != null && !packId.isBlank()) {
                SoundPackInfo packToActivate = getPackInfoById(packId);
                if (packToActivate != null) {
                    String myPackResourceName = RESOURCE_PACK_PREFIX + packId;
                    if (allAvailablePackNames.contains(myPackResourceName)) {
                        this.activePackId = packId;
                        newEnabledPacks.add(myPackResourceName);
                    } else {
                        LOGGER.error("Pack '{}' not found in Minecraft's resource pack list. Aborting activation.",
                                myPackResourceName);
                    }
                } else {
                    LOGGER.error("Pack with ID '{}' not found in SoundPackManager's internal list.", packId);
                }
            }

            // 3. 如果启用列表有变动，则应用更改并重载资源。
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
                // 即使资源包列表没变，配置中的ID也可能变了（例如从一个包切换到无包），需要保存。
                if (!Objects.equals(ConfigManager.getConfig().activeSoundPackId, this.activePackId)) {
                    ConfigManager.getConfig().activeSoundPackId = this.activePackId;
                    ConfigManager.saveConfig();
                }
            }
        });
    }

    /**
     * 获取当前激活的音色包的信息。
     * 
     * @return SoundPackInfo对象，如果没有激活的包则返回null。
     */
    public SoundPackInfo getActivePackInfo() {
        if (activePackId == null || activePackId.isBlank())
            return null;
        return availablePacks.stream().filter(p -> p.id().equals(activePackId)).findFirst().orElse(null);
    }

    /**
     * 根据ID获取音色包信息。
     * 
     * @param id 音色包的ID。
     * @return SoundPackInfo对象，如果未找到则返回null。
     */
    public SoundPackInfo getPackInfoById(String id) {
        return availablePacks.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * 获取当前激活的音色包的ID。
     * 
     * @return 激活的音色包ID字符串。
     */
    public String getActivePackId() {
        return activePackId;
    }

    /**
     * 获取所有可用音色包的列表。
     * 
     * @return 一个不可修改的SoundPackInfo列表。
     */
    public List<SoundPackInfo> getAvailablePacks() {
        return Collections.unmodifiableList(availablePacks);
    }

    /**
     * 检查当前在配置文件中标记为“活动”的音色包是否真的在Minecraft的已启用资源包列表中。
     * 这用于游戏启动时的预检查。
     * 
     * @return 如果当前配置的包已启用，返回true。
     */
    public boolean isCurrentPackActuallyEnabled() {
        String activePackId = getActivePackId();
        // 如果没有设置激活包，则认为检查通过。
        if (activePackId == null || activePackId.isBlank())
            return true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourcePackManager() == null)
            return true; // 无法检查时，默认为true

        // activePackId就是文件名/目录名，直接拼接即可得到Minecraft期望的资源名称。
        String expectedResourceName = RESOURCE_PACK_PREFIX + activePackId;

        ResourcePackManager resourcePackManager = client.getResourcePackManager();
        Collection<String> enabledPacks = resourcePackManager.getEnabledNames();

        return enabledPacks.contains(expectedResourceName);
    }
}