package top.hibermod.ctdscript.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源注册表：{@code <prefix>:<arg>} → {@link DisplaySource}。
 *
 * <h2>为什么按前缀分派</h2>
 * 板子上存的是一个字符串（要能写进 NBT、能手工敲命令）。
 * 前缀决定谁来解释它：{@code text:} 是字面量，{@code time:} 是时钟，
 * {@code pids:} 是脚本注册的。加一种源不用改方块、不用改存档格式。
 *
 * <h2>内置的两种</h2>
 * <ul>
 *   <li>{@code text:A|B} —— 直接显示两行字。这是「先让它有东西可显示」的兜底，
 *       也是手工测试最省事的一条；</li>
 *   <li>{@code time:} —— 当前游戏刻换算的游戏内时间，用来确认「真的在刷新」。</li>
 * </ul>
 */
public final class DisplaySources {

    private static final Map<String, DisplaySource> SOURCES = new LinkedHashMap<>();

    static {
        // 字面量：用 | 分行
        register("text", ctx -> {
            String raw = ctx.argOr("");
            if (raw.isBlank()) return null;
            List<String> out = new ArrayList<>();
            for (String part : raw.split("\\|")) out.add(part);
            return out;
        });

        // 时钟：给「这块板子到底有没有在 tick」一个肉眼可见的证据
        register("time", ctx -> {
            long ticks = ctx.gameTime() % 24000L;
            long hours = (ticks / 1000 + 6) % 24;
            long minutes = (ticks % 1000) * 60 / 1000;
            return List.of(String.format("%02d:%02d", hours, minutes));
        });
    }

    private DisplaySources() {}

    public static void register(String prefix, DisplaySource source) {
        if (prefix == null || prefix.isBlank() || source == null) return;
        String key = prefix.toLowerCase();
        DisplaySource previous = SOURCES.put(key, source);
        if (previous != null && previous != source) {
            // 两个模组抢同一个前缀：必须吵，否则脚本行为会「莫名其妙变了」
            System.err.println("[ctdscript] display source '" + key + "' replaced "
                + previous.getClass().getName() + " with " + source.getClass().getName());
        }
    }

    /**
     * 解析一个源 id 并取值。
     *
     * @param spec 形如 {@code text:一号线|往人民广场}；为空返回 {@code null}
     */
    public static List<String> resolve(String spec, DisplayContext ctx) {
        if (spec == null || spec.isBlank()) return null;

        int colon = spec.indexOf(':');
        String prefix = (colon < 0 ? spec : spec.substring(0, colon)).toLowerCase();
        String arg = colon < 0 ? "" : spec.substring(colon + 1);

        DisplaySource source = SOURCES.get(prefix);
        if (source == null) return null;

        DisplayContext withArg = new DisplayContext(
            ctx.level(), ctx.pos(), arg, ctx.gameTime());

        try {
            return source.lines(withArg);
        } catch (Throwable t) {
            System.err.println("[ctdscript] display source '" + spec + "' failed: " + t);
            return null;
        }
    }

    public static java.util.Set<String> prefixes() {
        return java.util.Set.copyOf(SOURCES.keySet());
    }

    public static String summary() {
        return SOURCES.size() + " sources " + SOURCES.keySet();
    }
}
