package top.hibermod.ctdscript.broadcast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语音源链：按登记顺序尝试，失败就降级到下一个。
 *
 * <h2>为什么一定要降级</h2>
 * 三种源里，TTS 和模型都可能因为外部原因不可用：进程没起、网络断了、模型没加载。
 * 如果第一个源失败就整体失败，玩家听到的是**一片安静**——
 * 而广播恰恰是「安静」最容易被忽视的功能，出了问题没人报。
 *
 * <p>所以：宁可退化成「本地音频」甚至「只在日志里说一遍」，也不要静默。
 *
 * <h2>统计</h2>
 * 每个源记成功/失败次数，{@code /ctd broadcast} 能看出是哪个源在拖后腿。
 */
public final class VoiceChain {

    private final Map<String, VoiceSource> sources = new LinkedHashMap<>();
    private final Map<String, int[]> stats = new LinkedHashMap<>();
    private long misses;

    /** 登记一个源；同 id 覆盖。 */
    public synchronized void register(VoiceSource source) {
        if (source == null || source.id() == null) return;
        sources.put(source.id(), source);
        stats.putIfAbsent(source.id(), new int[2]);
    }

    public synchronized void unregister(String id) {
        sources.remove(id);
        stats.remove(id);
    }

    public synchronized List<VoiceSource> sources() {
        return new ArrayList<>(sources.values());
    }

    /**
     * 播一条：从链头开始，谁先成功就算谁。
     *
     * @return 真正出声的源 id；全部失败返回 {@code null}（此时**必须**记日志）
     */
    public String speak(BroadcastMessage message) {
        List<VoiceSource> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(sources.values());
        }

        for (VoiceSource source : snapshot) {
            boolean ok;
            try {
                if (!source.available()) {
                    bump(source.id(), false);
                    continue;
                }
                ok = source.speak(message);
            } catch (Throwable t) {
                // 实现方不该抛，但真抛了也不能让广播链断掉
                ok = false;
            }
            bump(source.id(), ok);
            if (ok) return source.id();
        }

        synchronized (this) {
            misses++;
        }
        return null;
    }

    private synchronized void bump(String id, boolean ok) {
        int[] s = stats.computeIfAbsent(id, k -> new int[2]);
        if (ok) s[0]++; else s[1]++;
    }

    public synchronized long misses() {
        return misses;
    }

    /** 自检字符串。 */
    public synchronized String status() {
        StringBuilder sb = new StringBuilder("voiceSources=").append(sources.size())
            .append(" misses=").append(misses);
        for (Map.Entry<String, VoiceSource> e : sources.entrySet()) {
            int[] s = stats.getOrDefault(e.getKey(), new int[2]);
            sb.append(" | ").append(e.getKey())
              .append(e.getValue().available() ? " ready" : " down")
              .append(" ok=").append(s[0]).append(" fail=").append(s[1]);
        }
        return sb.toString();
    }
}
