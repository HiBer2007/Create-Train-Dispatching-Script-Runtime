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
 * <h2>为什么是「下单」而不是「自己画」</h2>
 * 让脚本直接碰 {@code GuiGraphics} 等于把渲染线程交给一段随时可能死循环的 Python。
 * 下单便宜、可校验、可裁剪；画由引擎在自己的线程上做。
 *
 * <h2>制式无关</h2>
 * 这里没有任何「速度表」「电压表」的概念，只有图元：
 * 贴图（含子区域）、旋转贴图、矩形、文字、进度条。
 * 指针式仪表 = 旋转贴图 + 文字；数字仪表 = 贴图 + 文字。长什么样由脚本决定。
 */
public final class HudCanvas {

    /** 一条绘制指令。 */
    public sealed interface Primitive permits Text, Rect, Image, ProgressBar {}

    public record Text(float x, float y, String text, int color, float scale) implements Primitive {}

    public record Rect(float x, float y, float width, float height, int color) implements Primitive {}

    /**
     * 贴图。{@code u/v/uw/uh} 是**纹理像素**；{@code uw/uh <= 0} 表示整张图。
     * 旋转围绕 (x + anchorX, y + anchorY) 进行，随后在该点绘制。
     */
    public record Image(net.minecraft.resources.ResourceLocation texture,
                        float x, float y, float width, float height,
                        float u, float v, float uw, float uh,
                        float angle, float anchorX, float anchorY) implements Primitive {}

    /** 进度条：背景 + 前景，{@code fraction} 取 0–1。 */
    public record ProgressBar(float x, float y, float width, float height,
                              float fraction, int fg, int bg) implements Primitive {}

    private final List<Primitive> primitives = new ArrayList<>();

    public List<Primitive> primitives() {
        return primitives;
    }

    public void clear() {
        primitives.clear();
    }

    // ── 文字 ──────────────────────────────────────────────────────────────

    /** 颜色用 long：GraalPy 会把 {@code 0xAA000000} 当 Long 传，用 int 会拒绝。 */
    @HostAccess.Export
    public void text(double x, double y, String text, long color) {
        if (text == null || text.isEmpty()) return;
        primitives.add(new Text((float) x, (float) y, text, (int) color, 1f));
    }

    @HostAccess.Export
    public void text(double x, double y, String text, long color, double scale) {
        if (text == null || text.isEmpty()) return;
        primitives.add(new Text((float) x, (float) y, text, (int) color,
            (float) Math.max(0.25, scale)));
    }

    // ── 矩形 ──────────────────────────────────────────────────────────────

    @HostAccess.Export
    public void rect(double x, double y, double width, double height, long color) {
        if (width <= 0 || height <= 0) return;
        primitives.add(new Rect((float) x, (float) y, (float) width, (float) height, (int) color));
    }

    // ── 贴图 ──────────────────────────────────────────────────────────────

    /** 整张图。{@code textureId} 形如 {@code mysys:gui/badge}。 */
    @HostAccess.Export
    public void image(String textureId, double x, double y, double width, double height) {
        image(textureId, x, y, width, height, 0, 0, 0, 0, 0, 0, 0);
    }

    /** 子区域（图集切片）。{@code uw/uh} 传 0 表示整张。 */
    @HostAccess.Export
    public void image(String textureId, double x, double y, double width, double height,
                      double u, double v, double uw, double uh) {
        image(textureId, x, y, width, height, u, v, uw, uh, 0, 0, 0);
    }

    /**
     * 可旋转的贴图——**指针式仪表的表针就是它**。
     *
     * @param angle   角度（度，顺时针为正）
     * @param anchorX 旋转中心相对绘制点的 x 偏移
     * @param anchorY 同上，y 偏移
     */
    @HostAccess.Export
    public void image(String textureId, double x, double y, double width, double height,
                      double u, double v, double uw, double uh, double angle,
                      double anchorX, double anchorY) {
        net.minecraft.resources.ResourceLocation id =
            net.minecraft.resources.ResourceLocation.tryParse(textureId == null ? "" : textureId);
        if (id == null || width <= 0 || height <= 0) return;
        primitives.add(new Image(id, (float) x, (float) y, (float) width, (float) height,
            (float) u, (float) v, (float) uw, (float) uh,
            (float) angle, (float) anchorX, (float) anchorY));
    }

    /** 只带角度的便捷写法：锚点取图心。 */
    @HostAccess.Export
    public void rotated(String textureId, double x, double y, double width, double height,
                        double angle) {
        image(textureId, x, y, width, height, 0, 0, 0, 0, angle, width / 2, height / 2);
    }

    // ── 进度条 ────────────────────────────────────────────────────────────

    @HostAccess.Export
    public void progress(double x, double y, double width, double height,
                         double fraction, long fg, long bg) {
        if (width <= 0 || height <= 0) return;
        primitives.add(new ProgressBar((float) x, (float) y, (float) width, (float) height,
            (float) Math.max(0, Math.min(1, fraction)), (int) fg, (int) bg));
    }

    /** 脚本可读：这一帧下了多少单（排查「写了但没画」时有用）。 */
    @HostAccess.Export
    public int count() {
        return primitives.size();
    }
}
