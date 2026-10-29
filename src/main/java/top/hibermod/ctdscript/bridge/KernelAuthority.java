package top.hibermod.ctdscript.bridge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内核最后一次下发的移动授权（按列车）。
 *
 * <h2>为什么单独存一份</h2>
 * 授权是**内核算出来的**，不是列车自身的属性。显示器、诊断命令、日志都需要它，
 * 但它们不该各自去问内核——那会把「谁有权读」变成一堆重复调用。
 *
 * <p>这里只做一件事：{@code out.proceed(...)} 时记一笔，读的人直接取。
 * 内核不再下发（停止运行/被移除）时条目会过期，读到的 {@code 0} 表示「没有授权」，
 * 这正是应该显示成未知的情况。
 */
public final class KernelAuthority {

    private static final Map<UUID, Double> LAST = new ConcurrentHashMap<>();

    private KernelAuthority() {}

    /** 内核下发时调用。 */
    public static void put(UUID trainId, double authority) {
        if (trainId == null) return;
        LAST.put(trainId, authority);
    }

    /** 最近一次授权；没记录返回 0。 */
    public static double peek(UUID trainId) {
        Double v = trainId == null ? null : LAST.get(trainId);
        return v == null ? 0.0 : v;
    }

    public static void clear(UUID trainId) {
        LAST.remove(trainId);
    }

    public static void clearAll() {
        LAST.clear();
    }

    public static int size() {
        return LAST.size();
    }
}
