package top.hibermod.ctdscript;

/**
 * 脚本加载阶段：哪些是「定义」，哪些是「算法」。
 *
 * <h2>划分依据</h2>
 * 判据不是「改起来方不方便」，而是**这东西有没有被别处引用**：
 *
 * <table border="1">
 *   <tr><th>目录</th><th>阶段</th><th>内容</th><th>为什么</th></tr>
 *   <tr><td>{@code signal/}</td><td>启动</td><td>信号制度、机型、灯位、语义映射</td>
 *       <td>设备、内核、HUD 全都按 id 引用它；换掉等于换了一套规则，运行中的车会看着旧规则做决策</td></tr>
 *   <tr><td>{@code device/}</td><td>启动</td><td>地面设备、车载设备类型</td>
 *       <td>设备实例已经绑在轨道边点上，类型换了实例就成了孤儿</td></tr>
 *   <tr><td>{@code timetable/}</td><td>启动</td><td>交路、停站表、速度等级</td>
 *       <td>车次与运行图按 id 引用交路</td></tr>
 *   <tr><td>{@code kernel/}</td><td><b>可热重载</b></td><td>调度算法：往哪走、怎么让行</td>
 *       <td>无状态或状态自持；换实现只是换个策略</td></tr>
 *   <tr><td>{@code ato/}</td><td><b>可热重载</b></td><td>自动驾驶算法：加减速曲线</td>
 *       <td>同上，每帧重算，没有长期引用</td></tr>
 * </table>
 *
 * <h2>强制手段</h2>
 * 重载阶段调用定义类注册会**直接抛异常**，而不是静默忽略——
 * 静默忽略会让作者以为改了，实际没生效，这种问题最难查。
 */
public final class ScriptMode {

    /** 当前所处阶段。 */
    public enum Phase {
        /** 启动加载全部。 */
        STARTUP,
        /** 只允许重载 {@code kernel/} 与 {@code ato/}。 */
        RELOAD
    }

    private static volatile Phase current = Phase.STARTUP;

    private ScriptMode() {}

    public static Phase current() {
        return current;
    }

    public static void enter(Phase phase) {
        current = phase;
    }

    /** 定义类注册（信号/设备/交路）是否当前允许。 */
    public static boolean definitionsAllowed() {
        return current == Phase.STARTUP;
    }

    /**
     * 定义类注册的统一守卫。
     *
     * @param what 人类可读的东西名，例如 "signal system"
     * @throws IllegalStateException 在重载阶段被调用
     */
    public static void requireStartup(String what) {
        if (current == Phase.RELOAD) {
            throw new IllegalStateException(
                "cannot register " + what + " during hot reload: definitions "
                + "(signal systems, device types, services) are startup-only. "
                + "Move this script into kernel/ or ato/ if it is an algorithm, "
                + "or restart the server if it really is a definition.");
        }
    }
}
