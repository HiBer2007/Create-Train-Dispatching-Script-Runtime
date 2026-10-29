package top.hibermod.ctdscript.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import top.hibermod.ctdapi.net.TrainFeedPayload;

/**
 * 客户端缓存的**服务端真实车速**。
 *
 * <h2>为什么需要</h2>
 * Create 只在有人操控时同步速度；松手或自动驾驶之后客户端那份
 * {@code train.speed} 就冻住了，而列车还在动。HUD 要显示真实速度，
 * 就必须用宿主推过来的这份数据。
 *
 * <p>宁可显示「还不知道」（返回 {@code null}），也不要用一份过期值糊弄——
 * 仪表上最难查的问题就是「数看着对，其实早就错了」。
 */
public final class TrainFeedCache {

    private static final Map<UUID, TrainFeedPayload.Entry> ENTRIES = new ConcurrentHashMap<>();

    private TrainFeedCache() {}

    /** 整包更新：服务端每次推的都是全量快照。 */
    public static void apply(TrainFeedPayload payload) {
        if (payload == null) return;

        ENTRIES.clear();
        for (TrainFeedPayload.Entry e : payload.trains()) {
            ENTRIES.put(e.trainId(), e);
        }
    }

    public static TrainFeedPayload.Entry get(UUID trainId) {
        return trainId == null ? null : ENTRIES.get(trainId);
    }

    public static int size() {
        return ENTRIES.size();
    }

    /** 服务端多久没推过了（调试用）。 */
    public static long newestGameTime() {
        long newest = -1;
        for (TrainFeedPayload.Entry e : ENTRIES.values()) {
            newest = Math.max(newest, e.gameTime());
        }
        return newest;
    }
}
