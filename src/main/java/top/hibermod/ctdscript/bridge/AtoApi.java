package top.hibermod.ctdscript.bridge;

import org.graalvm.polyglot.HostAccess;

/**
 * {@code ctd.ato}：把 Python 对象注册成 {@code AtoEngine}。
 *
 * <p><b>P0 阶段尚未实现</b>（roadmap P2）。与 {@link KernelApi} 同样的原因：
 * 需要适配器 + {@code AtoEngine.Context} 的 {@code @Export} 门面。
 *
 * <p>实现时必须保留 API 的铁律：**一个 ATO 对应一套信号制度**，
 * 且校验发生在 {@code AtoEngines.freeze()} 里，**脚本不能绕过**。
 */
public final class AtoApi {

    public static final AtoApi INSTANCE = new AtoApi();

    private AtoApi() {}

    @HostAccess.Export
    public String register(Object pythonObject) {
        throw new UnsupportedOperationException(
            "ctd.ato.register() is not implemented yet (roadmap P2). "
                + "Its signal-system binding must still be enforced by AtoEngines.freeze().");
    }
}
