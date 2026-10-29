package top.hibermod.ctdscript.broadcast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;

/**
 * 广播服务：把「事件」变成「一段话」，再交给语音源链去出声。
 *
 * <h2>三段分工</h2>
 * <ol>
 *   <li><b>事件</b>由引擎定义（{@link BroadcastEvent}）——世界发生了什么；</li>
 *   <li><b>文案</b>由脚本给（本类的 handler）——该怎么说；</li>
 *   <li><b>声音</b>由语音源给（{@link VoiceChain}）——用什么声儿说。</li>
 * </ol>
 * 三段各自可换：换语言只改脚本，换 TTS 只换语音源，换运营规则只改事件映射。
 *
 * <h2>没有文案怎么办</h2>
 * 不播，但**记数**。静默是最难查的故障：广播没响，没人报障，
 * 等到有人发现时已经错过好几站。所以 {@link #misses()} 一直记着。
 */
public final class BroadcastService {

    private final VoiceChain chain = new VoiceChain();

    /** 事件 → 文案生成器（脚本函数）。一个事件可以有多个监听者。 */
    private final Map<BroadcastEvent, List<Value>> handlers = new LinkedHashMap<>();

    private long emitted;
    private long noHandler;
    private long noVoice;

    public VoiceChain chain() {
        return chain;
    }

    /** 注册一个文案生成器。同一事件可以有多个，按注册顺序都跑，取第一个非空结果。 */
    public synchronized void on(BroadcastEvent event, Value handler) {
        if (event == null || handler == null) return;
        handlers.computeIfAbsent(event, k -> new ArrayList<>()).add(handler);
    }

    /** 清空全部监听者（热重载时用）。 */
    public synchronized void clearHandlers() {
        handlers.clear();
    }

    public synchronized int handlerCount() {
        return handlers.values().stream().mapToInt(List::size).sum();
    }

    /**
     * 广播一条。
     *
     * @param message 已经带上上下文的广播（文案可以留空，交给脚本填）
     * @return 真正出声的语音源 id；没出声返回 {@code null}
     */
    public String emit(BroadcastMessage message) {
        if (message == null) return null;

        synchronized (this) {
            emitted++;
        }

        String text = message.text();
        if (text == null || text.isBlank()) {
            text = renderText(message);
        }

        if (text == null || text.isBlank()) {
            synchronized (this) {
                noHandler++;
            }
            BroadcastLog.write("broadcast " + message.event().key()
                + " has no script text; skipped");
            return null;
        }

        BroadcastMessage finalMessage = new BroadcastMessage(message.event(), message.scope(),
            message.train(), message.station(), message.platform(), message.delaySeconds(), text);

        String voice = chain.speak(finalMessage);
        if (voice == null) {
            synchronized (this) {
                noVoice++;
            }
            // 一条声音都没出：必须留下痕迹，不能安静地丢掉
            BroadcastLog.write("broadcast " + finalMessage.summary()
                + " had no available voice source");
        }
        return voice;
    }

    /** 依次问监听者要文案，取第一个非空。 */
    private String renderText(BroadcastMessage message) {
        List<Value> list;
        synchronized (this) {
            list = handlers.get(message.event());
            if (list == null) return null;
            list = new ArrayList<>(list);
        }

        for (Value handler : list) {
            try {
                Value result = handler.execute(new ScriptBroadcast(message));
                if (result != null && result.isString()) {
                    String s = result.asString();
                    if (s != null && !s.isBlank()) return s;
                }
            } catch (Throwable t) {
                System.err.println("[ctdscript] broadcast handler for "
                    + message.event().key() + " failed: " + t);
            }
        }
        return null;
    }

    public synchronized long emitted() {
        return emitted;
    }

    public synchronized long misses() {
        return noHandler + noVoice;
    }

    public synchronized String status() {
        return "broadcasts=" + emitted
            + " handlers=" + handlerCount()
            + " noText=" + noHandler
            + " noVoice=" + noVoice
            + " | " + chain.status();
    }
}
