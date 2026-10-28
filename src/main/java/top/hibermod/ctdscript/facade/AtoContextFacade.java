package top.hibermod.ctdscript.facade;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.HostAccess;

import top.hibermod.ctdapi.ato.AtoEngine;
import top.hibermod.ctdapi.status.Emergency;
import top.hibermod.ctdapi.status.Restrictions;

/**
 * {@code decide(ctx)} 里的 {@code ctx}：ATO 的一帧输入，裁剪到脚本需要的量。
 *
 * <p>与 {@link FrameInput} 同样的原则：**不把 Java 记录机械翻译一遍**。
 * ATO 要做的是操纵决策，不是看全部遥测。
 */
public final class AtoContextFacade {

    private final AtoEngine.Context ctx;

    public AtoContextFacade(AtoEngine.Context ctx) {
        this.ctx = ctx;
    }

    // ── 身份与时间 ────────────────────────────────────────────────────────

    @HostAccess.Export
    public long game_time() {
        return ctx.gameTime();
    }

    @HostAccess.Export
    public TrainFacade train() {
        return new TrainFacade(ctx.train(), ctx.energy());
    }

    // ── 授权与限速 ────────────────────────────────────────────────────────

    /** 距离授权（格）。{@code <= 0} 必须停车。 */
    @HostAccess.Export
    public double authority() {
        return ctx.authority();
    }

    /** 信号侧给出的限速（格/tick）。 */
    @HostAccess.Export
    public double permitted_speed() {
        return ctx.permittedSpeed();
    }

    /** 车次速度等级的上限；没有等级时返回 {@link Double#MAX_VALUE}。 */
    @HostAccess.Export
    public double speed_class_limit() {
        return ctx.speedClass() == null ? Double.MAX_VALUE : ctx.speedClass().maxSpeed();
    }

    /** 车次速度等级名；无等级返回空串。 */
    @HostAccess.Export
    public String speed_class_name() {
        return ctx.speedClass() == null ? "" : ctx.speedClass().displayName().getString();
    }

    // ── 停车点 ────────────────────────────────────────────────────────────

    /** 到当前停车点的距离（格）；无停车点为 {@link Double#MAX_VALUE}。 */
    @HostAccess.Export
    public double distance_to_target() {
        return ctx.distanceToTarget();
    }

    /** 停车点名称；未知为空串。 */
    @HostAccess.Export
    public String target_name() {
        return ctx.targetName() == null ? "" : ctx.targetName().getString();
    }

    @HostAccess.Export
    public boolean at_platform() {
        return ctx.atPlatform();
    }

    @HostAccess.Export
    public boolean doors_open() {
        return ctx.doorsOpen();
    }

    // ── 突发状态 ──────────────────────────────────────────────────────────

    /** 前方是否有封闭区间。 */
    @HostAccess.Export
    public boolean blocked_ahead() {
        return ctx.blockedAhead();
    }

    /** 前方最严格限速；无限制为 {@link Double#MAX_VALUE}。 */
    @HostAccess.Export
    public double strictest_limit() {
        return ctx.strictestLimit();
    }

    @HostAccess.Export
    public boolean under_emergency() {
        return ctx.underEmergency();
    }

    @HostAccess.Export
    public boolean can_traction() {
        return ctx.canTraction();
    }

    /** 影响本车的限制，每项 {@code [kind, note]}。 */
    @HostAccess.Export
    public List<List<String>> restrictions() {
        List<List<String>> out = new ArrayList<>();
        for (Restrictions.Restriction r : ctx.restrictions()) {
            out.add(List.of(r.kind().name(), r.note().getString()));
        }
        return out;
    }

    /** 影响本车的应急指令，每项 {@code [action, reason]}。 */
    @HostAccess.Export
    public List<List<String>> emergencies() {
        List<List<String>> out = new ArrayList<>();
        for (Emergency.Order o : ctx.emergencies()) {
            out.add(List.of(o.action().name(), o.reason().getString()));
        }
        return out;
    }

    /** 按授权距离算出的允许速度（刹车曲线）。 */
    @HostAccess.Export
    public double speed_for_authority() {
        double a = ctx.train().acceleration();
        return ctx.authority() <= 0 ? 0 : Math.sqrt(2 * a * ctx.authority());
    }
}
