package top.hibermod.ctdscript.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 「哪架信号机服务于哪套信号制度」。
 *
 * <h2>为什么需要一张表</h2>
 * 信号方块是 Create 的，我们改不了它的 NBT 结构；而一套线路上可能同时存在
 * 传统固定闭塞与 CBTC 两种制度。哪种制度说了算，不能由「最后跑的脚本」决定——
 * 那会变成同一架灯在不同时刻属于不同系统。
 *
 * <p>所以显式登记：一架信号机只属于一套制度（用户的要求），
 * 未登记的信号机不属于任何制度，内核不去动它。
 *
 * <h2>为什么按信号 id 存</h2>
 * 信号机可以被拆掉重放，位置会变，但边点 id 是稳定的；
 * 按坐标存会在车场重建之后全部失效。
 */
public final class SignalAssignments extends SavedData {

    private static final String DATA_NAME = "ctdscript_signal_systems";

    /** 信号机 id → 制度 id（如 {@code ctd:cbtc}）。 */
    private final Map<UUID, String> bySignal = new HashMap<>();

    public static SignalAssignments get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(SignalAssignments::new, SignalAssignments::load),
            DATA_NAME);
    }

    /** 这架信号机服务于哪套制度；未登记返回空串。 */
    public String systemOf(UUID signalId) {
        String s = signalId == null ? null : bySignal.get(signalId);
        return s == null ? "" : s;
    }

    public void assign(UUID signalId, String systemId) {
        if (signalId == null) return;
        if (systemId == null || systemId.isBlank()) {
            bySignal.remove(signalId);
        } else {
            bySignal.put(signalId, systemId);
        }
        setDirty();
    }

    public int size() {
        return bySignal.size();
    }

    private static SignalAssignments load(CompoundTag tag, HolderLookup.Provider registries) {
        SignalAssignments data = new SignalAssignments();
        CompoundTag map = tag.getCompound("systems");
        for (String key : map.getAllKeys()) {
            try {
                data.bySignal.put(UUID.fromString(key), map.getString(key));
            } catch (IllegalArgumentException ignored) {
                // 坏条目跳过，别让整份数据读不出来
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag map = new CompoundTag();
        for (var e : bySignal.entrySet()) {
            map.putString(e.getKey().toString(), e.getValue());
        }
        tag.put("systems", map);
        return tag;
    }
}
