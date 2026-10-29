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

    public HudState(double speed, double targetSpeed, double speedLimit, boolean riding,
                    String trainName, String nextStop, String platform, int delaySeconds,
                    long gameTime) {
        this.speed = speed;
        this.targetSpeed = targetSpeed;
        this.speedLimit = speedLimit;
        this.riding = riding;
        this.trainName = trainName;
        this.nextStop = nextStop;
        this.platform = platform;
        this.delaySeconds = delaySeconds;
        this.gameTime = gameTime;
    }

    /** 空态：没坐车、没数据时用。脚本拿到的是一组 0，不是异常。 */
    public static HudState empty() {
        return new HudState(0, 0, 0, false, "", "", "", 0, 0);
    }

    /** 速度，单位是「格/刻」（Create 原生单位，不是 m/s）。 */
    @HostAccess.Export
    public double speed() {
        return speed;
    }

    /** 换算成 km/h，方便直接显示。 */
    @HostAccess.Export
    public double speed_kmh() {
        return speed * 20.0 * 3.6;
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
