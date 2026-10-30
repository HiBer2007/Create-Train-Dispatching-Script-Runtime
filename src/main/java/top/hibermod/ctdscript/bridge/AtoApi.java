package top.hibermod.ctdscript.bridge;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.ato.AtoEngine;
import top.hibermod.ctdapi.ato.AtoEngines;
import top.hibermod.ctdscript.adapters.PythonAto;

/**
 * {@code ctd.ato}：把 Python 对象注册成 {@code AtoEngine}。
 *
 * <pre>
 * from ctd import ato
 *
 * class SimpleAto:
 *     def decide(self, ctx):
 *         if not ctx.can_traction():
 *             return ato.stop("no traction power")
 *         return ato.decision(0.5, 0.0, 0.4, ato.NONE, False, "cruise")
 *
 * ato.register("mymod", "simple_ato", "ctdapi:mainline_4", SimpleAto())
 * </pre>
 *
 * <p><b>信号制度是显式参数</b>，不是从对象里反射出来的。理由同 {@code kernel.register}：
 * 一种写法，错了立刻报。而且注册后仍然要过 {@code AtoEngines.freeze()} 的校验。
 */
public final class AtoApi {

    public static final AtoApi INSTANCE = new AtoApi();

    private final List<ResourceLocation> registered = new ArrayList<>();

    private AtoApi() {}

    // ── 决策常量 ──────────────────────────────────────────────────────────

    @HostAccess.Export public final AtoEngine.DoorAction NONE = AtoEngine.DoorAction.NONE;
    @HostAccess.Export public final AtoEngine.DoorAction OPEN = AtoEngine.DoorAction.OPEN;
    @HostAccess.Export public final AtoEngine.DoorAction CLOSE = AtoEngine.DoorAction.CLOSE;

    // ── 决策工厂 ──────────────────────────────────────────────────────────

    /**
     * 构造一条操纵决策。
     *
     * @param traction    牵引档位 0..1
     * @param brake       制动档位 0..1
     * @param targetSpeed 目标速度（格/tick）
     * @param doors       车门动作
     * @param hold        是否保持停车
     * @param reason      一句话说明（进 HUD 与日志），不要留空
     */
    @HostAccess.Export
    public AtoEngine.Decision decision(double traction, double brake, double targetSpeed,
                                       AtoEngine.DoorAction doors, boolean hold, String reason) {
        return new AtoEngine.Decision(traction, brake, targetSpeed, doors, hold, null,
            Component.literal(reason == null ? "" : reason));
    }

    /** 全制动停车。 */
    @HostAccess.Export
    public AtoEngine.Decision stop(String reason) {
        return AtoEngine.Decision.stop(reason == null ? "" : reason);
    }

    /** 惰行。 */
    @HostAccess.Export
    public AtoEngine.Decision coast(String reason) {
        return AtoEngine.Decision.coast(reason == null ? "" : reason);
    }

    // ── 注册 ──────────────────────────────────────────────────────────────

    /**
     * @param namespace       ATO id 命名空间
     * @param path            ATO id 路径
     * @param signalSystemId  绑定的信号制度（"ctdapi:mainline_4"）
     * @param pythonObject    带 {@code decide(ctx)} 的 Python 对象
     */
    @HostAccess.Export
    public String register(String namespace, String path, String signalSystemId, Value value) {
        // 同 KernelApi：参数必须声明成 Value，声明成 Object 收不到真正的 guest value。
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        ResourceLocation system = ResourceLocation.parse(signalSystemId);

        PythonAto engine = new PythonAto(id, system, value);
        if (AtoEngines.get(id).isPresent()) {
            AtoEngines.replace(engine);      // 热重载
        } else {
            AtoEngines.register(engine);
            registered.add(id);
        }
        return id.toString();
    }

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
     * 撤销本进程通过脚本注册的**全部 ATO 引擎**（热重载用）。
     *
     * <p>与 {@code KernelApi#reset} 同一个问题：只清列表不注销，
     * 脚本换 id 之后旧引擎永远留在注册表里。
     */
    public void reset() {
        for (ResourceLocation id : registered) {
            try {
                AtoEngines.remove(id);
            } catch (Throwable t) {
                // 注销失败不该挡住重载，但不能静默
                top.hibermod.ctdapi.diagnostics.Failures.caught("Bridge 撤销 ATO", t);
                // 注销失败不该挡住重载
            }
        }
        registered.clear();
    }
}
