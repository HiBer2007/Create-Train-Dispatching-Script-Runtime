package top.hibermod.ctdscript;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.hibermod.ctdapi.ApiCatalog;

/**
 * 脚本重载器：**不依赖 {@code @Mod} 主类**，因此可以离线测试。
 *
 * <h2>为什么要单独一个类</h2>
 * 重载是这个模组里最容易出错的逻辑（解冻、撤销、重跑、回滚、重冻结五步），
 * 挂在 {@code @Mod} 类上就只能进游戏才能验。抽出来之后，
 * 一套临时目录 + 两个脚本就能覆盖成功与回滚两条路径。
 *
 * <h2>回滚的语义</h2>
 * <b>全有或全无。</b>只要有一个脚本失败，整批退回上一版。
 * 逐文件回滚会留下「一半新一半旧」的注册表，那种状态比完全不重载更难排查。
 */
public final class ScriptReloader {

    private static final Logger LOG = LoggerFactory.getLogger("ctdscript");

    /** 重载结果。 */
    public record Result(boolean success, ScriptLoader.Report report, int restored) {
        public String message() {
            return (success ? "reloaded " : "ROLLED BACK ") + report.summary()
                + ", restored=" + restored;
        }
    }

    private final Path root;
    private ScriptRuntime runtime;

    public ScriptReloader(Path root) {
        this.root = root;
    }

    public ScriptRuntime runtime() {
        return runtime;
    }

    /** 启动期首次加载：不做回滚（没有「上一版」可言）。 */
    public ScriptLoader.Report loadInitial() {
        closeRuntime();
        runtime = new ScriptRuntime();
        return new ScriptLoader(runtime, root).loadAll();
    }

    /**
     * 全量重载。五类脚本全部重跑；任一失败则整批回滚。
     */
    public Result reload() {
        List<ScriptRegistry.Entry> snapshot = ScriptRegistry.snapshot();
        try {
            closeRuntime();

            // ① 解冻并撤掉上一批脚本注册项
            unfreezeAll();
            int removed = ScriptRegistry.removeAll(snapshot);

            // ② 新运行时跑全量
            runtime = new ScriptRuntime();
            ScriptLoader.Report report = new ScriptLoader(runtime, root).loadAll();

            // ③ 有失败 → 整批回滚
            if (!report.failures().isEmpty()) {
                unfreezeAll();
                ScriptRegistry.removeAll(ScriptRegistry.snapshot());
                int restored = ScriptRegistry.restore(snapshot);
                freezeAll();
                LOG.warn("reload failed ({} failures), rolled back {} registrations",
                    report.failures().size(), restored);
                return new Result(false, report, restored);
            }

            // ④ 成功 → 重新冻结
            freezeAll();
            LOG.info("reloaded {} scripts ({} replaced)", report.ok(), removed);
            return new Result(true, report, report.ok());
        } catch (Throwable t) {
            LOG.error("reload crashed, rolling back", t);
            if (runtime != null) {
                runtime.close();
                runtime = null;
            }
            try {
                unfreezeAll();
                ScriptRegistry.removeAll(ScriptRegistry.snapshot());
                int restored = ScriptRegistry.restore(snapshot);
                freezeAll();
                return new Result(false,
                    new ScriptLoader.Report(0, 0, List.of(String.valueOf(t))), restored);
            } catch (Throwable t2) {
                LOG.error("rollback itself failed", t2);
                return new Result(false,
                    new ScriptLoader.Report(0, 0, List.of(String.valueOf(t))), 0);
            }
        }
    }

    public void close() {
        closeRuntime();
    }

    private void closeRuntime() {
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }

    // 注册表冻结状态是全静态的，所以这里必须是静态操作
    public static void unfreezeAll() {
        top.hibermod.ctdapi.signal.SignalRegistries.unfreeze();
        top.hibermod.ctdapi.device.Devices.unfreeze();
        top.hibermod.ctdapi.timetable.Service.unfreeze();
    }

    public static void freezeAll() {
        try {
            ApiCatalog.freezeAll();
        } catch (IllegalStateException e) {
            LOG.error("registry validation failed after reload: {}", e.getMessage());
        }
    }

    /** 供测试与命令输出：各类注册表当前是否冻结。 */
    public static String freezeState() {
        return "signal=" + top.hibermod.ctdapi.signal.SignalRegistries.isFrozen()
            + " device=" + top.hibermod.ctdapi.device.Devices.isFrozen()
            + " service=" + top.hibermod.ctdapi.timetable.Service.isFrozen();
    }
}
