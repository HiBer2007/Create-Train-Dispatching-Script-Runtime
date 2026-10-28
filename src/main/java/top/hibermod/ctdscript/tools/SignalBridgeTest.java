package top.hibermod.ctdscript.tools;

import top.hibermod.ctdapi.ApiCatalog;
import top.hibermod.ctdapi.signal.SignalRegistries;
import top.hibermod.ctdscript.ScriptRuntime;

/**
 * P0 验收：脚本能否真的把一套信号制度注册进 API。
 *
 * <p>不启动 Minecraft，只验证「Python → 桥接 → SignalRegistries」这条链路。
 */
public final class SignalBridgeTest {

    private SignalBridgeTest() {}

    private static final String SCRIPT = """
        from ctd import signal

        # 一套最小制度：红灯停，绿灯行
        sys = signal.system("scripttest", "mini", "脚本测试制度")
        t = signal.type("scripttest", "mini_2", sys.id())
        t.lamp(signal.RED)
        t.lamp(signal.GREEN)
        t.expresses(signal.STOP, 0)
        t.expresses(signal.PROCEED, 1)
        t.semantics(signal.BLOCK)
        t.register()

        sys.palette(signal.RED, signal.GREEN)
        sys.aspect(signal.STOP)
        sys.aspect(signal.PROCEED)
        sys.type(t)
        sys.fallback(t)
        sys.register()
        """;

    public static void main(String[] args) {
        int failures = 0;

        SignalRegistries.bootstrap();

        try (ScriptRuntime rt = new ScriptRuntime()) {
            rt.eval(SCRIPT, "scripttest/mini.py");
            System.out.println("  ok   脚本执行完成");
        } catch (Throwable t) {
            System.out.println("  FAIL 脚本执行: " + t);
            t.printStackTrace();
            System.exit(1);
        }

        // 断言：制度、机型都在注册表里
        var system = SignalRegistries.system(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("scripttest", "mini"));
        var type = SignalRegistries.type(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("scripttest", "mini_2"));

        if (system.isEmpty()) {
            System.out.println("  FAIL 制度未注册");
            failures++;
        } else {
            System.out.println("  ok   制度已注册: " + system.get().id()
                + " (" + system.get().typeIds().size() + " 机型, "
                + system.get().aspectIds().size() + " 语义)");
        }

        if (type.isEmpty()) {
            System.out.println("  FAIL 机型未注册");
            failures++;
        } else {
            System.out.println("  ok   机型已注册: " + type.get().id()
                + " (" + type.get().lampCount() + " 灯)");
            if (!type.get().supports(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ctdapi", "stop"))) {
                System.out.println("  FAIL 机型未表达 stop");
                failures++;
            } else {
                System.out.println("  ok   语义映射正确");
            }
        }

        // freeze 必须通过（此时内置制度也在，一起校验）
        try {
            ApiCatalog.freezeAll();
            System.out.println("  ok   freezeAll() 通过");
        } catch (IllegalStateException e) {
            System.out.println("  FAIL freezeAll: " + e.getMessage());
            failures++;
        }

        System.out.println();
        System.out.println("  registry entries: " + ApiCatalog.entries().size());
        for (var e : ApiCatalog.entries()) {
            if (e.id().getNamespace().equals("scripttest")) {
                System.out.println("    " + e);
            }
        }

        System.out.println();
        if (failures > 0) {
            System.out.println("P0 验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("P0 验收通过：Python 注册的信号制度已进入 API 注册表，并通过 freeze 校验");
    }
}
