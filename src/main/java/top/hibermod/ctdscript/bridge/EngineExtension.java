package top.hibermod.ctdscript.bridge;

import org.graalvm.polyglot.HostAccess;

/**
 * 引擎自带的第一个拓展：给脚本读一点运行时事实。
 *
 * <h2>它存在的意义不是功能，是示范</h2>
 * 附属模组要照着这个写：一个普通对象，方法标 {@code @HostAccess.Export}，
 * 注册到 {@link Extensions}。没有继承、没有接口、没有注解处理器。
 *
 * <p>顺带它真的有用：脚本能确认自己跑在服务端还是客户端、
 * 现在多少刻、宿主有没有在推帧。
 */
public final class EngineExtension {

    public static final String NAMESPACE = "engine";

    public static final EngineExtension INSTANCE = new EngineExtension();

    private EngineExtension() {}

    /** 服务端刻；客户端/未启动返回 -1。 */
    @HostAccess.Export
    public long game_time() {
        var server = top.hibermod.ctdscript.CtdScript.server();
        if (server == null) return -1;
        var level = server.overworld();
        return level == null ? -1 : level.getGameTime();
    }

    /** 是不是跑在专用服务器上。 */
    @HostAccess.Export
    public boolean dedicated() {
        return net.neoforged.fml.loading.FMLEnvironment.dist
            == net.neoforged.api.distmarker.Dist.DEDICATED_SERVER;
    }

    /** 已加载的脚本数（定义 + 算法）。 */
    @HostAccess.Export
    public int scripts() {
        var report = top.hibermod.ctdscript.CtdScript.report();
        return report == null ? 0 : report.ok();
    }

    /** 广播链自检。 */
    @HostAccess.Export
    public String broadcast_status() {
        return top.hibermod.ctdscript.CtdScript.broadcastService().status();
    }
}
