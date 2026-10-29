package top.hibermod.ctdscript.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

/**
 * 拓展注册表：让**别的模组**把自己的能力交给脚本用。
 *
 * <h2>为什么需要</h2>
 * 脚本引擎不可能预知所有需求——汽鸣铁道有自己的道岔、某个附属有自己的电报机、
 * 某个整合包有自己的信号灯。没有这条口子，这些能力要么写死进我们的模组，
 * 要么附属只能去改我们的源码。
 *
 * <h2>用法（附属模组一侧）</h2>
 * <pre>
 * Extensions.register("railway", new MyRailwayBridge());
 * </pre>
 * 之后任何脚本都能：
 * <pre>
 * from ctd import ext
 * ext.get("railway").set_switch(node, "left")
 * </pre>
 *
 * <h2>约定</h2>
 * <ul>
 *   <li><b>命名空间必须稳定</b>：脚本里写的是字符串 {@code "railway"}，
 *       改了就等于把已有脚本全废掉。建议用模组 id。</li>
 *   <li><b>对象自己负责导出</b>：沙箱是 {@code HostAccess.EXPLICIT}，
 *       被注册对象上只有标了 {@code @HostAccess.Export} 的方法对脚本可见。
 *       这不是限制，是保护——不标注就等于「内部实现」，脚本不该调。</li>
 *   <li><b>覆盖要吵</b>：同名注册会覆盖并打日志。
 *       两个模组抢一个命名空间时，静默覆盖会让人查很久。</li>
 * </ul>
 */
public final class Extensions {

    private static final Map<String, Object> REGISTRY = new LinkedHashMap<>();

    private Extensions() {}

    /**
     * 注册一个拓展对象。
     *
     * @param namespace 稳定命名空间（建议用模组 id）
     * @param host      对脚本暴露的对象；其方法需标注 {@code @HostAccess.Export}
     */
    public static synchronized void register(String namespace, Object host) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("extension namespace is required");
        }
        if (host == null) {
            throw new IllegalArgumentException("extension object is required for " + namespace);
        }

        String key = namespace.toLowerCase();
        Object previous = REGISTRY.put(key, host);
        if (previous != null && previous != host) {
            // 两个模组抢同一个名字：必须吵，不能静默覆盖
            System.err.println("[ctdscript] extension '" + key + "' replaced "
                + previous.getClass().getName() + " with " + host.getClass().getName());
        }
    }

    public static synchronized boolean has(String namespace) {
        return namespace != null && REGISTRY.containsKey(namespace.toLowerCase());
    }

    public static synchronized Object get(String namespace) {
        return namespace == null ? null : REGISTRY.get(namespace.toLowerCase());
    }

    public static synchronized java.util.Set<String> names() {
        return java.util.Set.copyOf(REGISTRY.keySet());
    }

    public static synchronized int size() {
        return REGISTRY.size();
    }

    /** 热重载不清拓展：它们属于模组，不属于脚本。 */
    public static synchronized String summary() {
        return "extensions=" + REGISTRY.size() + " " + REGISTRY.keySet();
    }

    /**
     * 交给脚本的那一层（{@code ctd.ext}）。
     *
     * <p>只暴露 {@link #get} 与 {@link #names}：
     * 注册是非脚本行为——脚本没有资格替换别人的实现。
     */
    public static final class ScriptView {

        public static final ScriptView INSTANCE = new ScriptView();

        private ScriptView() {}

        /** 取一个已注册的拓展；不存在返回 {@code null}（脚本里判断即可）。 */
        @HostAccess.Export
        public Object get(String namespace) {
            return Extensions.get(namespace);
        }

        @HostAccess.Export
        public boolean has(String namespace) {
            return Extensions.has(namespace);
        }

        @HostAccess.Export
        public String[] names() {
            return Extensions.names().toArray(new String[0]);
        }
    }
}
