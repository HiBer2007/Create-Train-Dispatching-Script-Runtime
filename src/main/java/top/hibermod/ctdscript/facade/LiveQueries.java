package top.hibermod.ctdscript.facade;

import java.util.List;
import java.util.UUID;

/**
 * 需要**实时**查询 Create 的脚本接口，单独成一个类。
 *
 * <h2>为什么必须拆出去</h2>
 * {@link FrameInput} 是每帧都新建的类。只要它自己引用 Create 的类型，
 * 类校验（verification）就会去加载 Create——而离线验收测试的 classpath 里没有 Create，
 * 于是整个内核在第一帧就抛 {@code NoClassDefFoundError}。
 *
 * <p>把会碰 Create 的方法放到独立类，只有真的调用时才加载：
 * 离线测试、以及不装 Create 的场景都不受影响。
 */
public final class LiveQueries {

    private LiveQueries() {}

    /** 某列车装了哪些车载设备。 */
    public static List<String> devicesOf(String trainId) {
        try {
            UUID id = UUID.fromString(trainId);
            var train = com.simibubi.create.Create.RAILWAYS.trains.get(id);
            if (train == null || train.carriages.isEmpty()) return List.of();

            List<String> found = new java.util.ArrayList<>();
            for (var carriage : train.carriages) {
                var entity = carriage.anyAvailableEntity();
                if (entity == null) continue;
                for (var kind : top.hibermod.ctdscript.content.TrainDevices.of(entity)) {
                    if (!found.contains(kind.id())) found.add(kind.id());
                }
            }
            return found;
        } catch (Throwable t) {
            // 拿不到就当作「没有设备」：该车按降级处理，与现实一致
            return List.of();
        }
    }

    /** 这架信号机服务于哪套制度；未登记返回空串。 */
    public static String systemOf(String signalId) {
        try {
            UUID id = UUID.fromString(signalId);
            for (var graph : com.simibubi.create.Create.RAILWAYS.trackNetworks.values()) {
                var point = graph.getPoint(
                    com.simibubi.create.content.trains.graph.EdgePointType.SIGNAL, id);
                if (point != null) {
                    return top.hibermod.ctdapi.signal.SignalSystemHolder.systemOf(point);
                }
            }
            return "";
        } catch (Throwable t) {
            return "";
        }
    }
}
