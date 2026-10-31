package top.hibermod.ctdscript.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.FrameContext;
import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.Topology;
import top.hibermod.ctdapi.device.Devices;
import top.hibermod.ctdapi.signal.SignalSystem;
import top.hibermod.ctdapi.signal.SignalType;
import top.hibermod.ctdapi.timetable.Service;
import top.hibermod.ctdscript.facade.FrameInput;

/**
 * 脚本门面的拓扑视图：`ctx.stations()` / `ctx.devices()` / `ctx.junctions()` / `ctx.section_count()`。
 *
 * <h2>为什么这几条要单独测</h2>
 * 它们是把 Java 侧的结构转成「Python 能读的行」的地方，**转换错了不会报错**——
 * 脚本拿到的是 `[]` 或者缺字段的行，于是内核以为「这个世界没有车站/岔口」。
 * 这正是前面几轮反复踩的「空契约」：不崩、不警告，只是结论错了。
 *
 * <p>测试用一个假 {@link Topology} 直接喂数据，不依赖游戏世界，
 * 所以能在离线把「行里有什么、顺序对不对、空值怎么表示」钉死。
 */
public final class SitesFacadeTest {

    private SitesFacadeTest() {}

    public static void main(String[] args) {
        int failures = 0;

        UUID stationId = UUID.randomUUID();
        Rail.EdgeId edge = edge("minecraft:overworld@0,64,0,0", "minecraft:overworld@8,64,0,0");

        // ── 车站 ────────────────────────────────────────────────────────
        Topology.StationSite station = new Topology.StationSite(
            stationId,
            Component.literal("中央站"),
            edge,
            12.5,
            false,
            List.of());

        FrameInput input = new FrameInput(new FakeContext(new FakeTopology(
            List.of(station), List.of(), List.of(), List.of())));

        List<List<String>> rows = input.stations();
        failures += check("车站一行", rows.size() == 1, "rows=" + rows.size());
        failures += check("车站行有 5 个字段", rows.get(0).size() == 5, String.valueOf(rows.get(0)));
        failures += check("车站 id 原样", rows.get(0).get(0).equals(stationId.toString()));
        failures += check("车站名保留中文", rows.get(0).get(1).equals("中央站"), rows.get(0).get(1));
        failures += check("车站边编码非空", !rows.get(0).get(2).isEmpty());
        failures += check("车站位置可解析", Double.parseDouble(rows.get(0).get(3)) == 12.5,
            rows.get(0).get(3));

        // ── 设备 ────────────────────────────────────────────────────────
        Devices.DeviceSite device = new Devices.DeviceSite(
            UUID.randomUUID(),
            ResourceLocation.fromNamespaceAndPath("ctd", "station_platform"),
            null,
            edge,
            3.0,
            true,
            new net.minecraft.nbt.CompoundTag());

        FrameInput deviceInput = new FrameInput(new FakeContext(new FakeTopology(
            List.of(), List.of(device), List.of(), List.of())));

        List<List<String>> deviceRows = deviceInput.devices();
        failures += check("设备一行", deviceRows.size() == 1);
        failures += check("设备行有 5 个字段", deviceRows.get(0).size() == 5, String.valueOf(deviceRows.get(0)));
        failures += check("设备类型是 ctd:station_platform",
            deviceRows.get(0).get(1).equals("ctd:station_platform"), deviceRows.get(0).get(1));

        // ── 岔口 ────────────────────────────────────────────────────────
        Topology.SwitchSite junction = new Topology.SwitchSite(
            Rail.NodeId.tryDecode("minecraft:overworld@16,64,0,0"),
            Topology.SwitchPosition.UNKNOWN,
            List.of(edge, edge),
            -1,
            null,
            true,
            false);

        FrameInput junctionInput = new FrameInput(new FakeContext(new FakeTopology(
            List.of(), List.of(), List.of(junction), List.of())));

        List<List<String>> junctionRows = junctionInput.junctions();
        failures += check("岔口一行", junctionRows.size() == 1);
        failures += check("岔口行有 3 个字段", junctionRows.get(0).size() == 3, String.valueOf(junctionRows.get(0)));
        failures += check("岔口股道数是 2", junctionRows.get(0).get(1).equals("2"), junctionRows.get(0).get(1));
        failures += check("岔口未锁闭", junctionRows.get(0).get(2).equals("false"), junctionRows.get(0).get(2));

        // ── 区段计数 ────────────────────────────────────────────────────
        failures += check("区段数为 0", junctionInput.section_count() == 0,
            String.valueOf(junctionInput.section_count()));

        // ── 拓扑为 null 时不能抛 ────────────────────────────────────────
        FrameInput nullTopo = new FrameInput(new FakeContext(null));
        failures += check("拓扑为 null 时车站为空", nullTopo.stations().isEmpty());
        failures += check("拓扑为 null 时设备为空", nullTopo.devices().isEmpty());
        failures += check("拓扑为 null 时岔口为空", nullTopo.junctions().isEmpty());
        failures += check("拓扑为 null 时区段为 0", nullTopo.section_count() == 0);

        System.out.println();
        if (failures > 0) {
            System.out.println("门面拓扑视图验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("门面拓扑视图验收通过：车站/设备/岔口行格式与空值行为");
    }

    private static int check(String name, boolean ok) {
        return check(name, ok, "");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }

    private static Rail.EdgeId edge(String a, String b) {
        return Rail.EdgeId.of(Rail.NodeId.tryDecode(a), Rail.NodeId.tryDecode(b));
    }

    // ── 假对象 ──────────────────────────────────────────────────────────

    private static final class FakeContext implements FrameContext {
        private final Topology topology;

        FakeContext(Topology topology) { this.topology = topology; }

        @Override public long gameTime() { return 0; }
        @Override public Topology topology() { return topology; }
        @Override public Telemetry telemetry() {
            return new Telemetry() {
                @Override public Collection<TrainState> trains() { return List.of(); }
                @Override public TrainState train(java.util.UUID id) { return null; }
            };
        }
        @Override public Collection<FrameContext.SignalState> signalStates() { return List.of(); }
        @Override public Collection<FrameContext.SwitchState> switchStates() { return List.of(); }
        @Override public Collection<top.hibermod.ctdapi.RailwayEvent> pendingEvents() { return List.of(); }
    }

    /** 只实现测试需要的四个集合，其余给空默认值。 */
    private static final class FakeTopology implements Topology {
        private final List<StationSite> stations;
        private final List<Devices.DeviceSite> devices;
        private final List<SwitchSite> switches;
        private final List<Section> sections;

        FakeTopology(List<StationSite> stations, List<Devices.DeviceSite> devices,
                     List<SwitchSite> switches, List<Section> sections) {
            this.stations = stations;
            this.devices = devices;
            this.switches = switches;
            this.sections = sections;
        }

        @Override public long version() { return 1; }
        @Override public Collection<Node> nodes() { return List.of(); }
        @Override public Node node(Rail.NodeId id) { return null; }
        @Override public Collection<Edge> edges() { return List.of(); }
        @Override public Edge edge(Rail.EdgeId id) { return null; }
        @Override public Collection<Edge> edgesFrom(Rail.NodeId id) { return List.of(); }
        @Override public Collection<SignalSite> signals() { return List.of(); }
        @Override public Collection<SwitchSite> switches() { return switches; }
        @Override public Collection<StationSite> stations() { return stations; }
        @Override public Collection<Section> sections() { return sections; }
        @Override public Collection<Section> sectionsOf(Rail.EdgeId edge) { return List.of(); }
        @Override public double maxTrainLength() { return 0; }
        @Override public Collection<top.hibermod.ctdapi.signal.Aspect> aspects() { return List.of(); }
        @Override public top.hibermod.ctdapi.signal.Aspect aspect(ResourceLocation id) { return null; }
        @Override public Collection<Service> services() { return List.of(); }
        @Override public Service service(ResourceLocation id) { return null; }
        @Override public Collection<Devices.DeviceSite> devices() { return devices; }
        @Override public Collection<Devices.DeviceSite> devicesOf(ResourceLocation typeId) { return List.of(); }
        @Override public Collection<Devices.DeviceSite> devicesOfTrain(UUID trainId) { return List.of(); }
        @Override public Collection<SignalSystem> signalSystems() { return List.of(); }
        @Override public SignalSystem signalSystem(ResourceLocation id) { return null; }
        @Override public Collection<SignalType> signalTypes() { return List.of(); }
        @Override public SignalType signalType(ResourceLocation id) { return null; }
    }
}
