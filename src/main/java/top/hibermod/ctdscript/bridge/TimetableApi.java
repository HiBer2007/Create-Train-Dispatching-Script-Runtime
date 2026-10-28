package top.hibermod.ctdscript.bridge;

import org.graalvm.polyglot.HostAccess;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.timetable.Service;

/**
 * {@code ctd.timetable}：注册交路（车次）与它们绑定的信号制度。
 *
 * <p>已可用（纯注册）。
 */
public final class TimetableApi {

    public static final TimetableApi INSTANCE = new TimetableApi();

    private TimetableApi() {}

    /**
     * 注册一条交路。
     *
     * @param namespace      交路 id 命名空间
     * @param path           交路 id 路径
     * @param code           对外车次号（"G1"）
     * @param lineName       线路名，可为 {@code null}
     * @param signalSystemId 绑定的信号制度（"ctdapi:mainline_4"）
     * @param speedClassId   速度等级 id，可为 {@code null}
     * @param priority       调度优先级（越小越优先）
     */
    @HostAccess.Export
    public String registerService(String namespace, String path, String code, String lineName,
                                  String signalSystemId, String speedClassId, int priority) {
        top.hibermod.ctdscript.ScriptMode.requireStartup("TimetableApi");
        Service.Builder b = Service.builder(ResourceLocation.fromNamespaceAndPath(namespace, path))
            .code(code)
            .signalSystem(ResourceLocation.parse(signalSystemId))
            .priority(priority);

        if (lineName != null && !lineName.isBlank()) {
            b.line(Component.literal(lineName));
        }
        if (speedClassId != null && !speedClassId.isBlank()) {
            b.speedClass(ResourceLocation.parse(speedClassId));
        }

        Service.register(b.build());
        return namespace + ":" + path;
    }
}
