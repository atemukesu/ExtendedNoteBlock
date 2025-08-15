package com.atemukesu.extendednoteblock.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 一个用于检测所需外部可执行文件（如 ffmpeg 和 fluidsynth）是否存在的工具类。
 * 它通过在系统 PATH 中尝试执行命令来验证其可用性。
 * 检测过程是异步的，以避免阻塞主线程。
 */
public class PathDetector {
    /**
     * 用于记录日志的 SLF4J Logger 实例。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("PathDetector");

    /**
     * 异步检测所需的可执行文件。
     * <p>
     * 此方法会启动一个新线程来检查 'ffmpeg' 和 'fluidsynth' 是否在系统的 PATH 环境变量中可用。
     * 检测完成后，会通过提供的回调函数返回一个映射，其中包含所有已找到的可执行文件的名称。
     * 这样做是为了防止在等待外部进程响应时，游戏主线程被阻塞，从而避免游戏卡顿。
     * </p>
     *
     * @param callback 一个回调函数，在检测完成后被调用。它接收一个 Map 作为参数，
     *                 其中键是可执行文件的名称（如 "ffmpeg"），值是用于执行的命令（也是 "ffmpeg"）。
     */
    public static void detectExecutablesAsync(Consumer<Map<String, String>> callback) {
        // 启动一个新线程来执行耗时的检测任务
        new Thread(() -> {
            Map<String, String> foundPaths = new HashMap<>();

            // 检查 ffmpeg
            if (isCommandAvailable("ffmpeg", "-version")) {
                foundPaths.put("ffmpeg", "ffmpeg");
                LOGGER.info("Found 'ffmpeg' in system PATH.");
            }

            // 检查 fluidsynth
            if (isCommandAvailable("fluidsynth", "-V")) {
                foundPaths.put("fluidsynth", "fluidsynth");
                LOGGER.info("Found 'fluidsynth' in system PATH.");
            }

            callback.accept(foundPaths);
        }).start();
    }

    /**
     * 检查指定的命令是否在当前系统环境中可用。
     * <p>
     * 该方法通过 {@link ProcessBuilder} 尝试执行给定的命令。如果进程成功启动并以退出码 0 结束，
     * 则认为该命令是可用的。任何异常（如 {@link IOException}，通常表示命令未找到）
     * 或非零的退出码都将被视为命令不可用。
     * </p>
     *
     * @param command 要检查的命令名称（例如 "ffmpeg"）。
     * @param args    传递给命令的参数（例如 "-version"），用于触发一个简单、无害的操作使其正常退出。
     * @return 如果命令成功执行（退出码为 0），则返回 {@code true}；否则返回 {@code false}。
     */
    private static boolean isCommandAvailable(String command, String args) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command, args);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            LOGGER.debug("Command '{}' not found or failed to execute.", command);
            return false;
        }
    }
}