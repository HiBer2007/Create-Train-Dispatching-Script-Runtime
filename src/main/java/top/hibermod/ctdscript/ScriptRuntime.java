package top.hibermod.ctdscript;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import top.hibermod.ctdscript.bridge.SignalApi;

/**
 * GraalPy 运行时：一个 Context、一套沙箱、一个 watchdog。
 *
 * <h2>沙箱边界</h2>
 * <ul>
 *   <li>{@code allowHostAccess(EXPLICIT)} —— 只有标了 {@code @HostAccess.Export} 的成员可见；</li>
 *   <li>{@code allowHostClassLookup(-> false)} —— 脚本不能 {@code import java}，也不能按名找类；</li>
 *   <li>不提供 IO / 进程 / 线程能力。</li>
 * </ul>
 *
 * <h2>注入的 {@code ctd} 包</h2>
 * GraalPy 的 bindings 是全局变量而不是模块，所以创建 Context 后跑一段 bootstrap，
 * 把桥接对象组装成真正的 {@code ctd} 包并塞进 {@code sys.modules}。
 * 这样脚本可以写 {@code from ctd import signal}。
 */
public final class ScriptRuntime implements AutoCloseable {

    /** 单次脚本调用的默认时间上限。 */
    public static final long DEFAULT_TIMEOUT_MS = 15_000L;

    private static final String BOOTSTRAP = """
        import sys, types

        _mod = types.ModuleType('ctd')
        _mod.signal    = _ctd_signal
        _mod.kernel    = _ctd_kernel
        _mod.ato       = _ctd_ato
        _mod.device    = _ctd_device
        _mod.timetable = _ctd_timetable
        _mod.operation = _ctd_operation
        sys.modules['ctd'] = _mod
        del _mod
        """;

    private final Context context;
    private final SignalApi signalApi = new SignalApi();
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile long timeoutMs = DEFAULT_TIMEOUT_MS;
    private Thread watchdog;
    private volatile boolean closed;

    public ScriptRuntime() {
        // EXPLICIT 只管「成员可见性」：没标 @Export 的方法不可达。
        // 但集合访问是另一组开关——不开的话，返回的 java.util.List 在 Python 里
        // 不是 iterable，脚本一写 for 就炸（TypeError: foreign object is not iterable）。
        HostAccess access = HostAccess.newBuilder(HostAccess.EXPLICIT)
            .allowListAccess(true)
            .allowMapAccess(true)
            .allowArrayAccess(true)
            .build();

        this.context = Context.newBuilder("python")
            .allowHostAccess(access)
            // 脚本不能 import java / 按名字找类
            .allowHostClassLookup(name -> false)
            // 不起进程、不起线程、不碰文件
            .allowCreateProcess(false)
            .allowCreateThread(false)
            .option("python.ForceImportSite", "false")
            // 注意：Context 里的选项名是 engine.*，没有 polyglot. 前缀（那是 JVM 系统属性用的）
            .option("engine.WarnInterpreterOnly", "false")
            .build();

        Value bindings = context.getBindings("python");
        bindings.putMember("_ctd_signal", signalApi);
        bindings.putMember("_ctd_kernel", top.hibermod.ctdscript.bridge.KernelApi.INSTANCE);
        bindings.putMember("_ctd_ato", top.hibermod.ctdscript.bridge.AtoApi.INSTANCE);
        bindings.putMember("_ctd_device", top.hibermod.ctdscript.bridge.DeviceApi.INSTANCE);
        bindings.putMember("_ctd_timetable", top.hibermod.ctdscript.bridge.TimetableApi.INSTANCE);
        bindings.putMember("_ctd_operation", top.hibermod.ctdscript.bridge.OperationApi.INSTANCE);

        context.eval(Source.newBuilder("python", BOOTSTRAP, "<ctd-bootstrap>").buildLiteral());

        startWatchdog();
    }

    /** 执行一段脚本源码。 */
    public Value eval(String source, String name) {
        return eval(Source.newBuilder("python", source, name).buildLiteral());
    }

    /** 执行一个已构建的 Source。 */
    public Value eval(Source source) {
        if (closed) throw new IllegalStateException("script runtime is closed");
        busy.set(true);
        try {
            return context.eval(source);
        } catch (PolyglotException e) {
            if (e.isInterrupted()) {
                throw new ScriptTimeoutException(timeoutMs, e);
            }
            throw new ScriptException(e);
        } finally {
            busy.set(false);
        }
    }

    /** 调用脚本里的一个函数（用于 on_frame / decide 这类热路径）。 */
    public void invoke(Value function, Object... args) {
        if (closed) throw new IllegalStateException("script runtime is closed");
        busy.set(true);
        try {
            function.executeVoid(args);
        } catch (PolyglotException e) {
            if (e.isInterrupted()) throw new ScriptTimeoutException(timeoutMs, e);
            throw new ScriptException(e);
        } finally {
            busy.set(false);
        }
    }

    public SignalApi signal() {
        return signalApi;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = Math.max(50, timeoutMs);
    }

    /**
     * 看门狗：脚本跑超时就中断 Context。
     *
     * <p>这不是抢占式中断——它能停住脚本，但**不保证停在安全点**。
     * 所以调用方还要做计时统计：连续超限就禁用该脚本（见 {@code ScriptLoader}）。
     */
    private void startWatchdog() {
        watchdog = new Thread(() -> {
            while (!closed) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    return;
                }
                if (busy.get()) {
                    long slept = 0;
                    while (busy.get() && slept < timeoutMs && !closed) {
                        try {
                            Thread.sleep(Math.min(10, timeoutMs - slept));
                            slept += 10;
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    if (busy.get() && !closed) {
                        try {
                            context.interrupt(Duration.ofMillis(1));
                        } catch (java.util.concurrent.TimeoutException e) {
                            // 1ms 内没打断成功，说明脚本已经自己退出或卡在不可中断点；
                            // 连续超限的禁用逻辑由调用方统计，这里不重复处理
                        }
                    }
                }
            }
        }, "ctd-script-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    @Override
    public void close() {
        closed = true;
        if (watchdog != null) watchdog.interrupt();
        try {
            context.close();
        } catch (Exception ignored) {
            // 关闭期异常不值得让关服失败
        }
    }

    /** 脚本自身的错误（语法、运行时异常）。 */
    public static final class ScriptException extends RuntimeException {
        public ScriptException(Throwable cause) {
            super(cause);
        }

        public String pretty() {
            Throwable c = getCause();
            if (c instanceof PolyglotException pe) {
                return pe.getMessage() + " @ " + pe.getSourceLocation();
            }
            return String.valueOf(c);
        }
    }

    /** 脚本超时。 */
    public static final class ScriptTimeoutException extends RuntimeException {
        public ScriptTimeoutException(long ms, Throwable cause) {
            super("script exceeded " + ms + "ms", cause);
        }
    }
}
