package top.hibermod.ctdscript.facade;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.HostAccess;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.FrameContext;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.interop.EnergyProvider;
import top.hibermod.ctdapi.interop.Interop;

/**
 * {@code on_frame(ctx, out)} 里的 {@code ctx}。
 *
 * <p>每个适配器实例**每帧只构造一次**；里面的 {@link TrainFacade} 列表也是每帧重建。
 * 之所以不复用：列车集合每帧都在变，缓存反而更容易出错，而一帧几十个小对象
 * 对 GC 的压力可以忽略（真正的开销在跨语言调用次数上，所以整个帧只过一次边界）。
 */
public final class FrameInput {

    private final FrameContext ctx;
    private final List<TrainFacade> trains = new ArrayList<>();
    private final List<List<String>> signals = new ArrayList<>();
    private final List<List<String>> switches = new ArrayList<>();

    public FrameInput(FrameContext ctx) {
        this.ctx = ctx;

        for (Telemetry.TrainState state : ctx.telemetry().trains()) {
            EnergyProvider.EnergySample energy = Interop.sampleEnergy(state);
            trains.add(new TrainFacade(state, energy));
        }

        // 信号机 → 它守护的边：脚本要据此判断「这段被占了吗」
        java.util.Map<java.util.UUID, String> edgeOfSignal = new java.util.HashMap<>();
        for (top.hibermod.ctdapi.Topology.SignalSite site : ctx.topology().signals()) {
            if (site.edge() != null) edgeOfSignal.put(site.id(), site.edge().encode());
        }

        for (FrameContext.SignalState s : ctx.signalStates()) {
            signals.add(List.of(
                s.signalId().toString(),
                s.side().name(),
                s.aspectId() == null ? "" : s.aspectId().toString(),
                edgeOfSignal.getOrDefault(s.signalId(), "")));
        }

        for (FrameContext.SwitchState s : ctx.switchStates()) {
            switches.add(List.of(
                s.node().encode(),
                s.lockedBranch() == null ? "" : s.lockedBranch().encode(),
                Long.toString(s.holder()),
                Boolean.toString(s.contested())));
        }
    }

    @HostAccess.Export
    public long game_time() {
        return ctx.gameTime();
    }

    /** 全部列车。[TrainFacade] */
    @HostAccess.Export
    public List<TrainFacade> trains() {
        return trains;
    }

    /**
     * 全部信号机的当前显示，每项是 {@code [signalId, side, aspectId]}。
     *
     * <p>用三元素列表而不是对象：这是只读快照，脚本通常只做一次遍历；
     * 返回对象会让每帧多出成百个小对象，而列表只是引用同一批 String。
     * {@code aspectId} 为空串表示未显示。
     */
    @HostAccess.Export
    public List<List<String>> signals() {
        return signals;
    }

    /**
     * 查询某列车装了哪些车载设备（speedometer / cbtc_atp）。
     *
     * 设备是车上的方块，属于世界状态；遥测只带每帧变化的数。
     * 只有服务端读得到（内核就跑在服务端）；拿不到返回空列表，
     * 含义是「没有设备」，于是该车按降级处理——与现实一致。
     */
    @HostAccess.Export
    public java.util.List<String> devices_of(String trainId) {
        try {
            java.util.UUID id = java.util.UUID.fromString(trainId);
            var train = com.simibubi.create.Create.RAILWAYS.trains.get(id);
            if (train == null || train.carriages.isEmpty()) return java.util.List.of();

            java.util.List<String> found = new java.util.ArrayList<>();
            for (var carriage : train.carriages) {
                var entity = carriage.anyAvailableEntity();
                if (entity == null) continue;
                for (var kind : top.hibermod.ctdscript.content.TrainDevices.of(entity)) {
                    if (!found.contains(kind.id())) found.add(kind.id());
                }
            }
            return found;
        } catch (Throwable t) {
            return java.util.List.of();
        }
    }

    /**
     * 这架信号机服务于哪套信号制度；未登记返回空串。
     *
     * <p>数据存在信号机自己身上（宿主 mixin 提供的 {@code SignalSystemHolder}），
     * 所以客户端读到的和服务端一致——不存在「外挂表客户端看不见」的问题。
     */
    @HostAccess.Export
    public String system_of(String signalId) {
        try {
            java.util.UUID id = java.util.UUID.fromString(signalId);
            for (var graph : com.simibubi.create.Create.RAILWAYS.trackNetworks.values()) {
                var point = graph.getPoint(
                    com.simibubi.create.content.trains.graph.EdgePointType.SIGNAL, id);
                if (point != null) {
                    return top.hibermod.ctdapi.signal.SignalSystemHolder.systemOf(point);
                }
            }
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** 某信号机某面的当前语义 id；未知返回空串。 */
    @HostAccess.Export
    public String aspect_of(String signalId, String side) {
        for (List<String> s : signals) {
            if (s.get(0).equals(signalId) && s.get(1).equalsIgnoreCase(side)) {
                return s.get(2);
            }
        }
        return "";
    }

    /** 按 id 取列车；不存在返回 {@code null}。 */
    @HostAccess.Export
    public TrainFacade train(String id) {
        for (TrainFacade t : trains) {
            if (t.id().equals(id)) return t;
        }
        return null;
    }

    /** 本帧待处理的事件条数（脚本想细看可自行扩展）。 */
    @HostAccess.Export
    public int event_count() {
        return ctx.pendingEvents().size();
    }

    /** 某语义 id 是否就是「停车」。 */
    @HostAccess.Export
    public boolean is_stop_aspect(String aspectId) {
        ResourceLocation id = ResourceLocation.tryParse(aspectId);
        if (id == null) return false;
        return top.hibermod.ctdapi.signal.SignalRegistries.aspect(id)
            .map(a -> a.category() == top.hibermod.ctdapi.signal.Aspect.Category.STOP)
            .orElse(false);
    }

    /**
     * 全部岔口的当前锁闭状态，每项 {@code [node, lockedBranch, holder, contested]}。
     *
     * <p>节点与边用的是 {@link top.hibermod.ctdapi.Rail.NodeId#encode()} 的字符串形式；
     * {@code lockedBranch} 为空串表示未锁。
     */
    @HostAccess.Export
    public List<List<String>> switches() {
        return switches;
    }

    /** 某岔口当前锁定的股道（编码字符串）；未锁返回空串。 */
    @HostAccess.Export
    public String locked_branch_of(String node) {
        for (List<String> s : switches) {
            if (s.get(0).equals(node)) return s.get(1);
        }
        return "";
    }

    /** 某岔口是否被多车竞争。 */
    @HostAccess.Export
    public boolean switch_contested(String node) {
        for (List<String> s : switches) {
            if (s.get(0).equals(node)) return Boolean.parseBoolean(s.get(3));
        }
        return false;
    }
}
