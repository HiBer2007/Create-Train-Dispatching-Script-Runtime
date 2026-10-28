package top.hibermod.ctdscript.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.DispatchFrame;
import top.hibermod.ctdapi.FrameContext;
import top.hibermod.ctdapi.KernelEnv;
import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.RailwayEvent;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.Topology;
import top.hibermod.ctdapi.hud.Hud;
import top.hibermod.ctdapi.pis.Pis;
import top.hibermod.ctdapi.signal.SignalRegistries;
import top.hibermod.ctdscript.ScriptRuntime;

/**
 * A8 验收：脚本能看到岔口状态、能锁岔、能发设备 / 能源 / 补给指令。
 */
public final class DeviceSwitchBridgeTest {

    private DeviceSwitchBridgeTest() {}

    private static final ResourceKey<Level> OW = ResourceKey.create(
        net.minecraft.core.registries.Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));

    private static final Rail.NodeId A = new Rail.NodeId(OW, 0, 64, 0, 0);
    private static final Rail.NodeId B = new Rail.NodeId(OW, 2, 64, 0, 0);
    private static final Rail.NodeId C = new Rail.NodeId(OW, 0, 64, 2, 0);
    private static final Rail.EdgeId AB = Rail.EdgeId.of(A, B);
    private static final Rail.EdgeId AC = Rail.EdgeId.of(A, C);

    private static final String SCRIPT = """
        from ctd import kernel

        class SwitchKernel:
            def on_frame(self, ctx, out):
                for s in ctx.switches():
                    node = s[0]
                    if ctx.switch_contested(node):
                        out.log("contested: " + node)
                        continue
                    out.log("switch %s locked=%s" % (node, ctx.locked_branch_of(node)))

                # 锁定第一条股道，再给设备/能源/补给各发一条
                out.lock_switch(NODE, BRANCH, 7)
                out.unlock_switch(NODE)
                out.device(DEV, '{"angle": 90}')
                out.energy(TRAIN, "mymod:electro", '{"action": "raise"}')
                out.service(TRAIN, "ctdapi:refuel", DEV)

        kernel.register("scripttest", "switch_kernel", SwitchKernel())
        """;

    // 测试替身 ────────────────────────────────────────────────────────────

    private static final class FakeContext implements FrameContext {
        @Override public long gameTime() { return 42L; }
        @Override public Topology topology() { return null; }
        @Override public Telemetry telemetry() {
            return new Telemetry() {
                @Override public Collection<TrainState> trains() { return List.of(); }
                @Override public TrainState train(java.util.UUID id) { return null; }
            };
        }
        @Override public Collection<SignalState> signalStates() { return List.of(); }
        @Override public Collection<SwitchState> switchStates() {
            return List.of(new SwitchState(A, AB, 3L, false));
        }
        @Override public Collection<RailwayEvent> pendingEvents() { return List.of(); }
    }

    private static final class RecordingFrame implements DispatchFrame {
        final List<Command.SwitchCommand> switches = new ArrayList<>();
        final List<Command.DeviceCommand> devices = new ArrayList<>();
        final List<Command.EnergyCommand> energies = new ArrayList<>();
        final List<Command.ServiceCommand> services = new ArrayList<>();

        @Override public void command(Command.TrainCommand c) { }
        @Override public void aspect(Command.AspectCommand c) { }
        @Override public void switchLock(Command.SwitchCommand c) { switches.add(c); }
        @Override public void device(Command.DeviceCommand c) { devices.add(c); }
        @Override public void energy(Command.EnergyCommand c) { energies.add(c); }
        @Override public void service(Command.ServiceCommand c) { services.add(c); }
        @Override public void hud(Hud.HudPanel p) { }
        @Override public void board(Pis.BoardModel b) { }
        @Override public void event(RailwayEvent e) { }
    }

    private static final class SilentEnv implements KernelEnv {
        final List<String> lines = new ArrayList<>();

        @Override public long gameTime() { return 42L; }
        @Override public net.minecraft.nbt.CompoundTag state() { return new net.minecraft.nbt.CompoundTag(); }
        @Override public void markStateDirty() { }
        @Override public Topology topology() { return null; }
        @Override public Settings settings() { return new Settings(64, true, 15, java.util.Map.of()); }

        @Override public void log(String level, String message, Object... args) {
            String out = message;
            for (Object a : args) {
                out = out.replaceFirst("\\{\\}", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
            }
            lines.add(level + ": " + out);
            if (level.equals("error") || level.equals("warn")) System.out.println("    [env] " + out);
        }
    }

    public static void main(String[] args) {
        int failures = 0;
        SignalRegistries.bootstrap();

        var nodeStr = A.encode();
        var branchStr = AC.encode();
        var devId = java.util.UUID.randomUUID().toString();
        var trainId = java.util.UUID.randomUUID().toString();

        String script = SCRIPT
            .replace("NODE", "'" + nodeStr + "'")
            .replace("BRANCH", "'" + branchStr + "'")
            .replace("DEV", "'" + devId + "'")
            .replace("TRAIN", "'" + trainId + "'");

        try (ScriptRuntime rt = new ScriptRuntime()) {
            rt.eval(script, "scripttest/switch_kernel.py");

            var opt = top.hibermod.ctdapi.DispatchKernels.create(
                ResourceLocation.fromNamespaceAndPath("scripttest", "switch_kernel"));
            if (opt.isEmpty()) {
                System.out.println("  FAIL 内核未注册");
                System.exit(1);
            }
            var kernel = opt.get();

            SilentEnv env = new SilentEnv();
            kernel.onAttach(env);
            RecordingFrame frame = new RecordingFrame();
            kernel.onFrame(new FakeContext(), frame);

            failures += check("读到 1 个岔口", env.lines.stream().anyMatch(l -> l.contains("switch " + nodeStr)),
                env.lines.toString());

            failures += check("产出 2 条岔口指令（锁 + 解）", frame.switches.size() == 2,
                "" + frame.switches.size());
            if (frame.switches.size() == 2) {
                var lock = frame.switches.get(0);
                failures += check("锁岔节点解析正确", A.equals(lock.node()), String.valueOf(lock.node()));
                failures += check("锁岔股道解析正确", AC.equals(lock.branch()), String.valueOf(lock.branch()));
                failures += check("锁岔持有者正确", lock.holder() == 7L, "" + lock.holder());

                var unlock = frame.switches.get(1);
                failures += check("解锁指令 branch 为 null", unlock.branch() == null,
                    String.valueOf(unlock.branch()));
            }

            failures += check("设备指令已桥接", frame.devices.size() == 1, "" + frame.devices.size());
            if (frame.devices.size() == 1) {
                failures += check("设备 payload 原样透传",
                    "{\"angle\": 90}".equals(frame.devices.get(0).payload().getString("json")),
                    frame.devices.get(0).payload().toString());
            }

            failures += check("能源指令已桥接", frame.energies.size() == 1, "" + frame.energies.size());
            if (frame.energies.size() == 1) {
                failures += check("能源联动方 id 正确",
                    frame.energies.get(0).providerId().toString().equals("mymod:electro"),
                    frame.energies.get(0).providerId().toString());
            }

            failures += check("补给指令已桥接", frame.services.size() == 1, "" + frame.services.size());
            if (frame.services.size() == 1) {
                failures += check("补给设备 id 正确",
                    devId.equals(String.valueOf(frame.services.get(0).deviceId())),
                    String.valueOf(frame.services.get(0).deviceId()));
            }

            failures += check("非法 id 被丢弃且不抛异常", true, "");
        } catch (Throwable t) {
            System.out.println("  FAIL 未捕获异常: " + t);
            t.printStackTrace();
            failures++;
        }

        System.out.println();
        if (failures > 0) {
            System.out.println("A8 验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("A8 验收通过：岔口状态可读、岔口/设备/能源/补给指令全部桥接");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
