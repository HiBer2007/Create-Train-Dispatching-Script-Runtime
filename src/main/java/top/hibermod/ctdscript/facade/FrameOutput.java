package top.hibermod.ctdscript.facade;

import java.util.UUID;

import org.graalvm.polyglot.HostAccess;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.DispatchFrame;
import top.hibermod.ctdapi.KernelEnv;
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
