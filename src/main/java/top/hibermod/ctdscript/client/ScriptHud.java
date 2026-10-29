package top.hibermod.ctdscript.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.graalvm.polyglot.Value;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import top.hibermod.ctdscript.CtdScript;
import top.hibermod.ctdscript.content.ContentRegistry;
import top.hibermod.ctdscript.content.HudCanvas;
import top.hibermod.ctdscript.content.HudDefinition;

/**
 * 脚本 HUD 的渲染端。
 *
 * <h2>分工</h2>
 * 脚本只负责「下单」（往 {@link HudCanvas} 里放图元），**画由引擎做**。
 * 这样渲染线程上永远只有引擎自己的代码，不会因为某个脚本死循环而卡住画面。
 *
 * <h2>节流</h2>
 * 每个面板按自己的 {@code intervalTicks} 重新求值，中间帧复用上一次的图元。
 * Python 端每帧调用一次的代价这里不接受——HUD 是给人看的，20 Hz 足够。
 *
 * <h2>为什么在与游戏事件总线订阅</h2>
 * 类上有 {@code @EventBusSubscriber(value = Dist.CLIENT)}，
 * 服务端根本不会加载这个类，所以不需要在公共代码里判断环境。
 */
@EventBusSubscriber(modid = CtdScript.MOD_ID, value = Dist.CLIENT)
public final class ScriptHud {

    /** 一个面板的缓存图元与上次求值时间。 */
    private static final class PanelState {
        final HudDefinition definition;
        final HudCanvas canvas = new HudCanvas();
        long lastUpdate = Long.MIN_VALUE;
        int failures;

        PanelState(HudDefinition definition) {
            this.definition = definition;
        }
    }

    private static final List<PanelState> PANELS = new ArrayList<>();
    private static boolean initialised;
    private static boolean disabled;

    private ScriptHud() {}

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        if (disabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;

        ensureInitialised();

        GuiGraphics gg = event.getGuiGraphics();
        long gameTime = mc.level.getGameTime();

        for (PanelState panel : PANELS) {
            long interval = panel.definition.intervalTicks();
            if (gameTime - panel.lastUpdate >= interval || panel.lastUpdate == Long.MIN_VALUE) {
                refresh(panel);
                panel.lastUpdate = gameTime;
            }
            draw(gg, mc, panel);
        }
    }

    private static void ensureInitialised() {
        if (initialised) return;
        initialised = true;
        for (HudDefinition def : ContentRegistry.huds()) {
            PANELS.add(new PanelState(def));
        }
        PANELS.sort(Comparator.comparingInt(p -> p.definition.order()));
        if (!PANELS.isEmpty()) {
            System.out.println("[ctdscript] hud panels: " + PANELS.size());
        }
    }

    private static void refresh(PanelState panel) {
        if (panel.failures >= 5) return;              // 连续失败就不再来回炸
        panel.canvas.clear();
        try {
            Value draw = panel.definition.draw();
            draw.execute(panel.canvas);
            panel.failures = 0;
        } catch (Throwable t) {
            panel.failures++;
            System.err.println("[ctdscript] hud " + panel.definition.id()
                + " failed (" + panel.failures + "): " + t);
        }
    }

    private static void draw(GuiGraphics gg, Minecraft mc, PanelState panel) {
        for (HudCanvas.Primitive p : panel.canvas.primitives()) {
            if (p instanceof HudCanvas.Text text) {
                float scale = text.scale();
                if (scale == 1f) {
                    gg.drawString(mc.font, text.text(), (int) text.x(), (int) text.y(),
                        text.color(), false);
                } else {
                    gg.pose().pushPose();
                    gg.pose().translate(text.x(), text.y(), 0);
                    gg.pose().scale(scale, scale, 1f);
                    gg.drawString(mc.font, text.text(), 0, 0, text.color(), false);
                    gg.pose().popPose();
                }
            } else if (p instanceof HudCanvas.Rect rect) {
                gg.fill((int) rect.x(), (int) rect.y(),
                    (int) (rect.x() + rect.width()), (int) (rect.y() + rect.height()),
                    rect.color());
            } else if (p instanceof HudCanvas.Image image) {
                // 纹理由脚本提供，启动时已注册成动态纹理（见 HudTextures）
                gg.blit(image.texture(), (int) image.x(), (int) image.y(),
                    0, 0, (int) image.width(), (int) image.height(),
                    (int) image.width(), (int) image.height());
            }
        }
    }

    /** 引擎自检用：当前有几个面板、各自失败几次。 */
    public static String status() {
        StringBuilder sb = new StringBuilder("hudPanels=").append(PANELS.size());
        for (PanelState p : PANELS) {
            sb.append(' ').append(p.definition.id().getPath())
              .append("(").append(p.canvas.count()).append(" prims)")
              .append(p.failures > 0 ? "!" + p.failures : "");
        }
        return sb.toString();
    }
}
