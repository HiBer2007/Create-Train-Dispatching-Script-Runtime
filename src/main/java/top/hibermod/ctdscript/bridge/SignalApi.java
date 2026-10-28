package top.hibermod.ctdscript.bridge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.polyglot.HostAccess;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.signal.Aspect;
import top.hibermod.ctdapi.signal.LampColor;
import top.hibermod.ctdapi.signal.SignalRegistries;
import top.hibermod.ctdapi.signal.SignalSemantics;
import top.hibermod.ctdapi.signal.SignalSystem;
import top.hibermod.ctdapi.signal.SignalType;

/**
 * {@code ctd.signal}：信号制度 / 机型 / 语义的**描述性注册**。
 *
 * <p>Python 侧用法：
 * <pre>
 * from ctd import signal
 *
 * sys = signal.system("ctd", "mainline_4", "干线四显示")
 * t = signal.type("ctd", "home_4", sys.id())
 * t.lamp(signal.RED).lamp(signal.YELLOW).lamp(signal.YELLOW).lamp(signal.GREEN)
 * t.expresses(signal.STOP, 0)
 * t.expresses(signal.PROCEED, 3)
 * t.semantics(signal.HOME).register()
 * sys.type(t).register()
 * </pre>
 *
 * <p>所有可被脚本访问的成员都标了 {@link HostAccess.Export}——
 * 运行时的 HostAccess 是 EXPLICIT，没标注的在 Python 里不可见。
 */
public final class SignalApi {

    private static ResourceLocation rl(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    // ── 常量：灯色 ────────────────────────────────────────────────────────

    @HostAccess.Export public final LampColor RED = new LampColor(rl("ctdapi", "red"), 0xFF0000);
    @HostAccess.Export public final LampColor YELLOW = new LampColor(rl("ctdapi", "yellow"), 0xFFC800);
    @HostAccess.Export public final LampColor GREEN = new LampColor(rl("ctdapi", "green"), 0x00D000);
    @HostAccess.Export public final LampColor WHITE = new LampColor(rl("ctdapi", "white"), 0xE8E8E8);
    @HostAccess.Export public final LampColor LUNAR_WHITE = new LampColor(rl("ctdapi", "lunar_white"), 0xF0F0FF);
    @HostAccess.Export public final LampColor BLUE = new LampColor(rl("ctdapi", "blue"), 0x2A5AD0);
    @HostAccess.Export public final LampColor PURPLE = new LampColor(rl("ctdapi", "purple"), 0x8A3AC8);

    // ── 常量：语义 ────────────────────────────────────────────────────────

    @HostAccess.Export public final Aspect STOP = aspect("stop", 0.00, Aspect.Category.STOP);
    @HostAccess.Export public final Aspect CAUTION = aspect("caution", 0.55, Aspect.Category.CAUTION);
    @HostAccess.Export public final Aspect ADVANCE_CAUTION =
        aspect("advance_caution", 0.40, Aspect.Category.ADVANCE_CAUTION);
    @HostAccess.Export public final Aspect PROCEED = aspect("proceed", 1.00, Aspect.Category.PROCEED);
    @HostAccess.Export public final Aspect SHUNT = aspect("shunt", 0.30, Aspect.Category.SHUNT);
    @HostAccess.Export public final Aspect CALL_ON = aspect("call_on", 0.20, Aspect.Category.CALL_ON);
    @HostAccess.Export public final Aspect DARK = aspect("dark", 0.00, Aspect.Category.DARK);

    // ── 常量：联锁语义 ────────────────────────────────────────────────────

    @HostAccess.Export public final SignalSemantics HOME = SignalSemantics.HOME;
    @HostAccess.Export public final SignalSemantics STARTING = SignalSemantics.STARTING;
    @HostAccess.Export public final SignalSemantics BLOCK = SignalSemantics.BLOCK;
    @HostAccess.Export public final SignalSemantics SHUNT_SEMANTICS = SignalSemantics.SHUNT;
    @HostAccess.Export public final SignalSemantics DISTANT = SignalSemantics.DISTANT;
    @HostAccess.Export public final SignalSemantics ROUTE_INDICATOR = SignalSemantics.ROUTE_INDICATOR;

    private static Aspect aspect(String path, double speed, Aspect.Category category) {
        return new Aspect(rl("ctdapi", path),
            Component.translatable("aspect.ctdapi." + path), speed, category);
    }

    // ── 工厂 ──────────────────────────────────────────────────────────────

    /** 新建一个信号制度描述。 */
    @HostAccess.Export
    public SystemBuilder system(String namespace, String path, String displayName) {
        return new SystemBuilder(rl(namespace, path), displayName);
    }

    /** 新建一个信号机型描述。{@code systemId} 形如 {@code "ctd:mainline_4"}。 */
    @HostAccess.Export
    public TypeBuilder type(String namespace, String path, String systemId) {
        return new TypeBuilder(rl(namespace, path), ResourceLocation.parse(systemId));
    }

    /** 注册一个独立语义（内置之外的自定义语义）。 */
    @HostAccess.Export
    public void registerAspect(String namespace, String path, String displayName,
                               double speedFraction, String category) {
        Aspect.Category cat = Aspect.Category.valueOf(category.toUpperCase(java.util.Locale.ROOT));
        SignalRegistries.registerAspect(
            new Aspect(rl(namespace, path), Component.literal(displayName), speedFraction, cat));
    }

    // ── 描述性对象 ────────────────────────────────────────────────────────

    /** {@code signal.system(...)} 返回的东西。 */
    public static final class SystemBuilder {
        private final ResourceLocation id;
        private final String displayName;
        private final List<LampColor> palette = new ArrayList<>();
        private final Set<ResourceLocation> aspects = new LinkedHashSet<>();
        private final Set<ResourceLocation> types = new LinkedHashSet<>();
        private ResourceLocation fallback;

        SystemBuilder(ResourceLocation id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        @HostAccess.Export
        public String id() {
            return id.toString();
        }

        @HostAccess.Export
        public SystemBuilder palette(LampColor... colors) {
            palette.addAll(List.of(colors));
            return this;
        }

        @HostAccess.Export
        public SystemBuilder aspect(Aspect aspect) {
            aspects.add(aspect.id());
            return this;
        }

        @HostAccess.Export
        public SystemBuilder type(TypeBuilder type) {
            types.add(type.idOrigin);
            return this;
        }

        @HostAccess.Export
        public SystemBuilder fallback(TypeBuilder type) {
            types.add(type.idOrigin);
            this.fallback = type.idOrigin;
            return this;
        }

        /** 落库。这一步之前对象只是描述。 */
        @HostAccess.Export
        public String register() {
            SignalSystem.Builder b = SignalSystem.builder(id).name(Component.literal(displayName));
            if (!palette.isEmpty()) b.palette(palette.toArray(new LampColor[0]));
            aspects.forEach(b::aspectId);
            types.forEach(b::typeId);
            if (fallback != null) b.fallbackId(fallback);
            SignalRegistries.registerSystem(b.build());
            return id.toString();
        }
    }

    /** {@code signal.type(...)} 返回的东西。 */
    public static final class TypeBuilder {
        private final ResourceLocation idOrigin;
        private final ResourceLocation systemId;
        private final List<LampColor> lamps = new ArrayList<>();
        private final List<Aspect> expressed = new ArrayList<>();
        private final List<int[]> slots = new ArrayList<>();
        private SignalSemantics semantics = SignalSemantics.BLOCK;
        private int priority;

        TypeBuilder(ResourceLocation id, ResourceLocation systemId) {
            this.idOrigin = id;
            this.systemId = systemId;
        }

        @HostAccess.Export
        public TypeBuilder lamp(LampColor color) {
            lamps.add(color);
            return this;
        }

        @HostAccess.Export
        public TypeBuilder expresses(Aspect aspect, int... lampSlots) {
            expressed.add(aspect);
            slots.add(lampSlots);
            return this;
        }

        @HostAccess.Export
        public TypeBuilder semantics(SignalSemantics semantics) {
            this.semantics = semantics;
            return this;
        }

        @HostAccess.Export
        public TypeBuilder priority(int priority) {
            this.priority = priority;
            return this;
        }

        @HostAccess.Export
        public String register() {
            SignalType.Builder b = SignalType.builder(idOrigin)
                .system(systemId)
                .name(Component.literal(idOrigin.getPath()))
                .semantics(semantics)
                .priority(priority);
            lamps.forEach(b::lamp);
            for (int i = 0; i < expressed.size(); i++) {
                b.expresses(expressed.get(i).id(), slots.get(i));
            }
            SignalRegistries.registerType(b.build());
            return idOrigin.toString();
        }
    }
}
