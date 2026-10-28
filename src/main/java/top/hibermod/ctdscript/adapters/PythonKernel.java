package top.hibermod.ctdscript.adapters;

import org.graalvm.polyglot.Value;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.DispatchFrame;
import top.hibermod.ctdapi.DispatchKernel;
import top.hibermod.ctdapi.FrameContext;
import top.hibermod.ctdapi.KernelEnv;
import top.hibermod.ctdscript.facade.FrameInput;
import top.hibermod.ctdscript.facade.FrameOutput;

/**
 * 把一个 Python 对象包装成 {@link DispatchKernel}。
 *
 * <h2>每帧只跨一次语言边界</h2>
 * 热路径上最贵的是跨语言调用，所以整帧只调用一次 {@code on_frame(ctx, out)}，
 * 而不是「每列车一次」。
 *
 * <h2>异常绝不外泄</h2>
 * 脚本抛异常时：记录 → **丢弃该帧全部指令** → 列车退回 Create 原生行为。
 * 这正好契合 API 的「零指令 = 不干预」原则。
 */
public final class PythonKernel implements DispatchKernel {

    /**
     * 连续失败多少次后自动禁用。
     *
     * <p>禁用不是「注销」——宿主仍然认为这个内核存在，只是它不再写任何指令。
     * 按 API 的「零指令 = 不干预」原则，效果就是退回 Create 原生行为。
     * 之所以不注销：注销需要宿主配合，而脚本自己炸了的时候没人能保证宿主还在正常工作。
     */
    private static final int DISABLE_AFTER = 10;

    private final ResourceLocation id;
    private final Value script;
    private final Value onFrame;
    private KernelEnv env;

    private int consecutiveFailures;
    private int totalFailures;
    private boolean disabled;

    /** 执行耗时统计（p50/p99/max）。tick 预算 50ms，脚本目标 15ms。 */
    private final top.hibermod.ctdscript.ScriptStats stats =
        new top.hibermod.ctdscript.ScriptStats(512);
    private static final long BUDGET_NANOS = 15_000_000L;

    public PythonKernel(ResourceLocation id, Value script) {
        this.id = id;
        this.script = script;
        this.onFrame = script.getMember("on_frame");
        if (onFrame == null || !onFrame.canExecute()) {
            throw new IllegalArgumentException(
                "python kernel '" + id + "' has no callable on_frame(ctx, out)");
        }
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public void onAttach(KernelEnv env) {
        this.env = env;
        // 脚本可选实现 on_attach(env_state)
        Value attach = script.getMember("on_attach");
        if (attach != null && attach.canExecute()) {
            try {
                attach.executeVoid();
            } catch (Exception e) {
                env.log("warn", "ctd-script: {} on_attach failed: {}", id, e.getMessage());
            }
        }
    }

    @Override
    public void onDetach() {
        Value detach = script.getMember("on_detach");
        if (detach != null && detach.canExecute()) {
            try {
                detach.executeVoid();
            } catch (Exception ignored) {
                // 卸载期异常不该影响关服
            }
        }
    }

    @Override
    public void onFrame(FrameContext in, DispatchFrame out) {
        if (disabled) return;                  // 已禁用：零指令 = 退回 Create 原生

        long start = System.nanoTime();
        try {
            FrameOutput fo = new FrameOutput(out, env);
            onFrame.executeVoid(new FrameInput(in), fo);
            consecutiveFailures = 0;
        } catch (Throwable t) {
            consecutiveFailures++;
            totalFailures++;

            boolean timeout = t instanceof top.hibermod.ctdscript.ScriptRuntime.ScriptTimeoutException
                || (t.getMessage() != null && t.getMessage().contains("interrupted"));

            if (consecutiveFailures == 1 || consecutiveFailures % 5 == 0) {
                env.log("error", "ctd-script: kernel {} failed ({} consecutive, {} total){}: {}",
                    id, consecutiveFailures, totalFailures,
                    timeout ? " [timeout]" : "", t.getMessage());
            }

            if (consecutiveFailures >= DISABLE_AFTER) {
                disabled = true;
                env.log("error", "ctd-script: kernel {} disabled after {} consecutive failures; "
                    + "trains fall back to vanilla Create behaviour", id, consecutiveFailures);
            }
            // 本帧没有任何指令
        } finally {
            stats.record(System.nanoTime() - start, BUDGET_NANOS);
        }
    }

    /** 执行耗时统计。 */
    public top.hibermod.ctdscript.ScriptStats stats() {
        return stats;
    }

    /** 是否已被自动禁用。 */
    public boolean disabled() {
        return disabled;
    }

    /** 累计失败次数。 */
    public int totalFailures() {
        return totalFailures;
    }

    /** 连续失败次数，宿主可据此禁用脚本内核。 */
    public int consecutiveFailures() {
        return consecutiveFailures;
    }
}
