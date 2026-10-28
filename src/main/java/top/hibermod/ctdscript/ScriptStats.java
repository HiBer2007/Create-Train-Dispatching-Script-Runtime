package top.hibermod.ctdscript;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 脚本执行耗时的滑动统计。
 *
 * <p>为什么要统计而不是只看平均：**一次 200ms 的卡顿比 100 次 2ms 严重得多**，
 * 平均值会把前者藏起来。所以看 p50 / p99 / max。
 *
 * <p>用固定容量的环形缓冲，不分配、不排序全量——每帧都要写一次，不能在这里制造 GC 压力。
 */
public final class ScriptStats {

    private final long[] samplesNanos;
    private int cursor;
    private int filled;

    private final AtomicLong total = new AtomicLong();
    private final AtomicLong maxNanos = new AtomicLong();
    private final AtomicLong overBudget = new AtomicLong();

    public ScriptStats(int capacity) {
        this.samplesNanos = new long[Math.max(8, capacity)];
    }

    /** 记一次调用。 */
    public void record(long nanos, long budgetNanos) {
        samplesNanos[cursor] = nanos;
        cursor = (cursor + 1) % samplesNanos.length;
        if (filled < samplesNanos.length) filled++;

        total.incrementAndGet();
        maxNanos.accumulateAndGet(nanos, Math::max);
        if (budgetNanos > 0 && nanos > budgetNanos) overBudget.incrementAndGet();
    }

    public long count() { return total.get(); }

    public long overBudget() { return overBudget.get(); }

    public double maxMillis() { return maxNanos.get() / 1_000_000.0; }

    /** 第 {@code p} 百分位（毫秒）。没有样本时返回 0。 */
    public double percentileMillis(double p) {
        if (filled == 0) return 0;
        long[] copy = Arrays.copyOf(samplesNanos, filled);
        Arrays.sort(copy);
        int idx = (int) Math.round((p / 100.0) * (filled - 1));
        idx = Math.max(0, Math.min(filled - 1, idx));
        return copy[idx] / 1_000_000.0;
    }

    public double meanMillis() {
        if (filled == 0) return 0;
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += samplesNanos[i];
        return (sum / (double) filled) / 1_000_000.0;
    }

    public String summary() {
        return String.format(
            "n=%d p50=%.2fms p99=%.2fms max=%.2fms mean=%.2fms over=%d",
            count(), percentileMillis(50), percentileMillis(99), maxMillis(), meanMillis(), overBudget());
    }

    public void reset() {
        cursor = 0;
        filled = 0;
        total.set(0);
        maxNanos.set(0);
        overBudget.set(0);
        Arrays.fill(samplesNanos, 0);
    }
}
