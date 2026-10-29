package top.hibermod.ctdscript.broadcast;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 广播的独立日志（**UTF-8**）。
 *
 * <h2>为什么不能只写 {@code System.out}</h2>
 * 实测：Windows 上把输出重定向到文件时，JVM 的 {@code stdout} 编码承载不了中文，
 * 「测试站」会变成 {@code ?????}，甚至 {@code U+FFFD} 替换符。
 * 广播文案恰恰全是中文——只靠控制台日志等于**验证不了、也排查不了**。
 *
 * <p>所以广播单开一份文件，显式 UTF-8。它在
 * {@code config/ctd/logs/broadcast.log}，运维可以直接用记事本打开。
 *
 * <h2>为什么不做轮转</h2>
 * 广播量很小（一站几条到几十条），先不引入轮转这种复杂度；
 * 真需要时再加，键是这份文件的位置。
 */
public final class BroadcastLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static volatile Path file;
    private static volatile boolean disabled;

    private BroadcastLog() {}

    /** 由引擎在构造期指定路径。 */
    public static void configure(Path configDir) {
        try {
            Path dir = configDir.resolve("logs");
            Files.createDirectories(dir);
            file = dir.resolve("broadcast.log");
        } catch (IOException e) {
            disabled = true;
            System.err.println("[ctdscript] cannot create broadcast log: " + e);
        }
    }

    public static void write(String line) {
        if (line == null) return;

        // 控制台也照写一份（英文环境或编码正常时方便看）
        System.out.println("[ctdscript] " + line);

        Path target = file;
        if (disabled || target == null) return;

        try {
            String entry = "[" + LocalTime.now().format(TIME) + "] " + line
                + System.lineSeparator();
            Files.write(target, entry.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            disabled = true;                 // 写不动就别每帧再试
            System.err.println("[ctdscript] broadcast log write failed: " + e);
        }
    }

    /** 当前日志路径（自检用）。 */
    public static String path() {
        Path p = file;
        return p == null ? "(unset)" : p.toString();
    }
}
