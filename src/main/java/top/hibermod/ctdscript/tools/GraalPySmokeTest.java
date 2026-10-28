package top.hibermod.ctdscript.tools;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * 不启动 Minecraft 的烟测：只验证 GraalPy 能在当前 JVM 上跑。
 *
 * <p>验证四件事：
 * <ol>
 *   <li>能创建 python 上下文（引擎在 stock OpenJDK 上可用，只是解释执行）；</li>
 *   <li>能执行脚本；</li>
 *   <li>{@code import java} 被挡住（沙箱前提）；</li>
 *   <li>{@code @HostAccess.Export} 的对象能被脚本调用。</li>
 * </ol>
 */
public final class GraalPySmokeTest {

    private GraalPySmokeTest() {}

    /** 只有显式 @Export 的成员才可见。 */
    public static final class Greeter {
        @HostAccess.Export
        public String hello(String name) {
            return "hello " + name;
        }

        public String secret() {
            return "should not be reachable";
        }
    }

    public static void main(String[] args) {
        int failures = 0;

        try (Context ctx = Context.newBuilder("python")
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowHostClassLookup(name -> false)
                .option("python.ForceImportSite", "false")
                .build()) {

            // ① 基本执行
            Value v = ctx.eval("python", "1 + 2 * 3");
            fail("python 执行", v.asInt() == 7, "got " + v);
            System.out.println("① python " + ctx.getEngine().getVersion() + " -> 1+2*3 = " + v.asInt());

            // ② f-string 与标准库
            Value s = ctx.eval("python", "'%s-%d' % ('ctd', 7)");
            fail("字符串格式化", "ctd-7".equals(s.asString()), "got " + s);

            // ③ 沙箱：import java 必须失败
            boolean blocked;
            try {
                ctx.eval("python", "import java.lang.System");
                blocked = false;
            } catch (Exception e) {
                blocked = true;
            }
            fail("import java 被拦截", blocked, "沙箱失效");

            // ④ 宿主对象：只有 @Export 可见
            Value bindings = ctx.getBindings("python");
            bindings.putMember("greeter", new Greeter());

            Value ok = ctx.eval("python", "greeter.hello('world')");
            fail("调用 @Export 方法", "hello world".equals(ok.asString()), "got " + ok);

            boolean secretBlocked;
            try {
                ctx.eval("python", "greeter.secret()");
                secretBlocked = false;
            } catch (Exception e) {
                secretBlocked = true;
            }
            fail("非 @Export 方法不可见", secretBlocked, "HostAccess.EXPLICIT 失效");

            // ⑤ 注入模块的可行性：用 bootstrap 片段把 bindings 包装成可 import 的包
            ctx.eval("python", """
                import sys, types
                m = types.ModuleType('ctd')
                m.greeter = greeter
                sys.modules['ctd'] = m
                """);
            Value imported = ctx.eval("python", "from ctd import greeter; greeter.hello('module')");
            fail("ctd 包可 import", "hello module".equals(imported.asString()), "got " + imported);
        } catch (Throwable t) {
            System.out.println("!! GraalPy 初始化失败: " + t);
            t.printStackTrace();
            failures++;
        }

        System.out.println();
        if (failures > 0) {
            System.out.println("烟测失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("烟测通过：GraalPy 可用，沙箱与包注入均按预期工作");
    }

    private static void fail(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        if (!ok) {
            throw new IllegalStateException("smoke test failed: " + name);
        }
    }
}
