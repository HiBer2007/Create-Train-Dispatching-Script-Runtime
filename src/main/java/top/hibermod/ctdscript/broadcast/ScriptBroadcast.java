package top.hibermod.ctdscript.broadcast;

import org.graalvm.polyglot.HostAccess;

/**
 * 交给脚本的广播上下文（**只读视图**）。
 *
 * <h2>为什么不直接把 {@link BroadcastMessage} 递过去</h2>
 * 沙箱是 {@code HostAccess.EXPLICIT}：只有显式导出的成员对脚本可见。
 * record 的组件访问器没有标注，脚本里写 {@code msg.train} 会直接抛
 * {@code AttributeError: foreign object has no attribute 'train'}——
 * 而不是返回空值。所以这里逐个方法标注导出。
 *
 * <p>顺便把字段名做成脚本习惯的下划线风格，与其它 API 保持一致。
 */
public final class ScriptBroadcast {

    private final BroadcastMessage message;

    public ScriptBroadcast(BroadcastMessage message) {
        this.message = message;
    }

    /** 事件键，如 {@code approaching}。 */
    @HostAccess.Export
    public String event() {
        return message.event().key();
    }

    /** {@code train} 或 {@code platform}。 */
    @HostAccess.Export
    public String scope() {
        return message.scope();
    }

    /** 车次/车辆编号，可能为空串。 */
    @HostAccess.Export
    public String train() {
        return message.train();
    }

    @HostAccess.Export
    public String station() {
        return message.station();
    }

    @HostAccess.Export
    public String platform() {
        return message.platform();
    }

    /** 晚点秒数；负值表示早点。 */
    @HostAccess.Export
    public int delay_seconds() {
        return message.delaySeconds();
    }

    /** 已渲染的文案（为空时表示需要脚本生成）。 */
    @HostAccess.Export
    public String text() {
        return message.text();
    }
}
