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
    /**
     * 热重载只跑这些目录。
     *
     * <p>{@code hud} 在列表里，因为它是屏幕数据、没有注册表约束；
     * {@code signal} / {@code device} / {@code timetable} / {@code block} 不在，
     * 它们必须在启动期一次成型。
     */
    public static final java.util.List<String> RELOADABLE_DIRS =
        java.util.List.of("kernel", "ato", "broadcast", "hud");

    public Result reload() {
        List<ScriptRegistry.Entry> snapshot = ScriptRegistry.snapshot();
        try {
            closeRuntime();

            // ① 解冻并撤掉上一批脚本注册项
            unfreezeAll();

            // 内核与 ATO 引擎必须先撤销：它们不经过 ScriptRegistry，
            // 换 id 之后旧的那份会永远留在注册表里，继续用绑在
            // 已关闭 Python 运行时上的对象执行（表现为日志里两个内核同时 active）。
            top.hibermod.ctdscript.bridge.KernelApi.INSTANCE.reset();
        top.hibermod.ctdscript.bridge.BroadcastApi.INSTANCE.reset();
            top.hibermod.ctdscript.bridge.AtoApi.INSTANCE.reset();

            int removed = ScriptRegistry.removeAll(snapshot);

            // ② 新运行时跑全量
            runtime = new ScriptRuntime();
            // 只重跑**可重载**目录。
            //
            // signal/device/timetable/block 属于启动期定义：方块在 NeoForge 注册表关闭后
            // 根本加不进去，重跑它们必然是失败 + 整批回滚。以前不分目录，所以任何带
            // block/ 或 hud/ 脚本的世界里 `/ctd reload` **永远失败**，
            // 而失败信息看起来像「某个脚本写错了」。
            ScriptLoader.Report report = new ScriptLoader(runtime, root).loadAll(RELOADABLE_DIRS);

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

        // ATO 与能源提供方也必须在列：漏掉任何一个，第一次重载能用、
        // 第二次起脚本全部加载失败，而症状看起来像「脚本写错了」。
        // HUD 不是方块：重载要重跑 hud/ 下的脚本，所以只放开这一半。
        // 不放开的话，任何带 hud 脚本的世界里 `/ctd reload` 都会整批回滚——
        // 而且回滚本身是成功的，从外面看只像「重载没生效」。
        top.hibermod.ctdscript.content.ContentRegistry.reopenHuds();

        top.hibermod.ctdapi.ato.AtoEngines.unfreeze();
        top.hibermod.ctdapi.interop.Interop.unfreeze();
    }

    public static void freezeAll() {
        top.hibermod.ctdscript.content.ContentRegistry.sealHuds();
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
            + " service=" + top.hibermod.ctdapi.timetable.Service.isFrozen()
            + " ato=" + top.hibermod.ctdapi.ato.AtoEngines.frozen()
            + " interop=" + top.hibermod.ctdapi.interop.Interop.frozen();
    }
}
