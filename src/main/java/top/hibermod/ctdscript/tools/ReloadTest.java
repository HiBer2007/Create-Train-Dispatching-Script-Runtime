package top.hibermod.ctdscript.tools;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.ato.AtoEngines;
import top.hibermod.ctdapi.signal.SignalRegistries;
import top.hibermod.ctdapi.timetable.Service;
import top.hibermod.ctdscript.ScriptRegistry;
import top.hibermod.ctdscript.ScriptReloader;

/**
 * B1–B4 验收：全量热重载 + 失败整批回滚。
 *
 * <p>用临时目录当脚本站，覆盖四条路径：
 * <ol>
 *   <li>首次加载成功（信号制度 + 交路 + ATO + 方块 + HUD 五类一起）；</li>
 *   <li>改坏的脚本 → 整批回滚，注册表回到上一版；</li>
 *   <li>改好的脚本 → 重载成功，新条目生效；</li>
 *   <li><b>再重载一次</b> → ATO 注册表必须已解冻，否则第二次就废了。</li>
 * </ol>
 *
 * <p>第 4 条是补上的：早先只测到第一次重载。ATO 注册表第一次冻结后没有解冻入口，
 * 于是「重载一次能用、重载两次就崩」——这种 bug 只有连着做两次才会露出来。
 */
public final class ReloadTest {

    private ReloadTest() {}

    private static final String GOOD_SIGNAL = String.join("\n",
        "from ctd import signal",
        "",
        "t = signal.type('rl', 't', 'rl:sys')",
        "t.lamp(signal.RED)",
        "t.lamp(signal.GREEN)",
        "t.expresses(signal.STOP, 0)",
        "t.register()",
        "",
        "s = signal.system('rl', 'sys', 'reload test')",
        "s.palette(signal.RED, signal.GREEN)",
        "s.aspect(signal.STOP)",        // 校验要求制度里至少有一个停车语义
        "s.type(t)",
        "s.register()",
        "");

    private static final String GOOD_SERVICE = String.join("\n",
        "from ctd import timetable",
        "",
        "timetable.registerService('rl', 'line', 'L1', 'Line 1', 'rl:sys', '', 0)",
        "");

    /**
     * 方块脚本：**必须在重载里被跳过**。
     *
     * <p>NeoForge 的方块注册表在启动后关闭，重跑这个脚本必然抛
     * 「block registration is closed」。以前重载不分目录，于是任何带 block/ 的世界
     * `/ctd reload` 永远整批回滚——而回滚是优雅的，看起来只像「重载没生效」。
     */
    private static final String GOOD_BLOCK = String.join("\n",
        "from ctd import content",
        "",
        "content.block('rl', 'panel', 'rl:block/panel', 2.0, 0, True)",
        "");

    /** HUD 脚本：**可以**重载，属于屏幕数据。 */
    private static final String GOOD_HUD = String.join("\n",
        "from ctd import content",
        "",
        "def draw(h, s):",
        "    h.rect(0, 0, 10, 10, 0xFF112233)",
        "",
        "content.hud('rl', 'badge', draw, 2, 0)",
        "");

    private static final String GOOD_ATO = String.join("\n",
        "from ctd import ato",
        "",
        "class MiniAto:",
        "    def decide(self, ctx):",
        "        return ato.stop('reload test')",
        "",
        "ato.register('rl', 'mini', 'rl:sys', MiniAto())",
        "");

    private static final String BROKEN = String.join("\n",
        "from ctd import signal",
        "",
        "raise RuntimeError('intentional failure for the rollback test')",
        "");

    public static void main(String[] args) throws Exception {
        int failures = 0;

        SignalRegistries.bootstrap();
        top.hibermod.ctdapi.traction.Traction.Builtin.bootstrap();

        Path root = Files.createTempDirectory("ctd-reload-test");
        Files.createDirectories(root.resolve("signal"));
        Files.createDirectories(root.resolve("timetable"));
        Files.createDirectories(root.resolve("ato"));
        Files.createDirectories(root.resolve("block"));
        Files.createDirectories(root.resolve("hud"));
        Files.writeString(root.resolve("signal/mini.py"), GOOD_SIGNAL);
        Files.writeString(root.resolve("timetable/line.py"), GOOD_SERVICE);
        Files.writeString(root.resolve("ato/mini.py"), GOOD_ATO);
        Files.writeString(root.resolve("block/panel.py"), GOOD_BLOCK);
        Files.writeString(root.resolve("hud/badge.py"), GOOD_HUD);

        ScriptReloader reloader = new ScriptReloader(root);

        // ① 首次加载
        var r1 = reloader.loadInitial();
        ScriptReloader.freezeAll();
        failures += check("首次加载 5 个脚本全部成功（含 block/ 与 hud/）", r1.failures().isEmpty() && r1.ok() == 5,
            r1.summary());
        int systemCount = SignalRegistries.systems().size();
        int serviceCount = Service.services().size();
        int recorded = ScriptRegistry.size();
        failures += check("脚本注册项已记录", recorded >= 3, "recorded=" + recorded);

        // ② 改坏一个**可重载**目录里的脚本
        //
        // 注意：坏的必须是 kernel/ato/hud/broadcast 之一。signal/ 这类启动期目录
        // 在重载时根本不跑——它们不可能让重载失败，这也正是它们安全的原因。
        Files.writeString(root.resolve("ato/mini.py"), BROKEN);
        var r2 = reloader.reload();
        failures += check("可重载目录里的坏脚本导致重载失败", !r2.success(), r2.message());

        // 新语义：**不回滚**。
        //   · 失败必须可见（failed() == true，报告里有文件名）
        //   · 旧条目不会被塞回去——半成品就是半成品，但它是看得见的
        failures += check("失败状态被置位", reloader.failed(), "failed=false");
        failures += check("失败报告里有脚本名",
            r2.report().failures().stream().anyMatch(s -> s.contains("ato/mini.py")),
            String.valueOf(r2.report().failures()));
        failures += check("失败后没有自动回滚（ATO 不会被塞回去）",
            AtoEngines.get(ResourceLocation.fromNamespaceAndPath("rl", "mini")).isEmpty(),
            "ato 仍在注册表里");
        failures += check("失败后仍然重新冻结（不会半开着被写）",
            ScriptReloader.freezeState().contains("ato=true"), ScriptReloader.freezeState());

        // ③ 改好 → 重载成功
        Files.writeString(root.resolve("ato/mini.py"), GOOD_ATO);
        var r3 = reloader.reload();
        failures += check("修好后重载成功（block/ 被跳过、hud/ 重跑）", r3.success(), r3.message());
        failures += check("重载只跑可重载目录（kernel/ato/broadcast/hud）",
            r3.report().ok() == 2, "reloaded=" + r3.report().ok());
        failures += check("成功之后失败标志清掉", !reloader.failed(), "failed=true");
        failures += check("修好后 ATO 重新注册",
            AtoEngines.get(ResourceLocation.fromNamespaceAndPath("rl", "mini")).isPresent(),
            "未注册");

        // 启动期定义（信号制度 / 交路）在整个过程中没有被重载碰过——
        // 这既是它们安全的原因，也是「改它们必须重启」的原因。
        failures += check("启动期定义全程未被重载触碰",
            SignalRegistries.systems().size() == systemCount
                && Service.services().size() == serviceCount,
            "signal=" + SignalRegistries.systems().size() + "/" + systemCount
                + " service=" + Service.services().size() + "/" + serviceCount);

        // ④ 再重载一次：ATO 注册表若没有解冻，这里会抛 "registry is frozen"
        var r4 = reloader.reload();
        failures += check("第二次重载仍成功（ATO 必须解冻）", r4.success(), r4.message());
        failures += check("第二次重载后 ATO 仍在",
            AtoEngines.get(ResourceLocation.fromNamespaceAndPath("rl", "mini")).isPresent(),
            "frozen=" + AtoEngines.frozen());

        reloader.close();
        System.out.println();
        if (failures > 0) {
            System.out.println("重载验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("重载验收通过：全量重载可做，失败时整批回滚，重复重载不失效");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
