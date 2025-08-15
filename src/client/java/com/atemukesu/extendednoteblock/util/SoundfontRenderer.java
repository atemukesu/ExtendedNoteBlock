package com.atemukesu.extendednoteblock.util;

import com.atemukesu.extendednoteblock.config.ConfigManager;
import com.atemukesu.extendednoteblock.map.InstrumentMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import javax.sound.midi.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 音色库渲染器类。
 * 该类负责将 MIDI 音符通过指定的音色库（SF2 文件）渲染成音频文件，
 * 并最终打包成 Minecraft 资源包。
 * 它利用外部工具 FluidSynth 和 FFmpeg 来完成音频的合成与转换。
 * 整个过程是多线程的，以提高效率。
 */
public class SoundfontRenderer {

    /**
     * 模组ID，用于资源路径等。
     */
    public static final String MOD_ID = "extendednoteblock";
    /**
     * 用于处理JSON的Gson实例。
     */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /**
     * 默认SF2音色库在资源文件中的路径。
     */
    public static final String DEFAULT_SF2_RESOURCE_PATH = "assets/" + MOD_ID + "/default.sf2";
    /**
     * FluidSynth 可执行文件的路径。
     */
    private final String fluidSynthPath;
    /**
     * FFmpeg 可执行文件的路径。
     */
    private final String ffmpegPath;
    /**
     * 用户指定的 SF2 音色库文件路径。
     */
    private final String sf2Path;
    /**
     * 生成的资源包的输出目录。
     */
    private final Path outputPackDir;
    /**
     * 用于更新状态信息的回调函数。
     */
    private final Consumer<Text> onStatusUpdate;
    /**
     * 用于更新进度条的回调函数（当前值，总值）。
     */
    private final BiConsumer<Integer, Integer> onProgressUpdate;
    /**
     * 用于记录日志信息的回调函数。
     */
    private final Consumer<Text> onLogMessage;
    /**
     * 资源包管理器中文件资源包的前缀。
     */
    private static final String RESOURCE_PACK_PREFIX = "file/";

    /**
     * 标记渲染过程中是否发生错误的原子布尔值。
     */
    private final AtomicBoolean hasErrors = new AtomicBoolean(false);

    /**
     * 构造一个新的 SoundfontRenderer 实例。
     *
     * @param fluidSynthPath   FluidSynth 可执行文件的路径。
     * @param ffmpegPath       FFmpeg 可执行文件的路径。
     * @param sf2Path          用户指定的 SF2 音色库文件路径，可为空。
     * @param outputPackDir    生成的资源包的输出目录。
     * @param onStatusUpdate   用于更新状态文本的回调。
     * @param onProgressUpdate 用于更新进度的回调。
     * @param onLogMessage     用于记录日志的回调。
     */
    public SoundfontRenderer(String fluidSynthPath, String ffmpegPath, String sf2Path, Path outputPackDir,
            Consumer<Text> onStatusUpdate, BiConsumer<Integer, Integer> onProgressUpdate,
            Consumer<Text> onLogMessage) {
        this.fluidSynthPath = fluidSynthPath;
        this.ffmpegPath = ffmpegPath;
        this.sf2Path = sf2Path;
        this.outputPackDir = outputPackDir;
        this.onStatusUpdate = onStatusUpdate;
        this.onProgressUpdate = onProgressUpdate;
        this.onLogMessage = onLogMessage;
    }

    /**
     * 获取所有预定义的乐器列表。
     * 此列表包括所有标准的GM乐器以及一个鼓组。
     * 为每个乐器定义了需要渲染的MIDI音符。
     *
     * @return 包含所有定义乐器的列表。
     */
    private static List<Instrument> getDefinedInstruments() {
        List<Instrument> instruments = new ArrayList<>();
        // 定义旋律乐器需要渲染的音高范围
        List<Integer> melodicNotes = List.of(0, 12, 24, 36, 48, 60, 72, 84, 96, 108);
        // 定义鼓组需要渲染的音高范围（对应不同的打击乐器）
        List<Integer> drumNotes = new ArrayList<>();
        for (int i = 35; i <= 81; i++) {
            drumNotes.add(i);
        }

        // 添加所有128个标准GM旋律乐器
        for (int i = 0; i <= 127; i++) {
            String instrumentName = InstrumentMap.GM_INSTRUMENT_ID_TO_NAME.getOrDefault(i, "Unknown Instrument " + i);
            instruments.add(new Instrument(instrumentName, 0, i, melodicNotes, false));
        }
        // 添加鼓组
        String drumKitName = InstrumentMap.GM_INSTRUMENT_ID_TO_NAME.get(128);
        instruments.add(new Instrument(drumKitName, 0, 0, drumNotes, true));
        return instruments;
    }

    /**
     * 获取预期生成的所有声音文件的文件名列表。
     * 这个列表用于检查或预知渲染过程的输出。
     *
     * @return 一个包含所有预期声音文件名的字符串列表。
     */
    public static List<String> getExpectedSoundFiles() {
        List<String> expectedFiles = new ArrayList<>();
        List<Instrument> instruments = getDefinedInstruments();
        for (int i = 0; i < instruments.size(); i++) {
            Instrument instrument = instruments.get(i);
            int instrumentId = i;
            for (int midiNote : instrument.notes()) {
                expectedFiles.add(String.format("%d.%d.ogg", instrumentId, midiNote));
            }
        }
        return expectedFiles;
    }

    /**
     * 执行完整的音色库渲染和资源包生成过程。
     * 这是一个阻塞方法，直到所有任务完成或超时才会返回。
     * 过程包括：
     * 1. 创建临时工作目录。
     * 2. 准备SF2音色库文件（使用用户指定的或默认的）。
     * 3. 初始化线程池。
     * 4. 为每个乐器的每个音符提交一个渲染任务。
     * 5. 等待所有任务完成。
     * 6. 生成 `sounds.json` 和 `pack.mcmeta` 文件。
     * 7. 触发Minecraft客户端重新加载资源包。
     * 8. 清理临时文件。
     *
     * @return 如果过程成功完成（可能有一些非关键性错误），则返回 `true`；如果发生严重错误，则返回 `false`。
     */
    public boolean run() {
        ExecutorService executor = null;
        Path workDir = null;
        try {
            // 创建临时工作目录
            workDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("extendednoteblock_temp");
            Files.createDirectories(workDir);

            // 创建资源包内声音文件的输出目录
            Path soundsOutputDir = this.outputPackDir.resolve("assets").resolve(MOD_ID).resolve("sounds")
                    .resolve("notes");
            Files.createDirectories(soundsOutputDir);

            // 准备SF2文件
            String finalSf2Path = prepareSf2File(workDir);
            if (finalSf2Path == null) {
                return false; // SF2文件准备失败
            }

            // 根据配置或系统核心数确定线程数
            Integer configuredThreads = ConfigManager.getConfig().threads;
            int threadCount = (configuredThreads != null && configuredThreads > 0)
                    ? configuredThreads
                    : Runtime.getRuntime().availableProcessors();

            log(Text.translatable("gui.extendednoteblock.rendering.log.info.threads", threadCount));
            executor = Executors.newFixedThreadPool(threadCount);

            List<Instrument> instruments = getDefinedInstruments();
            int totalTasks = instruments.stream().mapToInt(i -> i.notes().size()).sum();
            onProgressUpdate.accept(0, totalTasks);
            AtomicInteger currentTask = new AtomicInteger(0);
            ConcurrentHashMap<String, JsonObject> soundEntries = new ConcurrentHashMap<>();

            // 为每个乐器的每个音符提交渲染任务
            for (int i = 0; i < instruments.size(); i++) {
                final Instrument instrument = instruments.get(i);
                final int instrumentId = i;
                final Path finalWorkDir = workDir;

                for (int midiNote : instrument.notes()) {
                    executor.submit(() -> {
                        renderSingleNoteTask(instrument, instrumentId, midiNote, finalWorkDir, soundsOutputDir,
                                finalSf2Path, soundEntries);
                        onProgressUpdate.accept(currentTask.incrementAndGet(), totalTasks);
                    });
                }
            }

            // 等待所有任务完成
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                logFailure(Text.translatable("gui.extendednoteblock.rendering.log.failure.timeout.title"),
                        Text.translatable("gui.extendednoteblock.rendering.log.failure.timeout.details"));
                executor.shutdownNow();
            }

            if (hasErrors.get()) {
                log(Text.translatable("gui.extendednoteblock.rendering.log.info.some_errors"));
            }

            onProgressUpdate.accept(totalTasks, totalTasks);

            // 生成资源包文件
            onStatusUpdate.accept(Text.translatable("gui.extendednoteblock.rendering.status.generating_pack"));

            JsonObject soundsJson = new JsonObject();
            soundEntries.forEach(soundsJson::add);

            generateResourcePackFiles(this.outputPackDir, soundsJson);
            // 重新加载资源包以应用更改
            reloadResourcePacks();

        } catch (Exception e) {
            logFailure(Text.translatable("gui.extendednoteblock.rendering.log.failure.critical"),
                    Text.literal(e.getMessage()));
            if (executor != null) {
                executor.shutdownNow();
            }
            return false;
        } finally {
            // 清理临时工作目录
            if (workDir != null) {
                try {
                    Files.walk(workDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                } catch (IOException e) {
                    log(Text.translatable("gui.extendednoteblock.rendering.log.warning.cleanup", e.getMessage()));
                }
            }
        }
        return !hasErrors.get();
    }

    /**
     * 单个音符的渲染任务。
     * 该方法由线程池中的线程执行。它会检查目标OGG文件是否已存在，如果不存在，则执行以下步骤：
     * 1. 生成一个只包含单个音符的临时MIDI文件。
     * 2. 调用FluidSynth将MIDI文件渲染成WAV文件。
     * 3. 调用FFmpeg将WAV文件转换为OGG文件。
     * 4. 如果成功，将声音条目添加到并发映射中，以备生成`sounds.json`。
     * 5. 清理临时文件（MIDI和WAV）。
     *
     * @param instrument      当前乐器对象。
     * @param instrumentId    乐器的ID。
     * @param midiNote        要渲染的MIDI音符值。
     * @param workDir         临时工作目录。
     * @param soundsOutputDir 最终OGG文件的输出目录。
     * @param sf2Path         SF2音色库文件的路径。
     * @param soundEntries    用于存储`sounds.json`条目的并发映射。
     */
    private void renderSingleNoteTask(Instrument instrument, int instrumentId, int midiNote, Path workDir,
            Path soundsOutputDir, String sf2Path, ConcurrentHashMap<String, JsonObject> soundEntries) {
        String soundName = String.format("%d.%d", instrumentId, midiNote);
        Path oggFile = soundsOutputDir.resolve(soundName + ".ogg");

        // 如果文件已存在，则跳过渲染
        if (Files.exists(oggFile)) {
            String jsonKey = "notes." + soundName;
            String soundPath = MOD_ID + ":notes/" + soundName;

            JsonObject soundEntry = new JsonObject();
            soundEntry.addProperty("category", "record");
            JsonArray soundsArray = new JsonArray();
            soundsArray.add(soundPath);
            soundEntry.add("sounds", soundsArray);

            soundEntries.put(jsonKey, soundEntry);

            log(Text.translatable("gui.extendednoteblock.rendering.log.info.skipped",
                    soundName));

            return;
        }

        onStatusUpdate.accept(Text.translatable(
                "gui.extendednoteblock.rendering.status.rendering_instrument",
                instrument.name(), midiNote));

        String jsonKey = "notes." + soundName;
        Path midiFile = workDir.resolve(soundName + ".mid");
        Path wavFile = workDir.resolve(soundName + ".wav");

        try {
            if (generateMidiFile(instrument, midiNote, midiFile)
                    && renderWavFromMidi(midiFile, sf2Path, wavFile)
                    && convertToOgg(wavFile, oggFile)) {

                // 渲染成功后，构建 sounds.json 条目
                String soundPath = MOD_ID + ":notes/" + soundName;

                JsonObject soundEntry = new JsonObject();
                soundEntry.addProperty("category", "record");
                JsonArray soundsArray = new JsonArray();
                soundsArray.add(soundPath);
                soundEntry.add("sounds", soundsArray);

                soundEntries.put(jsonKey, soundEntry);

                log(Text.translatable("gui.extendednoteblock.rendering.log.success.render",
                        instrument.name(), instrumentId, midiNote));
            }
        } finally {
            // 清理本次任务产生的临时文件
            try {
                Files.deleteIfExists(midiFile);
                Files.deleteIfExists(wavFile);
            } catch (IOException e) {
                log(Text.translatable("gui.extendednoteblock.rendering.log.warning.cleanup.file", soundName));
            }
        }
    }

    /**
     * 准备要使用的SF2音色库文件。
     * 如果用户在配置中指定了有效的SF2文件路径，则使用该文件。
     * 否则，从模组的资源中提取默认的SF2文件到临时目录。
     *
     * @param workDir 临时工作目录，用于存放提取出的默认SF2文件。
     * @return SF2文件的绝对路径字符串。如果失败则返回 null。
     * @throws IOException 如果文件操作失败。
     */
    private String prepareSf2File(Path workDir) throws IOException {
        // 检查用户是否提供了SF2路径
        if (this.sf2Path != null && !this.sf2Path.isBlank()) {
            File userSf2 = new File(this.sf2Path);
            if (userSf2.exists()) {
                log(Text.translatable("gui.extendednoteblock.rendering.log.info.sf2.user", this.sf2Path));
                return this.sf2Path;
            } else {
                logFailure(Text.translatable("gui.extendednoteblock.rendering.log.failure.sf2.not_found"),
                        Text.literal(this.sf2Path));
            }
        }
        // 使用内置的默认SF2文件
        try (InputStream is = SoundfontRenderer.class.getClassLoader().getResourceAsStream(DEFAULT_SF2_RESOURCE_PATH)) {
            if (is == null) {
                logFailure(Text.translatable("gui.extendednoteblock.rendering.log.failure.sf2.internal_missing"),
                        Text.literal(DEFAULT_SF2_RESOURCE_PATH));
                return null;
            }
            Path defaultSf2 = workDir.resolve("default.sf2");
            Files.copy(is, defaultSf2, StandardCopyOption.REPLACE_EXISTING);
            log(Text.translatable("gui.extendednoteblock.rendering.log.info.sf2.default"));
            return defaultSf2.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new IOException("Failed to extract default SoundFont", e);
        }
    }

    /**
     * 为指定的乐器和音符生成一个临时的MIDI文件。
     *
     * @param instrument 乐器对象。
     * @param midiNote   要生成的MIDI音符值。
     * @param midiFile   输出的MIDI文件路径。
     * @return 如果生成成功，返回 `true`，否则返回 `false`。
     */
    private boolean generateMidiFile(Instrument instrument, int midiNote, Path midiFile) {
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, 480);
            Track track = sequence.createTrack();

            int channel = instrument.isDrumKit() ? 9 : 0; // 鼓组使用通道9
            long currentTick = 0;

            // 设置乐器
            if (instrument.isDrumKit()) {
                // 为鼓组设置正确的控制器和程序
                track.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 0, 127), currentTick));
                track.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 32, 0), currentTick));
                track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, channel, 0, 0), currentTick));
            } else {
                track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, channel, instrument.program(), 0),
                        currentTick));
            }

            // 添加音符按下事件
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, channel, midiNote, 100), currentTick));

            // 定义音符持续时间（较长以确保完整采样）
            long noteDurationTicks = 19200;
            // 添加音符松开事件
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, channel, midiNote, 100),
                    currentTick + noteDurationTicks));

            MidiSystem.write(sequence, 1, midiFile.toFile());
            return true;

        } catch (Exception e) {
            logFailure(
                    Text.translatable("gui.extendednoteblock.rendering.log.failure.midi", instrument.name(), midiNote),
                    Text.literal(e.getMessage()));
            return false;
        }
    }

    /**
     * 使用FluidSynth将MIDI文件渲染成WAV文件。
     *
     * @param midiFile MIDI输入文件路径。
     * @param sf2Path  SF2音色库文件路径。
     * @param wavFile  WAV输出文件路径。
     * @return 如果渲染成功，返回 `true`，否则返回 `false`。
     */
    private boolean renderWavFromMidi(Path midiFile, String sf2Path, Path wavFile) {
        ProcessBuilder pb = new ProcessBuilder(
                fluidSynthPath, "-ni", "-g", "0.8", "-r", "44100",
                "-F", wavFile.toAbsolutePath().toString(),
                sf2Path, midiFile.toAbsolutePath().toString());
        return executeProcess(pb, Text.translatable("gui.extendednoteblock.rendering.log.failure.fluidsynth"));
    }

    /**
     * 使用FFmpeg将WAV文件转换为OGG格式。
     * OGG 必须为单声道音频文件，否则衰减与立体声无效。
     *
     * @param wavFile WAV输入文件路径。
     * @param oggFile OGG输出文件路径。
     * @return 如果转换成功，返回 `true`，否则返回 `false`。
     */
    private boolean convertToOgg(Path wavFile, Path oggFile) {
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-i", wavFile.toAbsolutePath().toString(),
                "-ac", "1", // 强制输出单声道
                "-codec:a", "libvorbis", 
                "-q:a", "5",
                "-y", // 覆盖已存在的文件
                oggFile.toAbsolutePath().toString());
        return executeProcess(pb,
                Text.translatable("gui.extendednoteblock.rendering.log.failure.ffmpeg", oggFile.getFileName()));
    }

    /**
     * 执行一个外部进程（如FluidSynth或FFmpeg）。
     * 它会捕获进程的输出，并检查退出代码以判断是否成功。
     *
     * @param pb           配置好的 ProcessBuilder 实例。
     * @param errorContext 发生错误时用于日志记录的上下文信息。
     * @return 如果进程成功执行（退出码为0），返回 `true`，否则返回 `false`。
     */
    private boolean executeProcess(ProcessBuilder pb, Text errorContext) {
        try {
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取进程输出
            StringBuilder output = new StringBuilder();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 限制输出长度，避免日志过长
                    if (output.length() < 2048) {
                        output.append(line).append("\n");
                    }
                }
            }

            // 等待进程结束，设置超时
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                String reason = finished ? String.valueOf(process.exitValue()) : "timeout";
                logFailure(errorContext, Text.translatable("gui.extendednoteblock.rendering.log.failure.process",
                        reason, output.toString()));
                return false;
            }
            return true;
        } catch (IOException | InterruptedException e) {
            logFailure(errorContext, Text.literal(e.getMessage()));
            return false;
        }
    }

    /**
     * 生成资源包所需的元数据文件（`pack.mcmeta`）和声音定义文件（`sounds.json`）。
     *
     * @param packDir    资源包根目录。
     * @param soundsJson 包含所有声音定义的JsonObject。
     * @throws IOException 如果文件写入失败。
     */
    private void generateResourcePackFiles(Path packDir, JsonObject soundsJson) throws IOException {
        // 写入 sounds.json
        try (FileWriter writer = new FileWriter(
                packDir.resolve("assets").resolve(MOD_ID).resolve("sounds.json").toFile())) {
            GSON.toJson(soundsJson, writer);
        }
        // 写入 pack.mcmeta
        JsonObject packMeta = new JsonObject();
        JsonObject packSection = new JsonObject();
        packSection.addProperty("pack_format", 15); // 根据当前Minecraft版本调整
        packSection.addProperty("description", "Generated sounds for Extended Note Block Mod");
        packMeta.add("pack", packSection);
        try (FileWriter writer = new FileWriter(packDir.resolve("pack.mcmeta").toFile())) {
            GSON.toJson(packMeta, writer);
        }
    }

    /**
     * 向Minecraft客户端发出指令，使其重新加载所有资源包。
     * 这使得新生成的音效可以立即生效。
     */
    private void reloadResourcePacks() {
        MinecraftClient client = MinecraftClient.getInstance();
        String packId = this.outputPackDir.getFileName().toString();
        String packManagerName = RESOURCE_PACK_PREFIX + packId;

        client.getResourcePackManager().scanPacks();
        java.util.Set<String> enabledPacks = new java.util.HashSet<>(client.getResourcePackManager().getEnabledNames());
        enabledPacks.add(packManagerName);
        client.getResourcePackManager().setEnabledProfiles(new ArrayList<>(enabledPacks));

        // 在主线程执行资源重载
        client.execute(client::reloadResources);
    }

    /**
     * 记录一条普通日志消息。
     *
     * @param message 要记录的消息。
     */
    private void log(Text message) {
        onLogMessage.accept(message);
    }

    /**
     * 记录一条失败日志消息，并将`hasErrors`标志设置为true。
     *
     * @param context 错误的上下文信息。
     * @param reason  错误的具体原因。
     */
    private void logFailure(Text context, Text reason) {
        this.hasErrors.set(true);
        onLogMessage.accept(Text.translatable("gui.extendednoteblock.rendering.log.failure.format", context, reason));
    }
}