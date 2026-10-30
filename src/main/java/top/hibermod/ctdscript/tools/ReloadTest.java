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
 *   <li>首次加载成功（信号制度 + 交路 + ATO 三类一起）；</li>
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
        Files.writeString(root.resolve("signal/mini.py"), GOOD_SIGNAL);
        Files.writeString(root.resolve("timetable/line.py"), GOOD_SERVICE);
        Files.writeString(root.resolve("ato/mini.py"), GOOD_ATO);

        ScriptReloader reloader = new ScriptReloader(root);

        // ① 首次加载
        var r1 = reloader.loadInitial();
        ScriptReloader.freezeAll();
        failures += check("首次加载 3 个脚本全部成功", r1.failures().isEmpty() && r1.ok() == 3,
            r1.summary());
        int systemCount = SignalRegistries.systems().size();
        int serviceCount = Service.services().size();
        int recorded = ScriptRegistry.size();
        failures += check("脚本注册项已记录", recorded >= 3, "recorded=" + recorded);

        // ② 改坏 → 必须回滚
        Files.writeString(root.resolve("signal/mini.py"), BROKEN);
        var r2 = reloader.reload();
        failures += check("坏脚本导致重载失败", !r2.success(), r2.message());
        failures += check("回滚后制度数量不变", SignalRegistries.systems().size() == systemCount,
            "before=" + systemCount + " after=" + SignalRegistries.systems().size());
        failures += check("回滚后交路数量不变", Service.services().size() == serviceCount,
            "before=" + serviceCount + " after=" + Service.services().size());
        failures += check("脚本注册项数量恢复", ScriptRegistry.size() == recorded,
            "before=" + recorded + " after=" + ScriptRegistry.size());
        failures += check("回滚后注册表重新冻结",
            ScriptReloader.freezeState().contains("signal=true"), ScriptReloader.freezeState());

        // ③ 改好 → 重载成功
        Files.writeString(root.resolve("signal/mini.py"), GOOD_SIGNAL);
        var r3 = reloader.reload();
        failures += check("修好后重载成功", r3.success(), r3.message());
        failures += check("制度仍在", SignalRegistries.systems().size() == systemCount,
            String.valueOf(SignalRegistries.systems().size()));

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
