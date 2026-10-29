package top.hibermod.ctdscript.content;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.HostAccess;

/**
 * 脚本往 HUD 上「下单」用的画布。
 *
 * <p>所有方法都是**收集**，不做任何绘制：真正的绘制发生在渲染线程上，
 * 由引擎读取 {@link #primitives()} 完成。坐标系是屏幕像素，左上角原点。
 *
 * <p>每次刷新前 {@link #clear()}，所以脚本不需要自己清理。
 */
public final class HudCanvas {

    /** 一条绘制指令。 */
    public sealed interface Primitive permits Text, Rect, Image {}

    public record Text(float x, float y, String text, int color, float scale) implements Primitive {}

    public record Rect(float x, float y, float width, float height, int color) implements Primitive {}

    /** 贴一张脚本提供的纹理（{@code textures/gui/...}）；{@code textureId} 不含扩展名。 */
    public record Image(net.minecraft.resources.ResourceLocation texture,
                        float x, float y, float width, float height) implements Primitive {}

    private final List<Primitive> primitives = new ArrayList<>();

    public List<Primitive> primitives() {
        return primitives;
    }

    public void clear() {
        primitives.clear();
    }

    @HostAccess.Export
    public void text(double x, double y, String text, int color) {
        if (text == null || text.isEmpty()) return;
        primitives.add(new Text((float) x, (float) y, text, color, 1f));
    }

    @HostAccess.Export
    public void text(double x, double y, String text, int color, double scale) {
        if (text == null || text.isEmpty()) return;
        primitives.add(new Text((float) x, (float) y, text, color, (float) Math.max(0.25, scale)));
    }

    @HostAccess.Export
    public void rect(double x, double y, double width, double height, int color) {
        if (width <= 0 || height <= 0) return;
        primitives.add(new Rect((float) x, (float) y, (float) width, (float) height, color));
    }

    /**
     * 贴图。
     *
     * @param textureId 形如 {@code mysys:gui/badge}，对应
     *                  {@code config/ctd/assets/mysys/textures/gui/badge.png}
     */
    @HostAccess.Export
    public void image(String textureId, double x, double y, double width, double height) {
        net.minecraft.resources.ResourceLocation id =
            net.minecraft.resources.ResourceLocation.tryParse(textureId == null ? "" : textureId);
        if (id == null || width <= 0 || height <= 0) return;
        primitives.add(new Image(id, (float) x, (float) y, (float) width, (float) height));
    }

    /** 脚本可读：这一帧下了多少单（排查「写了但没画」时有用）。 */
    @HostAccess.Export
    public int count() {
        return primitives.size();
    }
}
