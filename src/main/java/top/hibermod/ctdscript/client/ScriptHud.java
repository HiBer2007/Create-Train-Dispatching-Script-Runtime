package top.hibermod.ctdscript.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.graalvm.polyglot.Value;

import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import top.hibermod.ctdscript.CtdScript;
import top.hibermod.ctdscript.content.ContentRegistry;
import top.hibermod.ctdscript.content.HudCanvas;
import top.hibermod.ctdscript.content.HudDefinition;
import top.hibermod.ctdscript.content.HudState;

/**
 * 脚本 HUD 的渲染端。
 *
 * <h2>分工</h2>
 * 脚本只负责「下单」（往 {@link HudCanvas} 里放图元），**画由引擎做**。
 * 渲染线程上永远只有引擎自己的代码。
 *
 * <h2>节流</h2>
 * 每个面板按自己的 {@code intervalTicks} 重新求值，中间帧复用上一次的图元。
 *
 * <h2>显示闸门</h2>
 * 只有「玩家坐在列车上」时才求值并渲染。更严格的条件（车上装了车载设备、
 * 且右键启用过指示器）在阶段 K2 完成后接入，见 PLAN §I3。
 */
@EventBusSubscriber(modid = CtdScript.MOD_ID, value = Dist.CLIENT)
public final class ScriptHud {

    private static final class PanelState {
        final HudDefinition definition;
        final HudCanvas canvas = new HudCanvas();
        long lastUpdate = Long.MIN_VALUE;
        int failures;
        int primitivesLastFrame;

        PanelState(HudDefinition definition) {
            this.definition = definition;
        }
    }

    private static final List<PanelState> PANELS = new ArrayList<>();
    private static boolean initialised;

    private ScriptHud() {}

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        ensureInitialised();
        if (PANELS.isEmpty()) return;

        HudState state = RidingContext.state(mc);
        if (state == null) return;          // 没坐在车上：不显示、也不求值

        GuiGraphics gg = event.getGuiGraphics();
        long gameTime = mc.level.getGameTime();

        for (PanelState panel : PANELS) {
            long interval = panel.definition.intervalTicks();
            if (panel.lastUpdate == Long.MIN_VALUE || gameTime - panel.lastUpdate >= interval) {
                refresh(panel, state);
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
    }

    private static void refresh(PanelState panel, HudState state) {
        if (panel.failures >= 5) return;
        panel.canvas.clear();
        try {
            Value draw = panel.definition.draw();
            try {
                // 两参签名优先：draw(canvas, state)
                draw.execute(panel.canvas, state);
            } catch (IllegalArgumentException wrongArity) {
                // 只写一个参数的老脚本仍然能用
                draw.execute(panel.canvas);
            }
            panel.failures = 0;
            panel.primitivesLastFrame = panel.canvas.count();
        } catch (Throwable t) {
            panel.failures++;
            System.err.println("[ctdscript] hud " + panel.definition.id()
                + " failed (" + panel.failures + "): " + t);
        }
    }

    private static void draw(GuiGraphics gg, Minecraft mc, PanelState panel) {
        for (HudCanvas.Primitive p : panel.canvas.primitives()) {
            if (p instanceof HudCanvas.Text text) {
                drawText(gg, mc, text);
            } else if (p instanceof HudCanvas.Rect rect) {
                gg.fill((int) rect.x(), (int) rect.y(),
                    (int) (rect.x() + rect.width()), (int) (rect.y() + rect.height()),
                    rect.color());
            } else if (p instanceof HudCanvas.ProgressBar bar) {
                gg.fill((int) bar.x(), (int) bar.y(),
                    (int) (bar.x() + bar.width()), (int) (bar.y() + bar.height()), bar.bg());
                gg.fill((int) bar.x(), (int) bar.y(),
                    (int) (bar.x() + bar.width() * bar.fraction()),
                    (int) (bar.y() + bar.height()), bar.fg());
            } else if (p instanceof HudCanvas.Image image) {
                drawImage(gg, image);
            }
        }
    }

    private static void drawText(GuiGraphics gg, Minecraft mc, HudCanvas.Text text) {
        float scale = text.scale();
        if (scale == 1f) {
            gg.drawString(mc.font, text.text(), (int) text.x(), (int) text.y(), text.color(), false);
        } else {
            gg.pose().pushPose();
            gg.pose().translate(text.x(), text.y(), 0);
            gg.pose().scale(scale, scale, 1f);
            gg.drawString(mc.font, text.text(), 0, 0, text.color(), false);
            gg.pose().popPose();
        }
    }

    private static void drawImage(GuiGraphics gg, HudCanvas.Image image) {
        ResourceLocation tex = GuiTextures.resolve(image.texture());
        if (tex == null) return;

        int u = (int) image.u();
        int v = (int) image.v();
        int w = (int) image.width();
        int h = (int) image.height();
        int uw = image.uw() > 0 ? (int) image.uw() : w;
        int uh = image.uh() > 0 ? (int) image.uh() : h;

        if (image.angle() == 0) {
            gg.blit(tex, (int) image.x(), (int) image.y(), u, v, w, h, uw, uh);
            return;
        }

        gg.pose().pushPose();
        gg.pose().translate(image.x() + image.anchorX(), image.y() + image.anchorY(), 0);
        gg.pose().mulPose(Axis.ZP.rotationDegrees(image.angle()));
        gg.blit(tex, (int) -image.anchorX(), (int) -image.anchorY(), u, v, w, h, uw, uh);
        gg.pose().popPose();
    }

    /** 引擎自检用。 */
    public static String status() {
        StringBuilder sb = new StringBuilder("hudPanels=").append(PANELS.size());
        for (PanelState p : PANELS) {
            sb.append(' ').append(p.definition.id().getPath())
              .append("(").append(p.primitivesLastFrame).append(" prims)");
            if (p.failures > 0) sb.append('!').append(p.failures);
        }
        return sb.toString();
    }

    /** 「玩家是否坐在车上」的判断单独成类，方便以后接车载设备闸门。 */
    static final class RidingContext {

        private RidingContext() {}

        /** 不在车上返回 {@code null}。 */
        static HudState state(Minecraft mc) {
            if (mc.player == null) return null;

            net.minecraft.world.entity.Entity vehicle = mc.player.getVehicle();
            if (!(vehicle instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity cce)) {
                return null;
            }
            var carriage = cce.getCarriage();
            if (carriage == null || carriage.train == null) return null;

            var train = carriage.train;
            String name = train.name == null ? "" : train.name.getString();
            return new HudState(train.speed, train.targetSpeed, 0, true, name,
                "", "", 0, mc.level == null ? 0 : mc.level.getGameTime());
        }
    }
}
