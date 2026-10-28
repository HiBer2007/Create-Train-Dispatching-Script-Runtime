package top.hibermod.ctdscript.bridge;

import org.graalvm.polyglot.HostAccess;

/**
 * {@code ctd.kernel}：把 Python 对象注册成 {@code DispatchKernel}。
 *
 * <p><b>P0 阶段尚未实现。</b>它需要一层适配器把 Python 的 {@code on_frame(ctx, out)}
 * 包成 Java 的 {@code DispatchKernel#onFrame}，并且为 {@code FrameContext} /
 * {@code DispatchFrame} 提供 {@code @Export} 门面——因为运行时是
 * {@code HostAccess.EXPLICIT}，API 的接口本身不带注解，Python 直接调用会看不到方法。
 *
 * <p>这道门面是 P1 的主要工作量，也是「脚本能看见多少 API」的边界定义处。
 */
public final class KernelApi {

    public static final KernelApi INSTANCE = new KernelApi();

    private KernelApi() {}

    @HostAccess.Export
    public String register(Object pythonObject) {
        throw new UnsupportedOperationException(
            "ctd.kernel.register() is not implemented yet (roadmap P1). "
                + "It needs DispatchKernel adapters plus @Export facades for FrameContext/DispatchFrame.");
    }
}
