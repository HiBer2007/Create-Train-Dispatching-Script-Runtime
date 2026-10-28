package top.hibermod.ctdscript.tools;

import top.hibermod.ctdscript.ScriptRuntime;

/**
 * B6 验收：沙箱的四道禁令必须**真的**生效。
 *
 * <p>只写「我们配置了 allowCreateProcess(false)」不算数——配置对不对要靠故意越界来验。
 * 这里每一条都尝试真的突破一次，能突破就是 FAIL。
 */
public final class SandboxTest {

    private SandboxTest() {}

    public static void main(String[] args) {
        int failures = 0;

        try (ScriptRuntime rt = new ScriptRuntime()) {
            // ① import java
            failures += expectBlocked(rt, "import java 被拒",
                "import java.lang.System\nSystem.out.println('pwned')\n");

            // ② 按名字找类
            failures += expectBlocked(rt, "通过 polyglot 找类被拒",
                "import polyglot\npolyglot.eval(language='java', string='1+1')\n");

            // ③ 起进程
            failures += expectBlocked(rt, "subprocess 被拒",
                "import subprocess\nsubprocess.run(['cmd','/c','echo','pwned'])\n");

            // ④ 起线程
            failures += expectBlocked(rt, "创建线程被拒",
                "import threading\n"
                + "t = threading.Thread(target=lambda: None)\n"
                + "t.start()\n"
                + "t.join()\n");

            // ⑤ 文件写入
            failures += expectBlocked(rt, "写文件被拒",
                "open('ctd_sandbox_probe.txt','w').write('pwned')\n");

            // ⑥ 正常脚本仍然能跑（不能因为收紧沙箱把功能弄没了）
            try {
                // 只看「能不能跑完」：eval 返回最后一条语句的值，print() 返回 None
                rt.eval("x = 1 + 1\nprint(x)\n", "sandbox/ok.py");
                boolean ok = true;
                System.out.println((ok ? "  ok   " : "  FAIL ") + "普通脚本仍可执行");
                if (!ok) failures++;
            } catch (Throwable t) {
                System.out.println("  FAIL 普通脚本被误伤: " + t);
                failures++;
            }
        }

        System.out.println();
        if (failures > 0) {
            System.out.println("沙箱验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("沙箱验收通过：四道禁令全部生效，且未误伤正常脚本");
    }

    private static int expectBlocked(ScriptRuntime rt, String name, String script) {
        try {
            rt.eval(script, "sandbox/probe.py");
            System.out.println("  FAIL " + name + "  <- 竟然成功了");
            return 1;
        } catch (Throwable t) {
            String msg = String.valueOf(t.getMessage());
            System.out.println("  ok   " + name + "  (" + firstLine(msg) + ")");
            return 0;
        }
    }

    private static String firstLine(String s) {
        if (s == null) return "blocked";
        int nl = s.indexOf('\n');
        String line = nl > 0 ? s.substring(0, nl) : s;
        return line.length() > 90 ? line.substring(0, 90) + "..." : line;
    }
}
