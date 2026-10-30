package top.hibermod.ctdscript;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * 记录「脚本注册了什么」，用于热重载的精确回滚。
 *
 * <h2>为什么必须记对象本身，而不是只记 id</h2>
 * 回滚有两种做法：
 * <ol>
 *   <li>重跑旧脚本 —— 要求旧文件还在、还能跑通。改了文件就回不去了；</li>
 *   <li>把旧对象重新注册一遍 —— 只依赖内存里的引用。</li>
 * </ol>
 * 这里选第二种。代价是每个注册项要多存一个引用（几十字节），
 * 换来的是「新脚本写坏了也能回到上一版状态」这个确定性。
 *
 * <h2>为什么不记「谁注册的」</h2>
 * 回滚是**全有或全无**的：只要有一个脚本失败，整批回滚。
 * 按文件粒度回滚会留下「一半新一半旧」的注册表，那比不回滚更难查。
 */
public final class ScriptRegistry {

    /** 注册项类别，决定回滚时用哪个 register 方法。 */
    public enum Kind {
        LAMP, ASPECT, SIGNAL_TYPE, SIGNAL_SYSTEM, DEVICE_TYPE, SERVICE, HUD
    }

    /** 一条记录。{@code value} 是注册时传进去的那个对象。 */
    public record Entry(Kind kind, ResourceLocation id, Object value) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private ScriptRegistry() {}

    public static void add(Kind kind, ResourceLocation id, Object value) {
        ENTRIES.removeIf(e -> e.kind() == kind && e.id().equals(id));
        ENTRIES.add(new Entry(kind, id, value));
    }

    public static List<Entry> snapshot() {
        return List.copyOf(ENTRIES);
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static void clear() {
        ENTRIES.clear();
    }

    /**
     * 把快照里的条目重新注册回全局注册表。
     *
     * <p>调用前注册表必须已经 unfreeze，调用后由调用方重新 freeze。
     * 单条失败不中断其余：残缺的注册表也好过完全空的。
     *
     * @return 成功恢复的条数
     */
    public static int restore(List<Entry> snapshot) {
        int restored = 0;
        for (Entry e : snapshot) {
            try {
                switch (e.kind()) {
                    case LAMP -> {
                        top.hibermod.ctdapi.signal.SignalRegistries.registerLamp(
                            (top.hibermod.ctdapi.signal.LampColor) e.value());
                        restored++;
                    }
                    case ASPECT -> {
                        top.hibermod.ctdapi.signal.SignalRegistries.registerAspect(
                            (top.hibermod.ctdapi.signal.Aspect) e.value());
                        restored++;
                    }
                    case SIGNAL_TYPE -> {
                        top.hibermod.ctdapi.signal.SignalRegistries.registerType(
                            (top.hibermod.ctdapi.signal.SignalType) e.value());
                        restored++;
                    }
                    case SIGNAL_SYSTEM -> {
                        top.hibermod.ctdapi.signal.SignalRegistries.registerSystem(
                            (top.hibermod.ctdapi.signal.SignalSystem) e.value());
                        restored++;
                    }
                    case DEVICE_TYPE -> {
                        top.hibermod.ctdapi.device.Devices.register(
                            (top.hibermod.ctdapi.device.Devices.DeviceType) e.value());
                        restored++;
                    }
                    case HUD -> {
                        top.hibermod.ctdscript.content.ContentRegistry.addHud(
                            (top.hibermod.ctdscript.content.HudDefinition) e.value());
                        restored++;
                    }
                    case SERVICE -> {
                        top.hibermod.ctdapi.timetable.Service.register(
                            (top.hibermod.ctdapi.timetable.Service) e.value());
                        restored++;
                    }
                }
            } catch (Throwable t) {
                System.err.println("[ctdscript] rollback of " + e.kind() + " " + e.id()
                    + " failed: " + t.getMessage());
            }
        }
        ENTRIES.clear();
        ENTRIES.addAll(snapshot);
        return restored;
    }

    /** 从全局注册表里删掉全部脚本注册项。 */
    public static int removeAll(List<Entry> entries) {
        int removed = 0;
        for (Entry e : entries) {
            boolean ok = switch (e.kind()) {
                case LAMP, ASPECT, SIGNAL_TYPE, SIGNAL_SYSTEM ->
                    top.hibermod.ctdapi.signal.SignalRegistries.remove(e.id());
                case DEVICE_TYPE -> top.hibermod.ctdapi.device.Devices.remove(e.id());
                case SERVICE -> top.hibermod.ctdapi.timetable.Service.remove(e.id());
                case HUD -> top.hibermod.ctdscript.content.ContentRegistry.removeHud(e.id());
            };
            if (ok) removed++;
        }
        ENTRIES.clear();
        return removed;
    }
}
