package top.hibermod.ctdscript.adapters;

import org.graalvm.polyglot.Value;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.ato.AtoEngine;
import top.hibermod.ctdscript.facade.AtoContextFacade;

/**
 * 把一个 Python 对象包装成 {@link AtoEngine}。
 *
 * <p><b>不做任何放宽</b>：{@code signalSystemId} 由注册时显式给出，随后仍由
 * {@code AtoEngines.freeze()} 校验「制度存在且唯一」。脚本绕不过去。
 *
 * <p>脚本的 {@code decide(ctx)} 必须返回 {@code ato.decision(...)} 的产物；
 * 返回 {@code None} 等价于「本帧不干预」。
 */
public final class PythonAto implements AtoEngine {

    private final ResourceLocation id;
    private final ResourceLocation signalSystemId;
    private final Value decideFn;

    public PythonAto(ResourceLocation id, ResourceLocation signalSystemId, Value script) {
        this.id = id;
        this.signalSystemId = signalSystemId;
        this.decideFn = script.getMember("decide");
        if (decideFn == null || !decideFn.canExecute()) {
            throw new IllegalArgumentException(
                "python ATO '" + id + "' has no callable decide(ctx)");
        }
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public ResourceLocation signalSystemId() {
        return signalSystemId;
    }

    @Override
    public Decision decide(Context in) {
        Value result = decideFn.execute(new AtoContextFacade(in));

        if (result == null || result.isNull()) {
            return null;                       // 本帧不干预
        }
        if (result.isHostObject()) {
            Object host = result.asHostObject();
            if (host instanceof Decision decision) {
                return decision;
            }
        }
        throw new IllegalStateException(
            "python ATO '" + id + "' returned " + result + ", expected ato.decision(...) or None");
    }
}
