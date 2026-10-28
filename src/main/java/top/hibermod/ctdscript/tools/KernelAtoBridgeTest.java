package top.hibermod.ctdscript.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.ApiCatalog;
import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.DispatchFrame;
import top.hibermod.ctdapi.FrameContext;
import top.hibermod.ctdapi.KernelEnv;
import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.RailwayEvent;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.Topology;
import top.hibermod.ctdapi.ato.AtoEngine;
import top.hibermod.ctdapi.ato.AtoEngines;
import top.hibermod.ctdapi.hud.Hud;
import top.hibermod.ctdapi.interop.EnergyProvider;
import top.hibermod.ctdapi.pis.Pis;
import top.hibermod.ctdapi.signal.SignalRegistries;
import top.hibermod.ctdscript.ScriptRuntime;

/**
 * P1/P2 验收：脚本内核与脚本 ATO 在**不启动游戏**的前提下跑通。
 *
 * <p>用测试替身实现 {@link FrameContext} / {@link DispatchFrame} / {@link Telemetry}，
 * 直接驱动适配器——这样验的是桥接层本身，不受 Create 环境影响。
 */
public final class KernelAtoBridgeTest {

    private KernelAtoBridgeTest() {}

    // ── 测试替身 ──────────────────────────────────────────────────────────

    private static Telemetry.TrainState train(UUID id, double speed, double authority) {
        return new Telemetry.TrainState(
            id,
            Component.literal("T-" + id.toString().substring(0, 4)),
            6.0,
            null, 0.0, null, 0.0,
            List.of(),
            speed, 0.5, 0.0075, 1.4,
            2, false, false, false, 123.4,
            new Telemetry.RouteState(new Telemetry.RouteId(1L), 240.0, List.of(), List.of(), false),
            authority,
            EnergyProvider.EnergySample.unknown(),
            java.util.Set.of(ResourceLocation.fromNamespaceAndPath("ctdapi", "mainline_4")));
    }

    private static final class FakeTelemetry implements Telemetry {
        private final List<TrainState> trains;

        FakeTelemetry(List<TrainState> trains) {
            this.trains = trains;
        }

        @Override public Collection<TrainState> trains() { return trains; }

        @Override public TrainState train(UUID id) {
            for (TrainState t : trains) if (t.id().equals(id)) return t;
            return null;
        }
    }

    private static final class FakeContext implements FrameContext {
        private final Telemetry telemetry;

        FakeContext(Telemetry telemetry) {
            this.telemetry = telemetry;
        }

        @Override public long gameTime() { return 1000L; }
        @Override public Topology topology() { return null; }   // FrameInput 不使用
        @Override public Telemetry telemetry() { return telemetry; }
        @Override public Collection<SignalState> signalStates() { return List.of(); }
        @Override public Collection<SwitchState> switchStates() { return List.of(); }
        @Override public Collection<RailwayEvent> pendingEvents() { return List.of(); }
    }

    private static final class RecordingFrame implements DispatchFrame {
        final List<Command.TrainCommand> trains = new ArrayList<>();
        final List<Command.AspectCommand> aspects = new ArrayList<>();
        final List<RailwayEvent> events = new ArrayList<>();

        @Override public void command(Command.TrainCommand c) { trains.add(c); }
        @Override public void aspect(Command.AspectCommand c) { aspects.add(c); }
        @Override public void switchLock(Command.SwitchCommand c) { }
        @Override public void device(Command.DeviceCommand c) { }
        @Override public void energy(Command.EnergyCommand c) { }
        @Override public void service(Command.ServiceCommand c) { }
        @Override public void hud(Hud.HudPanel p) { }
        @Override public void board(Pis.BoardModel b) { }
        @Override public void event(RailwayEvent e) { events.add(e); }
    }

    private static final class SilentEnv implements KernelEnv {
        final List<String> lines = new ArrayList<>();

        @Override public net.minecraft.nbt.CompoundTag state() {
            return new net.minecraft.nbt.CompoundTag();
        }

        @Override public void markStateDirty() { }

        @Override public void log(String level, String message, Object... args) {
            // 模拟宿主：按 {} 顺序填参，否则看不见真正的错误信息
            String out = message;
            for (Object a : args) {
                out = out.replaceFirst("\\{\\}", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
            }
            lines.add(level + ": " + out);
            if (level.equals("error")) System.out.println("    [env] " + out);
        }

        @Override public long gameTime() { return 1000L; }

        @Override public Topology topology() { return null; }

        @Override public Settings settings() {
            return new Settings(64.0, true, 15.0, java.util.Map.of());
        }
    }

    // ── 脚本 ──────────────────────────────────────────────────────────────

    private static final String KERNEL_SCRIPT = """
        from ctd import kernel

        class FixedBlock:
            def on_frame(self, ctx, out):
                for t in ctx.trains():
                    if t.manually_driven() or t.derailed():
                        continue
                    if not t.has_route():
                        out.hold(t.id(), "no route")
                        continue
                    ma = min(t.route_remaining(), t.speed_for(max(t.authority(), 0.0)))
                    out.proceed(t.id(), ma, t.speed_for(max(ma, 0.0)))
                out.log("frame %d: %d trains" % (ctx.game_time(), len(ctx.trains())))

        kernel.register("scripttest", "fixed_block", FixedBlock())
        """;

    private static final String ATO_SCRIPT = """
        from ctd import ato

        class SimpleAto:
            def decide(self, ctx):
                if ctx.under_emergency():
                    return ato.stop("emergency")
                if not ctx.can_traction():
                    return ato.stop("no traction power")
                if ctx.blocked_ahead():
                    return ato.stop("track closed")
                if ctx.authority() <= 0:
                    return ato.stop("no movement authority")

                limit = ctx.permitted_speed()
                limit = min(limit, ctx.speed_class_limit())
                limit = min(limit, ctx.strictest_limit())
                target = min(limit, ctx.speed_for_authority())

                dv = target - ctx.train().speed()
                if dv >= 0:
                    return ato.decision(min(1.0, dv / 0.05), 0.0, target, ato.NONE, False, "accelerate")
                return ato.decision(0.0, min(1.0, -dv / 0.05), target, ato.NONE, False, "brake")

        ato.register("scripttest", "simple_ato", "ctdapi:mainline_4", SimpleAto())
        """;

    // ── 主流程 ────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        int failures = 0;

        SignalRegistries.bootstrap();
        top.hibermod.ctdapi.traction.Traction.Builtin.bootstrap();

        try (ScriptRuntime rt = new ScriptRuntime()) {
            // ── 内核 ──────────────────────────────────────────────────────
            rt.eval(KERNEL_SCRIPT, "scripttest/kernel.py");
            var kernelOpt = top.hibermod.ctdapi.DispatchKernels.create(
                ResourceLocation.fromNamespaceAndPath("scripttest", "fixed_block"));
            if (kernelOpt.isEmpty()) {
                System.out.println("  FAIL 脚本内核未注册");
                System.exit(1);
            }
            var ko = kernelOpt.get();

            SilentEnv env = new SilentEnv();
            ko.onAttach(env);

            UUID moving = UUID.randomUUID();
            UUID parked = UUID.randomUUID();
            Telemetry.TrainState parkedState = new Telemetry.TrainState(
                parked, Component.literal("parked"), 6.0, null, 0, null, 0, List.of(),
                0, 0, 0.0075, 1.4, 1, false, false, false, 0,
                null, 0, EnergyProvider.EnergySample.unknown(), java.util.Set.of());

            RecordingFrame frame = new RecordingFrame();
            ko.onFrame(new FakeContext(new FakeTelemetry(List.of(
                train(moving, 0.3, 120.0), parkedState))), frame);

            failures += check("内核产出了 2 条列车指令", frame.trains.size() == 2,
                "got " + frame.trains.size());
            failures += check("有进路的车收到授权 > 0",
                frame.trains.stream().anyMatch(c -> c.trainId().equals(moving) && c.movementAuthority() > 0),
                "no proceed command");
            failures += check("无进路的车被扣停",
                frame.trains.stream().anyMatch(c -> c.trainId().equals(parked) && c.hold()),
                "no hold command");
            failures += check("脚本日志已转发", env.lines.stream().anyMatch(l -> l.contains("frame 1000")),
                env.lines.toString());

            // ── ATO ───────────────────────────────────────────────────────
            rt.eval(ATO_SCRIPT, "scripttest/ato.py");
            var atoOpt = AtoEngines.get(ResourceLocation.fromNamespaceAndPath("scripttest", "simple_ato"));
            if (atoOpt.isEmpty()) {
                System.out.println("  FAIL 脚本 ATO 未注册");
                System.exit(1);
            }
            AtoEngine ato = atoOpt.get();
            failures += check("ATO 绑定到 ctdapi:mainline_4",
                ato.signalSystemId().equals(ResourceLocation.fromNamespaceAndPath("ctdapi", "mainline_4")),
                ato.signalSystemId().toString());

            AtoEngine.Decision d = ato.decide(new AtoEngine.Context(
                1000L, train(moving, 0.3, 120.0), EnergyProvider.EnergySample.unknown(), null,
                120.0, 1.0, Double.MAX_VALUE, null, false, false, List.of(), List.of()));

            failures += check("ATO 返回了决策", d != null, "null");
            if (d != null) {
                failures += check("目标速度落在授权允许范围内",
                    d.targetSpeed() > 0 && d.targetSpeed() <= 1.0 + 1e-9,
                    String.valueOf(d.targetSpeed()));
                failures += check("原因非空", d.reason() != null && !d.reason().getString().isBlank(),
                    String.valueOf(d.reason()));
                System.out.println("    decision: traction=" + d.traction() + " brake=" + d.brake()
                    + " target=" + String.format("%.3f", d.targetSpeed()) + " reason=" + d.reason().getString());
            }

            // 授权为 0 → 必须停车
            AtoEngine.Decision stopD = ato.decide(new AtoEngine.Context(
                1001L, train(moving, 0.3, 0.0), EnergyProvider.EnergySample.unknown(), null,
                0.0, 1.0, Double.MAX_VALUE, null, false, false, List.of(), List.of()));
            failures += check("授权为 0 时停车", stopD != null && stopD.hold() && stopD.targetSpeed() == 0,
                String.valueOf(stopD == null ? "null" : stopD.targetSpeed()));

            // 热重载语义：同一 id 再注册一次是「换实现」，不抛异常
            try {
                rt.eval(ATO_SCRIPT, "scripttest/ato.py");
                System.out.println("  ok   热重载：同 id 重新注册未抛异常");
            } catch (Throwable e) {
                System.out.println("  FAIL 热重载: " + e);
                failures++;
            }

            // freeze 必须收下这套注册
            try {
                ApiCatalog.freezeAll();
                System.out.println("  ok   freezeAll() 通过（含脚本注册的内核与 ATO）");
            } catch (IllegalStateException e) {
                System.out.println("  FAIL freezeAll: " + e.getMessage());
                failures++;
            }
        } catch (Throwable t) {
            System.out.println("  FAIL 未捕获异常: " + t);
            t.printStackTrace();
            failures++;
        }

        System.out.println();
        if (failures > 0) {
            System.out.println("P1/P2 验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("P1/P2 验收通过：脚本内核与脚本 ATO 都能在离线环境驱动 API");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
