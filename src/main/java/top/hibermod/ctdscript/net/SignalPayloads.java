package top.hibermod.ctdscript.net;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 信号制度 GUI 的两条消息。
 *
 * <h2>为什么要两个</h2>
 * 打开 GUI 需要知道「玩家指的是哪架信号机」，这个信息只有服务端能可靠地取到
 * （客户端也能 raycast，但它和服务端的判定可能差一格）。
 * 选完之后的写入也只有服务端能做。所以一进一出两条。
 */
public final class SignalPayloads {

    private SignalPayloads() {}

    /** 服务端 → 客户端：打开某架信号机的制度选择界面。 */
    public record Open(UUID signalId, String current) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Open> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath("ctdscript", "open_signal_gui"));

        public static final StreamCodec<FriendlyByteBuf, Open> STREAM_CODEC =
            CustomPacketPayload.codec(Open::write, Open::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeUUID(signalId);
            buf.writeUtf(current == null ? "" : current);
        }

        private static Open read(FriendlyByteBuf buf) {
            return new Open(buf.readUUID(), buf.readUtf());
        }

        @Override
        public CustomPacketPayload.Type<Open> type() {
            return TYPE;
        }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() ->
                top.hibermod.ctdscript.client.SignalSystemScreen.open(signalId, current));
        }
    }

    /** 客户端 → 服务端：把这架信号机设为某套制度（空串 = 不归任何制度）。 */
    public record Set(UUID signalId, String systemId) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Set> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath("ctdscript", "set_signal_system"));

        public static final StreamCodec<FriendlyByteBuf, Set> STREAM_CODEC =
            CustomPacketPayload.codec(Set::write, Set::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeUUID(signalId);
            buf.writeUtf(systemId == null ? "" : systemId);
        }

        private static Set read(FriendlyByteBuf buf) {
            return new Set(buf.readUUID(), buf.readUtf());
        }

        @Override
        public CustomPacketPayload.Type<Set> type() {
            return TYPE;
        }

        public void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server == null) return;

                ResourceLocation id = systemId.isEmpty() ? null
                    : ResourceLocation.tryParse(systemId);

                for (var graph : com.simibubi.create.Create.RAILWAYS.trackNetworks.values()) {
                    var point = graph.getPoint(
                        com.simibubi.create.content.trains.graph.EdgePointType.SIGNAL, signalId);
                    var holder = top.hibermod.ctdapi.signal.SignalSystemHolder.of(point);
                    if (holder != null) {
                        holder.ctd$setSignalSystem(id);
                        return;
                    }
                }
            });
        }
    }
}
