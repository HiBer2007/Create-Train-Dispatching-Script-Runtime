package top.hibermod.ctdscript.facade;

import org.graalvm.polyglot.HostAccess;

import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.interop.EnergyProvider;

/**
 * 一列车的脚本视图。
 *
 * <p><b>这是刻意裁剪过的，不是 Java 版 TrainState 的机器翻译。</b>
 * 脚本做策略决策只需要这些量；暴露全部二十几个字段只会让脚本更容易写错，
 * 也让门面变成一个必须跟着 API 改的负担。
 */
public final class TrainFacade {

    private final Telemetry.TrainState state;
    private final EnergyProvider.EnergySample energy;

    public TrainFacade(Telemetry.TrainState state, EnergyProvider.EnergySample energy) {
        this.state = state;
        this.energy = energy;
    }

    @HostAccess.Export
    public String id() {
        return state.id().toString();
    }

    @HostAccess.Export
    public String name() {
        return state.name().getString();
    }

    // ── 运动学 ────────────────────────────────────────────────────────────

    /** 当前速度（格/tick，正 = 沿进路前进）。 */
    @HostAccess.Export
    public double speed() {
        return state.speed();
    }

    /** 宿主/Greate 本帧算出的目标速度，可作参考。 */
    @HostAccess.Export
    public double target_speed() {
        return state.targetSpeed();
    }

    /** 当前可用加速度（格/tick²）。Create 里制动与加速同值。 */
    @HostAccess.Export
    public double acceleration() {
        return state.acceleration();
    }

    @HostAccess.Export
    public double max_speed() {
        return state.maxSpeed();
    }

    @HostAccess.Export
    public double length() {
        return state.length();
    }

    // ── 运行状态 ──────────────────────────────────────────────────────────

    @HostAccess.Export
    public boolean derailed() {
        return state.derailed();
    }

    @HostAccess.Export
    public boolean manually_driven() {
        return state.manuallyDriven();
    }

    @HostAccess.Export
    public double odometer() {
        return state.odometer();
    }

    /** 当前生效的距离授权（格）。{@code <= 0} 表示必须停车。 */
    @HostAccess.Export
    public double authority() {
        return state.authority();
    }

    /** 是否已有进路。 */
    @HostAccess.Export
    public boolean has_route() {
        return state.route() != null;
    }

    /** 进路剩余长度；无进路时为 0。 */
    @HostAccess.Export
    public double route_remaining() {
        return state.route() == null ? 0.0 : state.route().remaining();
    }

    /** 进路是否已锁死（一旦进入不可撤销）。 */
    @HostAccess.Export
    public boolean route_committed() {
        return state.route() != null && state.route().committed();
    }

    // ── 能源（来自联动方；未知时视为可牵引）────────────────────────────────

    @HostAccess.Export
    public boolean can_traction() {
        return energy.canTraction();
    }

    @HostAccess.Export
    public double energy_fraction() {
        return energy.fraction();
    }

    /** 能源联动方给的一句话摘要，可能为空串。 */
    @HostAccess.Export
    public String energy_summary() {
        return energy.summary() == null ? "" : energy.summary().getString();
    }

    // ── 计算辅助 ──────────────────────────────────────────────────────────

    /** 以当前速度在 {@code distance} 内能否停住。 */
    @HostAccess.Export
    public boolean overshoots(double distance) {
        return Telemetry.TrainState.stoppingDistance(Math.abs(state.speed()), state.acceleration()) > distance;
    }

    /** 在给定距离内可用的最大速度（刹车曲线）。 */
    @HostAccess.Export
    public double speed_for(double distance) {
        double a = state.acceleration();
        return distance <= 0 ? 0 : Math.sqrt(2 * a * distance);
    }

    /** 供适配器取回原始对象，不对脚本暴露。 */
    public Telemetry.TrainState unwrap() {
        return state;
    }
}
