package top.hibermod.ctdscript.bridge;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.DispatchKernels;
import top.hibermod.ctdscript.adapters.PythonKernel;

/**
 * {@code ctd.kernel}：把 Python 对象注册成 {@code DispatchKernel}。
 *
 * <p>用法：
 * <pre>
 * from ctd import kernel
 *
 * class FixedBlockKernel:
 *     def on_frame(self, ctx, out):
 *         ...
 *
 * kernel.register("mymod", "fixed_block", FixedBlockKernel())
 * </pre>
 *
 * <p><b>为什么 id 是显式传的</b>：原方案里让脚本实现 {@code id()} 返回元组，
 * 但那要求桥接层去猜返回值类型（列表？元组？字符串？），错一次就是一个难查的启动失败。
 * 显式参数只有一种写法，错了立刻报。
 */
public final class KernelApi {

    public static final KernelApi INSTANCE = new KernelApi();

    /** 本进程注册过的脚本内核，供 reload 时回滚。 */
    private final List<ResourceLocation> registered = new ArrayList<>();

    /** id → 谁注册的（脚本名）。用来区分「热重载换实现」和「两个脚本抢同一个 id」。 */
    private final java.util.Map<ResourceLocation, String> owners = new java.util.HashMap<>();

    private KernelApi() {}

    @HostAccess.Export
    public String register(String namespace, String path, Value value) {
        // 参数类型必须是 Value：声明成 Object 时 GraalPy 传过来的是包装对象，
        // instanceof Value 判不出来。
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        PythonKernel kernel = new PythonKernel(id, value);

        // 热重载：同一 id 第二次进来是「换实现」而不是「重复注册」。
        // 正常注册路径（宿主/其它模组）仍然走 DispatchKernels.register，重复即抛。
        String source = top.hibermod.ctdscript.ScriptRuntime.currentScript();
        String owner = owners.get(id);

        // 同一个 id 被**另一个脚本**注册，不是热重载，是冲突。
        // 早先这里一律 replace，于是后来者静默顶掉前面那个——
        // 表现只是「注册数比文件数少一个」，没有任何线索。
        if (owner != null && !owner.equals(source)) {
            throw new IllegalStateException(
                "内核 id 冲突: " + id + " 已被 " + owner + " 注册，" + source + " 又想注册");
        }

        if (DispatchKernels.isRegistered(id)) {
            DispatchKernels.replace(id, () -> kernel);
            owners.put(id, source);
        } else {
            DispatchKernels.register(id, () -> kernel);
            registered.add(id);
            owners.put(id, source);
        }
        return id.toString();
    }

    /** 已注册的脚本内核 id。 */
    @HostAccess.Export
    public List<String> registered() {
        List<String> out = new ArrayList<>();
        for (ResourceLocation id : registered) out.add(id.toString());
        return out;
    }

    public List<ResourceLocation> registeredIds() {
        return List.copyOf(registered);
    }

    /**
     * 撤销本进程通过脚本注册的**全部内核**（热重载用）。
     *
     * <p>早先这里只 {@code registered.clear()}，既没从 {@code DispatchKernels} 注销，
     * 也没有任何地方调用它。后果：脚本换个内核 id 之后，旧 id 永远留在注册表里，
     * 继续用绑在**已关闭 Python 运行时**上的对象跑——日志里会看到两个内核同时 active，
     * 旧的还在打警告。
     */
    public void reset() {
        for (ResourceLocation id : registered) {
            try {
                DispatchKernels.remove(id);
            } catch (Throwable t) {
                // 注销失败不该挡住重载，但不能静默
                top.hibermod.ctdapi.diagnostics.Failures.caught("Bridge 撤销内核", t);
                // 注销失败不该挡住重载
            }
        }
        registered.clear();
    }
}
