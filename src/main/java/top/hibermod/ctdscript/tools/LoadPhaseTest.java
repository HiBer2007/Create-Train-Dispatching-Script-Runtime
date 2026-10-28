package top.hibermod.ctdscript.tools;

import top.hibermod.ctdscript.ScriptMode;
import top.hibermod.ctdscript.ScriptRuntime;

/**
 * B 阶段验收：定义类注册在重载阶段必须被**显式拒绝**。
 *
 * <p>静默忽略是最糟的处理方式——作者以为改了，实际没生效。
 */
public final class LoadPhaseTest {

    private LoadPhaseTest() {}

    /** 一个合法的最小信号制度：先注册机型，再注册制度（制度必须至少有一个机型）。 */
    private static final String SIGNAL_SCRIPT = """
        from ctd import signal

        t = signal.type("phasetest", "mini_t", "phasetest:mini")
        t.lamp(signal.RED)
        t.lamp(signal.GREEN)
        t.expresses(signal.STOP, 0)
        t.register()

        s = signal.system("phasetest", "mini", "phase test")
        s.type(t)
        s.register()
        """;

    private static final String KERNEL_SCRIPT = """
        from ctd import kernel

        class K:
            def on_frame(self, ctx, out):
                pass

        kernel.register("phasetest", "k", K())
        """;

    public static void main(String[] args) {
        int failures = 0;

        try (ScriptRuntime rt = new ScriptRuntime()) {
            // ① 启动阶段：信号制度可以注册
            ScriptMode.enter(ScriptMode.Phase.STARTUP);
            try {
                rt.eval(SIGNAL_SCRIPT, "phasetest/signal.py");
                System.out.println("  ok   启动阶段允许注册信号制度");
            } catch (Throwable t) {
                System.out.println("  FAIL 启动阶段被拒: " + t.getMessage());
                failures++;
            }

            // ② 重载阶段：同样的脚本必须被拒，且错误信息要能指导修正
            ScriptMode.enter(ScriptMode.Phase.RELOAD);
            try {
                // 换一组 id，避免被「重复注册」误判成本次要验的那条守卫
                rt.eval(SIGNAL_SCRIPT.replace("phasetest", "phasetest2"), "phasetest/signal2.py");
                System.out.println("  FAIL 重载阶段竟然允许注册信号制度");
                failures++;
            } catch (Throwable t) {
                String msg = String.valueOf(t.getMessage());
                boolean informative = msg.contains("startup-only");
                System.out.println((informative ? "  ok   " : "  FAIL ")
                    + (informative ? "重载阶段被拒且给出可操作提示" : "被拒但提示不明确: " + msg));
                if (!informative) failures++;
            }

            // ③ 重载阶段：算法仍可注册
            try {
                rt.eval(KERNEL_SCRIPT, "phasetest/kernel.py");
                System.out.println("  ok   重载阶段允许注册内核");
            } catch (Throwable t) {
                System.out.println("  FAIL 重载阶段内核被拒: " + t.getMessage());
                failures++;
            }
        }

        System.out.println();
        if (failures > 0) {
            System.out.println("加载阶段验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("加载阶段验收通过：定义类只在启动期可注册，算法类可热重载");
    }
}