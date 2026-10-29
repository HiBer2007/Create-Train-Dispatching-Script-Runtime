package top.hibermod.ctdscript.bridge;

import org.graalvm.polyglot.HostAccess;

/**
 * 车载 ATP 的语音与告警 —— 预留接口。
 *
 * <h2>为什么现在就要留</h2>
 * ATP 的语音不是「有声音就行」，它跟**运行状态**绑死：
 * 出发、进站预告、超速预警、常用制动、紧急制动、开门提示。
 * 这些事件在信号判定里天然就有，等到做语音时再去反推状态，会逼着内核
 * 把已经算过的东西再算一遍，而且算出来的时刻和真正触发告警的时刻对不上。
 *
 * <p>所以这里先定**事件名与语义**，具体发声由 {@code broadcast.VoiceChain} 决定：
 * 现在挂的是日志源，将来挂 TTS 或录音都不用改内核。
 *
 * <h2>谁调用</h2>
 * 内核（脚本）在状态切换的那一刻调；引擎不主动猜测。
 * 一条告警只在**状态跳变**时响一次——每帧都响会变成噪音。
 */
public final class AtpVoice {

    /** 告警等级，从轻到重。 */
    public enum Level {
        /** 提示：出发、到站。 */
        INFO,
        /** 预警：接近限速。 */
        WARNING,
        /** 制动：已经施加常用制动。 */
        BRAKE,
        /** 紧急：EB 触发。 */
        EMERGENCY
    }

    /** 预留的语音事件。名字是契约的一部分，改了就等于断了历史日志。 */
    public static final String DEPART = "atp.depart";
    public static final String ARRIVING = "atp.arriving";
    public static final String DOORS = "atp.doors";
    public static final String OVERSPEED_WARNING = "atp.overspeed_warning";
    public static final String SERVICE_BRAKE = "atp.service_brake";
    public static final String EMERGENCY_BRAKE = "atp.emergency_brake";
    public static final String SIGNAL_STOP = "atp.signal_stop";
    public static final String AUTHORITY_LOST = "atp.authority_lost";
    public static final String DEGRADED = "atp.degraded";

    private AtpVoice() {}

    /**
     * 报告一个 ATP 语音事件。
     *
     * @param code   上面那些常量之一
     * @param level  告警等级
     * @param text   要说的话（可空，交给语音源自己组织）
     * @return 出声的语音源 id；没出声返回 {@code null}
     */
    @HostAccess.Export
    public static String say(String code, String level, String text) {
        Level lv = parse(level);

        // 走广播的语音链：现在只有日志源，接了 TTS 之后同一行代码就会真的说话
        var service = top.hibermod.ctdscript.CtdScript.broadcastService();
        if (service == null) return null;

        return service.emit(new top.hibermod.ctdscript.broadcast.BroadcastMessage(
            eventOf(lv),
            "train",
            null, null, null, 0, text == null ? "" : text));
    }

    private static top.hibermod.ctdscript.broadcast.BroadcastEvent eventOf(Level level) {
        return switch (level) {
            case WARNING -> top.hibermod.ctdscript.broadcast.BroadcastEvent.ATP_WARNING;
            case BRAKE -> top.hibermod.ctdscript.broadcast.BroadcastEvent.ATP_BRAKE;
            case EMERGENCY -> top.hibermod.ctdscript.broadcast.BroadcastEvent.ATP_EMERGENCY;
            default -> top.hibermod.ctdscript.broadcast.BroadcastEvent.ATP_INFO;
        };
    }

    private static Level parse(String level) {
        if (level == null) return Level.INFO;
        for (Level lv : Level.values()) {
            if (lv.name().equalsIgnoreCase(level)) return lv;
        }
        return Level.INFO;
    }
}
