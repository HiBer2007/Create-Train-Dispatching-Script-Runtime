package top.hibermod.ctdscript.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import top.hibermod.ctdapi.signal.SignalRegistries;
import top.hibermod.ctdscript.net.SignalPayloads;

/**
 * 选「这架信号机服务于哪套信号制度」的界面。
 *
 * <h2>为什么做成列表而不是下拉框</h2>
 * 制度通常只有几套，全部平铺出来比点两下展开更省事；
 * 而且玩家能看到「一共有哪些制度可选」，这在调试时很重要。
 *
 * <h2>数据从哪来</h2>
 * 直接读 {@link SignalRegistries#systems()}——客户端和服务端跑的是同一批启动脚本，
 * 所以两边看到的列表一致，不需要再同步一份。
 */
public class SignalSystemScreen extends Screen {

    private final UUID signalId;
    private final String current;
    private final List<String> systems = new ArrayList<>();

    private static final int BUTTON_WIDTH = 160;
    private static final int BUTTON_HEIGHT = 20;

    private SignalSystemScreen(UUID signalId, String current) {
        super(Component.literal("信号制度"));
        this.signalId = signalId;
        this.current = current == null ? "" : current;

        for (var system : SignalRegistries.systems()) {
            systems.add(system.id().toString());
        }
        systems.sort(String::compareTo);
    }

    /** 由网络包调用：服务端已经确认了信号机 id。 */
    public static void open(UUID signalId, String current) {
        Minecraft.getInstance().setScreen(new SignalSystemScreen(signalId, current));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - (systems.size() * (BUTTON_HEIGHT + 4)) / 2;

        for (String id : systems) {
            String label = id + (id.equals(current) ? "  ✓" : "");
            addRenderableWidget(Button.builder(Component.literal(label), b -> pick(id))
                .bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
            y += BUTTON_HEIGHT + 4;
        }

        addRenderableWidget(Button.builder(Component.literal("不归任何制度"), b -> pick(""))
            .bounds(centerX - BUTTON_WIDTH / 2, y + 8, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build());
    }

    private void pick(String systemId) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(new SignalPayloads.Set(signalId, systemId));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, "选择这架信号机服务的制度",
            this.width / 2, this.height / 2 - 90, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "signal " + signalId,
            this.width / 2, this.height / 2 - 76, 0x888888);
    }

    @Override
    public boolean isPauseScreen() {
        return false;                       // 别让单人游戏暂停：这是运维界面
    }
}
