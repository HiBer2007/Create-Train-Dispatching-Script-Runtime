package top.hibermod.ctdscript.api;

import org.graalvm.polyglot.HostAccess;

/** 运行图与车站绑定：脚本按图调度时写入，显示件只读。 */
public final class RouteApi {

    @HostAccess.Export
    public void bindStation(String station, String platform, int x, int y, int z) {
        top.hibermod.ctdapi.route.StationBindings.put(station, platform, x, y, z);
    }

    @HostAccess.Export
    public void route(String lineId, java.util.List<String> stations, boolean loop) {
        top.hibermod.ctdapi.route.RouteStore.putLine(lineId, stations, loop);
    }

    @HostAccess.Export
    public void runPlan(String train, String lineId, String from, String to) {
        top.hibermod.ctdapi.route.RouteStore.putPlan(train, lineId, from, to);
    }
}
