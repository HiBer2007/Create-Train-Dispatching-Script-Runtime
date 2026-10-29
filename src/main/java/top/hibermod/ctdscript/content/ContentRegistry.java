package top.hibermod.ctdscript.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 启动期收集的「脚本内容」：方块与 HUD 定义。
 *
 * <h2>为什么单独一个注册表</h2>
 * 这两类东西都只能在启动期确定：
 * <ul>
 *   <li><b>方块</b>要在 {@code RegisterEvent} 里进注册表，之后加不进去；</li>
 *   <li><b>纹理</b>要在资源包加载前就位，之后换不掉。</li>
 * </ul>
 * 所以它们和 {@code signal/device/timetable} 一样属于**定义类**，
 * 热重载时不允许改动（改了也没有意义，注册表不会重新打开）。
 *
 * <p>收集发生在模组构造期，消费发生在之后的注册事件与资源加载事件。
 * 中间只传数据，不传 Minecraft 对象——这是能让它工作起来的唯一姿势。
 */
public final class ContentRegistry {

    private static final List<BlockDefinition> BLOCKS = new ArrayList<>();
    private static final List<HudDefinition> HUDS = new ArrayList<>();
    private static volatile boolean sealed;

    private ContentRegistry() {}

    // ── 方块 ──────────────────────────────────────────────────────────────

    public static synchronized BlockDefinition addBlock(BlockDefinition def) {
        requireOpen("block");
        for (BlockDefinition b : BLOCKS) {
            if (b.id().equals(def.id())) {
                throw new IllegalStateException("duplicate block id: " + def.id());
            }
        }
        BLOCKS.add(def);
        return def;
    }

    public static List<BlockDefinition> blocks() {
        return Collections.unmodifiableList(BLOCKS);
    }

    // ── HUD ───────────────────────────────────────────────────────────────

    public static synchronized HudDefinition addHud(HudDefinition def) {
        requireOpen("hud");
        for (HudDefinition h : HUDS) {
            if (h.id().equals(def.id())) {
                throw new IllegalStateException("duplicate hud id: " + def.id());
            }
        }
        HUDS.add(def);
        return def;
    }

    public static List<HudDefinition> huds() {
        return Collections.unmodifiableList(HUDS);
    }

    // ── 封存 ──────────────────────────────────────────────────────────────

    /**
     * 封存：此后不再接受新定义。
     *
     * <p>调用点是注册事件之前。热重载期间脚本再调 {@code block.register} 会得到
     * 「定义类只能在启动期注册」的明确异常，而不是静默无效。
     */
    public static void seal() {
        sealed = true;
    }

    public static boolean isSealed() {
        return sealed;
    }

    /** 测试用：清空并重新打开。 */
    public static synchronized void resetForTesting() {
        BLOCKS.clear();
        HUDS.clear();
        sealed = false;
    }

    private static void requireOpen(String what) {
        if (sealed) {
            throw new IllegalStateException(
                what + " registration is closed: content definitions are startup-only "
                    + "(blocks and textures cannot be added after the registries open)");
        }
    }

    /** 摘要，供启动日志使用。 */
    public static String summary() {
        return "blocks=" + BLOCKS.size() + " huds=" + HUDS.size();
    }
}
