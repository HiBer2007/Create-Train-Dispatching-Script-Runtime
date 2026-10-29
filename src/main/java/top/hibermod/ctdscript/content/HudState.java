package top.hibermod.ctdscript.content;

import org.graalvm.polyglot.HostAccess;

/**
 * 喂给 HUD 脚本的**只读**运行态。
 *
 * <h2>为什么由引擎给，而不是脚本自己算</h2>
 * 速度、限速、下一站、晚点这些数字来自服务端权威状态。
 * 让脚本自己从世界对象里翻，会出现「HUD 显示的速度和车实际速度不一致」——
 * 这种不一致最难查，因为两边都「有道理」。
 *
 * <p>所以引擎算好、脚本只读。字段名刻意用下划线风格，和 Python 侧一致。
 *
 * <h2>以后会长的东西</h2>
 * 站台号、晚点秒数、门侧、编组信息都会陆续加进来；
 * 脚本只读自己需要的字段，不需要关心新增了哪些。
 */
public final class HudState {

    private final double speed;
    private final double targetSpeed;
    private final double speedLimit;
    private final boolean riding;
    private final String trainName;
    private final String nextStop;
    private final String platform;
    private final int delaySeconds;
    private final long gameTime;

    /** 相对驾驶台的符号：朝驾驶台面对的方向走为正，倒着走为负。 */
    private final int sign;

    public HudState(double speed, double targetSpeed, double speedLimit, boolean riding,
                    String trainName, String nextStop, String platform, int delaySeconds,
                    long gameTime, int sign) {
        this.speed = speed;
        this.targetSpeed = targetSpeed;
        this.speedLimit = speedLimit;
        this.riding = riding;
        this.trainName = trainName;
        this.nextStop = nextStop;
        this.platform = platform;
        this.delaySeconds = delaySeconds;
        this.gameTime = gameTime;
        this.sign = sign >= 0 ? 1 : -1;
    }

    /** 空态：没坐车、没数据时用。脚本拿到的是一组 0，不是异常。 */
    public static HudState empty() {
        return new HudState(0, 0, 0, false, "", "", "", 0, 0, 1);
    }

    /** 速度，单位是「格/刻」（Create 原生单位，不是 m/s）。 */
    @HostAccess.Export
    public double speed() {
        return speed;
    }

    /**
     * 速度（km/h），**带符号**。
     *
     * <p>符号相对**驾驶台**：朝驾驶台面对的方向行驶为正，倒车为负。
     * Create 原生的 HUD 用的是 {@code Math.abs(train.speed)}（无符号），
     * 但指针式仪表需要知道往哪边摆——倒车时指针该往回走。
     */
    @HostAccess.Export
    public double speed_kmh() {
        return speed * sign * 72.0;
    }

    /** 不带符号的速度（km/h）。 */
    @HostAccess.Export
    public double speed_abs_kmh() {
        return Math.abs(speed) * 72.0;
    }

    /** 当前符号：+1 前进，-1 倒行。 */
    @HostAccess.Export
    public int direction_sign() {
        return sign;
    }

    @HostAccess.Export
    public double target_speed() {
        return targetSpeed;
    }

    @HostAccess.Export
    public double speed_limit() {
        return speedLimit;
    }

    /** 玩家是否正坐在列车上。 */
    @HostAccess.Export
    public boolean riding() {
        return riding;
    }

    @HostAccess.Export
    public String train_name() {
        return trainName == null ? "" : trainName;
    }

    @HostAccess.Export
    public String next_stop() {
        return nextStop == null ? "" : nextStop;
    }

    @HostAccess.Export
    public String platform() {
        return platform == null ? "" : platform;
    }

    @HostAccess.Export
    public int delay_seconds() {
        return delaySeconds;
    }

    @HostAccess.Export
    public long game_time() {
        return gameTime;
    }
}
