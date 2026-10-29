package top.hibermod.ctdscript.bridge;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import top.hibermod.ctdscript.broadcast.BroadcastEvent;
import top.hibermod.ctdscript.broadcast.BroadcastMessage;
import top.hibermod.ctdscript.broadcast.BroadcastService;

/**
 * 脚本侧的广播接口：{@code ctd.broadcast}。
 *
 * <h2>脚本说什么，引擎做什么</h2>
 * <ul>
 *   <li>{@code broadcast.on("approaching", fn)} —— 挂一个文案生成器；</li>
 *   <li>{@code broadcast.say("...", "train")} —— 直接播一条现成文案；</li>
 *   <li>{@code broadcast.status()} —— 自检：几条监听、哪个音源在扛。</li>
 * </ul>
 *
 * <h2>为什么文案是一个函数而不是一段字符串</h2>
 * 广播要带上下文：哪个车次、哪站、几站台、晚点多少。
 * 返回字符串的函数天然能拿到这些；写死字符串则立刻过时。
 */
public final class BroadcastApi {

    public static final BroadcastApi INSTANCE = new BroadcastApi();

    private BroadcastApi() {}

    private BroadcastService service() {
        return top.hibermod.ctdscript.CtdScript.broadcastService();
    }

    /**
     * 挂一个文案生成器。函数签名 {@code fn(msg) -> str}，
     * {@code msg} 上可读 {@code train} / {@code station} / {@code platform} /
     * {@code delay_seconds} / {@code scope} / {@code event}。
     *
     * @return 事件键（回显，方便脚本里确认）
     */
    @HostAccess.Export
    public String on(String eventKey, Value handler) {
        BroadcastEvent event = BroadcastEvent.byKey(eventKey);
        if (event == null) {
            throw new IllegalArgumentException("unknown broadcast event: " + eventKey
                + " (known: approaching, stopped, departing, platform_changed, delayed,"
                + " cancelled, through, terminating)");
        }
        service().on(event, handler);
        return event.key();
    }

    /** 直接播一条，绕过事件匹配。 */
    @HostAccess.Export
    public String say(String text, String scope) {
        BroadcastMessage message = new BroadcastMessage(BroadcastEvent.APPROACHING,
            scope == null || scope.isBlank() ? "train" : scope,
            "", "", "", 0, text);
        String voice = service().emit(message);
        return voice == null ? "" : voice;
    }

    /**
     * 触发一次事件，让监听者出文案。
     *
     * @return 真正出声的音源 id；没出声返回空字符串
     */
    @HostAccess.Export
    public String emit(String eventKey, String train, String station, String platform,
                       int delaySeconds, String scope) {
        BroadcastEvent event = BroadcastEvent.byKey(eventKey);
        if (event == null) {
            throw new IllegalArgumentException("unknown broadcast event: " + eventKey);
        }
        BroadcastMessage message = new BroadcastMessage(event,
            scope == null || scope.isBlank() ? "train" : scope,
            train == null ? "" : train,
            station == null ? "" : station,
            platform == null ? "" : platform,
            delaySeconds,
            null);
        String voice = service().emit(message);
        return voice == null ? "" : voice;
    }

    /** 登记一个语音源的处理入口留给附属模组用（脚本侧不需要调用）。 */
    @HostAccess.Export
    public String status() {
        return service().status();
    }

    @HostAccess.Export
    public int handlers() {
        return service().handlerCount();
    }

    /** 热重载时清空监听，避免旧脚本的闭包继续被调用。 */
    public void reset() {
        service().clearHandlers();
    }
}
