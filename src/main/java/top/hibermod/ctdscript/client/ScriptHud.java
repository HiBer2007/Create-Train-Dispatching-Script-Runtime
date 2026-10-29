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
    private static long refreshCount;

    private ScriptHud() {}

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        ensureInitialised();
        if (PANELS.isEmpty()) return;

        HudState state = RidingContext.state(mc);
        if (state == null) return;

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
            if (refreshCount++ % 100 == 0) {
                System.out.println(String.format(
                    "[ctdscript] hud tick speed=%.4f signed=%.1fkm/h sign=%d riding=%s",
                    state.speed_kmh() / 72.0, state.speed_kmh(),
                    state.direction_sign(), state.riding()));
            }
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
                return reject("not riding a carriage (" + (vehicle == null ? "no vehicle"
                    : vehicle.getClass().getSimpleName()) + ")");
            }
            var carriage = cce.getCarriage();
            if (carriage == null || carriage.train == null) return reject("carriage/train missing");

            // 第三层闸门（车）：这列车必须装了车载显示设备。
            if (!hasDisplayDevice(cce)) return reject("no onboard device on this contraption");

            // 第四层闸门（人）：**我自己**有没有打开显示。
            // 开关按人不按车——司机打开不该让全车乘客都看到读数。
            if (!top.hibermod.ctdscript.client.ClientHudPrefs.enabled()) return reject("indicator off for this player");

            var train = carriage.train;
            String name = train.name == null ? "" : train.name.getString();

            // 优先用宿主推来的**真实**车速：Create 只在有人操控时同步，
            // 松手/自动驾驶后客户端那份 train.speed 是冻住的。
            var feed = TrainFeedCache.get(train.id);
            double speed = feed != null ? feed.speed() : train.speed;
            double target = feed != null ? feed.targetSpeed() : train.targetSpeed;

            return new HudState(speed, target, 0, true, name,
                "", "", 0, mc.level == null ? 0 : mc.level.getGameTime(),
                cabSign(mc.player, cce));
        }

        /**
         * 构造物里是否装了车载显示设备（只问「有没有」，不问「开没开」）。
         *
         * <p>方块被组装到车上时，Create 把方块实体的 NBT 原样存进
         * {@code StructureBlockInfo.nbt()}，所以这里查的是那份快照，
         * 不需要方块实体本身（客户端也不一定有）。
         */
            /**
         * 相对**玩家所在驾驶台**的符号。
         *
         * <h2>用什么定驾驶台</h2>
         * 取**玩家所在构造物里离他最近的那个驾驶台方块**，而不是
         * {@code ControlsHandler.getControlsPos()}。后者只在「正在操控」时才有值：
         * 一松开手它就变 null，符号会突然翻号（实测 +19 跳 -19）。
         * 而玩家明明还坐在那个驾驶台上，方向不该因为松手而改变。
         *
         * <h2>怎么定正负</h2>
         * 照抄 Create 的判据（{@code CarriageContraptionEntity#control:562-568}）：
         * {@code inverted = !controlsBlock.FACING.equals(getInitialOrientation().getCounterClockWise())}。
         * 用同一套规则，玩家的直觉才和原版一致。
         *
         * <p>找不到驾驶台（坐在没驾驶台的车厢里）就返回 +1：乘客看到的只是车速本身。
         */
        static int cabSign(
                net.minecraft.world.entity.player.Player player,
                com.simibubi.create.content.trains.entity.CarriageContraptionEntity cce) {
            // 正在操控：记住这个驾驶台
            net.minecraft.core.BlockPos current =
                com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler
                    .getControlsPos();
            if (current != null) {
                LAST_CAB.put(cce.getId(), current);
            }

            net.minecraft.core.BlockPos cab = LAST_CAB.get(cce.getId());
            if (cab == null) {
                // 从没碰过驾驶台：按车体自己的方向（+1）
                return 1;
            }

            try {
                var contraption = cce.getContraption();
                if (contraption == null) return 1;

                var info = contraption.getBlocks().get(cab);
                if (info == null || !info.state().hasProperty(
                        com.simibubi.create.content.contraptions.actors.trainControls
                            .ControlsBlock.FACING)) {
                    return 1;
                }

                net.minecraft.core.Direction facing = info.state().getValue(
                    com.simibubi.create.content.contraptions.actors.trainControls
                        .ControlsBlock.FACING);
                net.minecraft.core.Direction initial =
                    cce.getInitialOrientation().getCounterClockWise();

                return facing.equals(initial) ? 1 : -1;
            } catch (Throwable t) {
                return 1;
            }
        }

        /** 每个构造物最后用过的驾驶台（局部坐标）。 */
        private static final java.util.Map<Integer, net.minecraft.core.BlockPos> LAST_CAB =
            new java.util.HashMap<>();
        /** 拿不到状态时记一次原因；每 100 次打一条，避免刷屏。 */
        private static int nullCount;

        private static HudState reject(String reason) {
            if (++nullCount % 100 == 1) {
                System.out.println("[ctdscript] hud skipped (" + nullCount + "): " + reason);
            }
            return null;
        }

        static boolean hasDisplayDevice(
                com.simibubi.create.content.trains.entity.CarriageContraptionEntity cce) {
            var contraption = cce.getContraption();
            if (contraption == null) return false;

            net.minecraft.world.level.block.Block device =
                top.hibermod.ctdscript.content.EngineBlocks.onboardBlock();
            if (device == null) return false;

            for (var info : contraption.getBlocks().values()) {
                if (info.state().is(device)) return true;
            }
            return false;
        }
    }
}
