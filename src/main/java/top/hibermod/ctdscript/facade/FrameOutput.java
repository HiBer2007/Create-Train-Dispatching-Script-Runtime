package top.hibermod.ctdscript.facade;

import java.util.UUID;

import org.graalvm.polyglot.HostAccess;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.DispatchFrame;
import top.hibermod.ctdapi.KernelEnv;
import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Topology;

/**
 * {@code on_frame(ctx, out)} 里的 {@code out}：脚本写指令的地方。
 *
 * <p>所有方法都做**参数校验并静默丢弃非法调用**——脚本传了个坏 UUID 不该让整帧崩掉，
 * 但也不能把半条指令写进去。校验失败时写一条日志，然后什么都不做。
 */
public final class FrameOutput {

    private final DispatchFrame frame;
    private final KernelEnv env;
    private int dropped;

    public FrameOutput(DispatchFrame frame, KernelEnv env) {
        this.frame = frame;
        this.env = env;
    }

    /** 本帧被丢弃的非法调用数（适配器用它决定是否告警）。 */
    public int droppedCount() {
        return dropped;
    }

    // ── 列车 ──────────────────────────────────────────────────────────────

    /** 让列车停车（授权 0）。 */
    @HostAccess.Export
    public void hold(String trainId, String reason) {
        UUID id = uuid(trainId);
        if (id == null) return;
        frame.command(Command.TrainCommand.hold(id));
        trace("held " + trainId + ": " + (reason == null ? "" : reason));
    }

    /** 给定距离授权与限速。 */
    @HostAccess.Export
    public void proceed(String trainId, double authority, double speedCap) {
        UUID id = uuid(trainId);
        if (id == null) return;
        if (authority <= 0 || speedCap <= 0) {
            frame.command(Command.TrainCommand.hold(id));
            return;
        }
        frame.command(Command.TrainCommand.proceed(id, authority, speedCap));
    }

    /** 同时指定授权、限速与是否保持。{@code hold=true} 时授权被忽略。 */
    @HostAccess.Export
    public void command(String trainId, double authority, double speedCap, boolean hold) {
        UUID id = uuid(trainId);
        if (id == null) return;
        frame.command(new Command.TrainCommand(
            id, Command.Direction.KEEP,
            Math.max(0, authority), Math.max(0, speedCap),
            hold, null));
    }

    // ── 信号 ──────────────────────────────────────────────────────────────

    /** 给某信号机的某一面设置语义。{@code side} 是 {@code "FIRST"} / {@code "SECOND"}。 */
    @HostAccess.Export
    public void aspect(String signalId, String side, String aspectId) {
        UUID id = uuid(signalId);
        if (id == null) return;

        Topology.SignalSide s;
        try {
            s = Topology.SignalSide.valueOf(side.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            dropped++;
            env.log("warn", "ctd-script: bad side '{}' for signal {}", side, signalId);
            return;
        }

        ResourceLocation aspect = ResourceLocation.tryParse(aspectId);
        if (aspect == null) {
            dropped++;
            env.log("warn", "ctd-script: bad aspect id '{}'", aspectId);
            return;
        }

        frame.aspect(new Command.AspectCommand(id, s, aspect));
    }

    // ── 岔口 ──────────────────────────────────────────────────────────────

    /**
     * 锁定某个岔口的某条股道。
     *
     * @param node       岔心节点（{@code Rail.NodeId#encode()} 的形式）
     * @param branch     期望开通的股道边（{@code Rail.EdgeId#encode()} 的形式）
     * @param holder     持锁者标识（进路 id 之类），用于冲突检测
     */
    @HostAccess.Export
    public void lock_switch(String node, String branch, long holder) {
        Rail.NodeId n = Rail.NodeId.tryDecode(node);
        Rail.EdgeId b = Rail.EdgeId.tryDecode(branch);
        if (n == null || b == null) {
            dropped++;
            env.log("warn", "ctd-script: bad switch ids '{}' / '{}'", node, branch);
            return;
        }
        frame.switchLock(new Command.SwitchCommand(n, Topology.SwitchPosition.UNKNOWN, b, holder));
    }

    /** 解除某个岔口的锁闭。 */
    @HostAccess.Export
    public void unlock_switch(String node) {
        Rail.NodeId n = Rail.NodeId.tryDecode(node);
        if (n == null) {
            dropped++;
            env.log("warn", "ctd-script: bad switch node '{}'", node);
            return;
        }
        frame.switchLock(new Command.SwitchCommand(n, Topology.SwitchPosition.UNKNOWN, null, 0L));
    }

    // ── 设备与能源 ────────────────────────────────────────────────────────

    /**
     * 给一台设备下指令。{@code payload} 的结构由设备实现解释，桥接层不解析。
     */
    @HostAccess.Export
    public void device(String deviceId, String payloadJson) {
        UUID id = uuid(deviceId);
        if (id == null) {
            dropped++;
            env.log("warn", "ctd-script: bad device id '{}'", deviceId);
            return;
        }
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        if (payloadJson != null && !payloadJson.isBlank()) {
            tag.putString("json", payloadJson);
        }
        frame.device(new Command.DeviceCommand(id, tag, 0L));
    }

    /**
     * 给能源联动方下指令（升弓、切供电、加注…）。
     *
     * <p>{@code payload} 原样透传，API 不解释它的结构。
     */
    @HostAccess.Export
    public void energy(String trainId, String providerId, String payloadJson) {
        UUID id = uuid(trainId);
        ResourceLocation provider = ResourceLocation.tryParse(providerId);
        if (id == null || provider == null) {
            dropped++;
            env.log("warn", "ctd-script: bad energy target '{}' / '{}'", trainId, providerId);
            return;
        }
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        if (payloadJson != null && !payloadJson.isBlank()) {
            tag.putString("json", payloadJson);
        }
        frame.energy(new Command.EnergyCommand(id, provider, tag, 0L));
    }

    /**
     * 请求一次地面补给（加油 / 充电 / 上水）。
     *
     * @param deviceId 目标设备；传空串表示让宿主就近挑一个
     */
    @HostAccess.Export
    public void service(String trainId, String serviceId, String deviceId) {
        UUID id = uuid(trainId);
        ResourceLocation service = ResourceLocation.tryParse(serviceId);
        if (id == null || service == null) {
            dropped++;
            env.log("warn", "ctd-script: bad service target '{}' / '{}'", trainId, serviceId);
            return;
        }
        UUID device = (deviceId == null || deviceId.isBlank()) ? null : uuid(deviceId);
        frame.service(new Command.ServiceCommand(id, device, service,
            new net.minecraft.nbt.CompoundTag(), 0L));
    }
    // ── 杂项 ──────────────────────────────────────────────────────────────

    /**
     * 写一条日志。
     *
     * <p>{@code DispatchFrame} 没有日志通道，所以这里直接走 {@link KernelEnv}。
     * 脚本排障靠它，别删。
     */
    @HostAccess.Export
    public void log(String message) {
        env.log("info", "[script] {}", message);
    }

    /**
     * 通过**本帧**下发一条指派。
     *
     * <p>和 {@code operation.publish(...)} 的区别在于时序：
     * 这一条会等其他内核跑完、与其它指令一起在帧末提交，
     * 而 {@code publish} 是立刻生效。想让多个内核的指派按「后写覆盖」规则决出胜负，
     * 就必须走这个口。
     */
    @HostAccess.Export
    public void assign(top.hibermod.ctdapi.operation.Assignment assignment) {
        if (assignment == null) return;
        frame.assign(assignment);
    }

    private void trace(String message) {
        env.log("info", "[script] {}", message);
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }
}
