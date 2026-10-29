package top.hibermod.ctdscript.broadcast;

/**
 * 广播事件：**什么时候该说话**。
 *
 * <h2>为什么由引擎定义事件，而不是脚本</h2>
 * 事件是「世界发生了什么」，文案是「怎么说」。前者必须统一——
 * 否则每个脚本对「列车进站」的理解都不一样，站台和车上会各说各的。
 * 后者必须开放——不同路局、不同语种、不同制式的措辞完全不同。
 *
 * <p>参考 Tramways 的做法（事件枚举 + 语音变体），但**不照搬它的分类**：
 * 它的九类是有轨电车运营的划分，这里取的是铁路通用语义。
 */
public enum BroadcastEvent {

    /** 列车接近车站。 */
    APPROACHING("approaching", true, true),

    /** 列车停稳。 */
    STOPPED("stopped", true, true),

    /** 列车离站。 */
    DEPARTING("departing", true, true),

    /** 站台变更。 */
    PLATFORM_CHANGED("platform_changed", true, true),

    /** 列车晚点。 */
    DELAYED("delayed", true, true),

    /** 列车取消。 */
    CANCELLED("cancelled", true, false),

    /** 通过（不停车）。 */
    THROUGH("through", true, true),

    /** 终到折返。 */
    TERMINATING("terminating", true, true);

    private final String key;
    private final boolean onTrain;
    private final boolean onPlatform;

    BroadcastEvent(String key, boolean onTrain, boolean onPlatform) {
        this.key = key;
        this.onTrain = onTrain;
        this.onPlatform = onPlatform;
    }

    /** 配置/脚本里用的稳定键，**不要改**——改了已有脚本的监听就静默失效。 */
    public String key() {
        return key;
    }

    public boolean onTrain() {
        return onTrain;
    }

    public boolean onPlatform() {
        return onPlatform;
    }

    /** 按脚本里写的键查找；找不到返回 {@code null}。 */
    public static BroadcastEvent byKey(String key) {
        if (key == null) return null;
        for (BroadcastEvent e : values()) {
            if (e.key.equalsIgnoreCase(key)) return e;
        }
        return null;
    }
}
