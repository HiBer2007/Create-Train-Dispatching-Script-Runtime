package top.hibermod.ctdscript.bridge;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdscript.content.BlockDefinition;
import top.hibermod.ctdscript.content.ContentRegistry;
import top.hibermod.ctdscript.content.HudDefinition;

/**
 * {@code ctd.content}：脚本注册**方块**与 **HUD** 的入口。
 *
 * <pre>
 * from ctd import content
 *
 * # 一个方块。纹理放在 config/ctd/assets/&lt;ns&gt;/textures/block/&lt;name&gt;.png
 * content.block("mysys", "bridge_panel", "mysys:block/bridge_panel", 2.0, 0, True)
 *
 * # 一块 HUD。引擎画，脚本只下单。
 * def draw(h):
 *     h.text(8, 8, "hello", 0xFFFFFF)
 * content.hud("mysys", "badge", draw, 2, 0)
 * </pre>
 *
 * <h2>为什么这两个放在启动期</h2>
 * 方块要在 {@code RegisterEvent} 里进注册表，纹理要在资源包加载前就位——
 * 两件事都只发生一次。热重载期间再调这些方法会得到明确的异常，
 * 而不是「看起来注册成功、实际上没有」。
 */
public final class ContentApi {

    public static final ContentApi INSTANCE = new ContentApi();

    private ContentApi() {}

    /**
     * 注册一个方块。
     *
     * @param namespace  命名空间
     * @param path       方块名
     * @param texture    纹理 id，可省略前缀（默认 {@code namespace:block/path}）
     * @param hardness   硬度
     * @param light      自发光 0–15
     * @param solid      是否有碰撞
     * @return 方块 id
     */
    @HostAccess.Export
    public String block(String namespace, String path, String texture,
                        double hardness, int light, boolean solid) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        ResourceLocation tex = ResourceLocation.tryParse(
            (texture == null || texture.isBlank())
                ? (namespace + ":block/" + path)
                : texture);
        if (tex == null) {
            throw new IllegalArgumentException("bad texture id for block " + id);
        }
        ContentRegistry.addBlock(new BlockDefinition(id, tex, (float) hardness, light, solid, null));
        return id.toString();
    }

    /** 注册一个方块，可指定显示名。 */
    @HostAccess.Export
    public String block(String namespace, String path, String texture,
                        double hardness, int light, boolean solid, String displayName) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        ResourceLocation tex = ResourceLocation.tryParse(
            (texture == null || texture.isBlank()) ? (namespace + ":block/" + path) : texture);
        if (tex == null) throw new IllegalArgumentException("bad texture id for block " + id);
        ContentRegistry.addBlock(new BlockDefinition(id, tex, (float) hardness, light, solid,
            displayName));
        return id.toString();
    }

    /** 注册一个方块，纹理与 id 同名。 */
    @HostAccess.Export
    public String block(String namespace, String path) {
        return block(namespace, path, null, 1.0, 0, true);
    }

    /**
     * 注册一块 HUD。
     *
     * @param draw          回调，签名 {@code draw(canvas)}
     * @param intervalTicks 刷新间隔，最小 1（20 = 每秒一次）
     * @param order         绘制顺序，小的先画
     */
    @HostAccess.Export
    public String hud(String namespace, String path, Value draw, int intervalTicks, int order) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        ContentRegistry.addHud(new HudDefinition(id, draw, intervalTicks, order));
        return id.toString();
    }

    /** 已注册的方块（脚本可读，用于自检）。 */
    @HostAccess.Export
    public java.util.List<String> blocks() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (BlockDefinition b : ContentRegistry.blocks()) out.add(b.id().toString());
        return out;
    }

    /** 已注册的 HUD。 */
    @HostAccess.Export
    public java.util.List<String> huds() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (HudDefinition h : ContentRegistry.huds()) out.add(h.id().toString());
        return out;
    }
}
