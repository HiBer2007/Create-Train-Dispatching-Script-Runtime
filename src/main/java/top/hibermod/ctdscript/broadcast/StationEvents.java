package top.hibermod.ctdscript.broadcast;

import top.hibermod.ctdapi.operation.OperationFeed;
import top.hibermod.ctdapi.operation.TrainAtStation;

/**
 * 把宿主的车站事件接到广播上。
 *
 * <h2>链路</h2>
 * <pre>
 * 宿主每帧看列车 → StationWatcher 判定相位变化
 *   → OperationFeed.trainAtStation（契约层）
 *     → 本类翻译成 BroadcastEvent
 *       → 脚本出文案 → 语音源链出声
 * </pre>
 *
 * <h2>为什么脚本不直接订阅 API</h2>
 * 脚本可以自己订（API 是对外开放的），但默认接上更省事：
 * 装好就有广播，不需要先写一段胶水。想自己控制就注册监听器替换文案，
 * 事件源头不变。
 */
public final class StationEvents implements OperationFeed.Listener {

    private static final StationEvents INSTANCE = new StationEvents();

    private StationEvents() {}

    public static void attach() {
        OperationFeed.subscribe(INSTANCE);
    }

    @Override
    public void onTrainAtStation(TrainAtStation event) {
        BroadcastEvent broadcastEvent = switch (event.phase()) {
            case APPROACHING -> BroadcastEvent.APPROACHING;
            case STOPPED -> BroadcastEvent.STOPPED;
            case DEPARTING -> BroadcastEvent.DEPARTING;
        };

        // 站台号目前 API 里没有；留空，脚本按需自行补充
        top.hibermod.ctdscript.CtdScript.broadcastService().emit(
            new BroadcastMessage(broadcastEvent, "train",
                event.trainName(), event.stationName(), "",
                event.delaySeconds(), null));
    }
}
