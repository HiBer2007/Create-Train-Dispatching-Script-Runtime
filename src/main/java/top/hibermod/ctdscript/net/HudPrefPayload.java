package top.hibermod.ctdscript.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 「这个玩家要不要看车载仪表」——**按人**同步到客户端。
 *
 * <h2>为什么必须按人</h2>
 * 设备决定「这列车有没有仪表能力」，这是**车的属性**；
 * 看不看是自己的事，这是**人的偏好**。
 * 混成一个开关就会出现「司机打开，全车乘客的屏幕上多出一块仪表」——
 * 乘客不想看、也不该看到司机那套读数。
 *
 * <p>所以：客户端的闸门查的是本地这份「我开没开」，
 * 而不是去问世界里的方块。
 */
public record HudPrefPayload(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HudPrefPayload> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("ctdscript", "hud_pref"));

    public static final StreamCodec<FriendlyByteBuf, HudPrefPayload> STREAM_CODEC =
        CustomPacketPayload.codec(HudPrefPayload::write, HudPrefPayload::new);

    @Override
    public CustomPacketPayload.Type<HudPrefPayload> type() {
        return TYPE;
    }

    private HudPrefPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
    }

    /** 客户端处理：只改本地标志，不碰世界。 */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            top.hibermod.ctdscript.client.ClientHudPrefs.set(enabled);
        });
    }
}
