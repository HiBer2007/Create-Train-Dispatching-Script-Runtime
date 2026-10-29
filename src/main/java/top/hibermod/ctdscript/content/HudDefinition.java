package top.hibermod.ctdscript.content;

import org.graalvm.polyglot.Value;

import net.minecraft.resources.ResourceLocation;

/**
 * 脚本声明的一块 HUD。
 *
 * <h2>谁画</h2>
 * **引擎画**，不是脚本画。脚本每次被调用时只是往 {@link HudCanvas} 里「下单」：
 *
 * <pre>
 * def draw(h):
 *     h.text(8, 8, "T0 次 12:30", 0xFFFFFF)
 *     h.rect(8, 20, 120, 3, 0x34C759)
 * </pre>
 *
 * 这样做的原因：让 Python 直接调用 OpenGL 等于把渲染线程交给一个
 * 随时可能死循环的脚本。下单价便宜、可校验、可裁剪，画由引擎在自己的线程上做。
 *
 * <h2>频率</h2>
 * {@code draw} 不是每帧都调。引擎按 {@link #updateIntervalTicks()} 节流，
 * 中间帧复用上一次的图元列表——HUD 是给人看的，20 Hz 足够。
 *
 * @param id           面板 id
 * @param draw         回调，签名 {@code draw(canvas)}
 * @param intervalTicks 刷新间隔（tick），最小 1
 * @param order        绘制顺序，小的先画
 */
public record HudDefinition(
    ResourceLocation id,
    Value draw,
    int intervalTicks,
    int order
) {
    public HudDefinition {
        if (id == null) throw new IllegalArgumentException("hud id is required");
        if (draw == null || !draw.canExecute()) {
            throw new IllegalArgumentException("hud " + id + " needs a callable draw(canvas)");
        }
        intervalTicks = Math.max(1, intervalTicks);
    }
}
