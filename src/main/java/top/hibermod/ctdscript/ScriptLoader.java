package top.hibermod.ctdscript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 扫描并执行脚本目录。
 *
 * <p>目录约定（顺序即执行顺序，因为后面的可能引用前面的 id）：
 * <pre>
 * config/ctd/scripts/
 * ├── signal/     信号制度、机型、语义
 * ├── device/     设备类型
 * ├── timetable/  交路
 * ├── kernel/     调度内核
 * └── ato/        ATO 引擎
 * </pre>
 */
public final class ScriptLoader {

    /** 执行顺序固定。 */
    private static final String[] ORDER = { "signal", "device", "timetable", "kernel", "ato" };

    private final ScriptRuntime runtime;
    private final Path root;

    public ScriptLoader(ScriptRuntime runtime, Path root) {
        this.runtime = runtime;
        this.root = root;
    }

    /** 一次加载的结果。 */
    public record Report(int total, int ok, List<String> failures) {
        public String summary() {
            return summary("scripts");
        }

        public String summary(String where) {
            if (total == 0) return "no scripts found (" + where + ")";
            StringBuilder sb = new StringBuilder(ok + "/" + total + " scripts loaded");
            for (String f : failures) sb.append("\n  - ").append(f);
            return sb.toString();
        }
    }

    /** 全量加载。单个脚本失败不影响其它脚本（但失败的注册不会落库）。 */
    public Report loadAll() {
        return loadAll(List.of(ORDER));
    }

    /**
     * 只加载指定子目录。
     *
     * <p>热重载用：只有 {@code kernel} 与 {@code ato} 支持替换，
     * 其它注册表（信号 / 设备 / 交路）一旦冻结就无法增量修改。
     */
    public Report loadAll(List<String> dirs) {
        List<Path> files = new ArrayList<>();
        for (String dir : dirs) {
            Path p = root.resolve(dir);
            if (!Files.isDirectory(p)) continue;
            try (Stream<Path> s = Files.walk(p)) {
                s.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".py"))
                    .sorted()
                    .forEach(files::add);
            } catch (IOException e) {
                return new Report(0, 0, List.of("cannot scan " + p + ": " + e.getMessage()));
            }
        }

        int ok = 0;
        List<String> failures = new ArrayList<>();
        for (Path f : files) {
            String name = root.relativize(f).toString().replace('\\', '/');
            try {
                String source = Files.readString(f, StandardCharsets.UTF_8);
                runtime.eval(source, "ctd/" + name);
                ok++;
            } catch (ScriptRuntime.ScriptTimeoutException e) {
                failures.add(name + " — timeout (" + e.getMessage() + ")");
            } catch (ScriptRuntime.ScriptException e) {
                failures.add(name + " — " + e.pretty());
            } catch (Throwable t) {
                failures.add(name + " — " + t);
            }
        }
        return new Report(files.size(), ok, failures);
    }
}
