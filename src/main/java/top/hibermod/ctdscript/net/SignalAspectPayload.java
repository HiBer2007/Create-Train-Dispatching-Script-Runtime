package top.hibermod.ctdscript.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 把「每架信号机现在显示哪个语义」推给客户端。
 *
 * <h2>为什么必须同步</h2>
 * 语义是内核算出来的，客户端算不出来——它既没有全线的列车位置，
 * 也不知道授权。没有这条同步，信号机上就只能显示 Create 自带的红/灭。
 *
 * <h2>为什么是整张表而不是增量</h2>
 * 信号机数量是几十的量级，整张表几十字节；增量要维护版本号与重传，
 * 复杂度远高于省下的带宽。
 */
public record SignalAspectPayload(List<Entry> entries) implements CustomPacketPayload {

    /** 一架信号机当前该显示什么。 */
    public record Entry(UUID signalId, String aspectId) {}

    public static final CustomPacketPayload.Type<SignalAspectPayload> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("ctdscript", "signal_aspects"));

    public static final StreamCodec<FriendlyByteBuf, SignalAspectPayload> STREAM_CODEC =
        CustomPacketPayload.codec(SignalAspectPayload::write, SignalAspectPayload::read);

    public SignalAspectPayload {
        if (entries == null) entries = List.of();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeUUID(e.signalId());
            buf.writeUtf(e.aspectId() == null ? "" : e.aspectId());
        }
    }

    private static SignalAspectPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(Math.min(n, 512));
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readUUID(), buf.readUtf()));
        }
        return new SignalAspectPayload(list);
    }

    @Override
    public CustomPacketPayload.Type<SignalAspectPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 整表替换：清掉那些已经被拆掉的信号机
            top.hibermod.ctdapi.status.SignalStates.clear();
            for (Entry e : entries) {
                top.hibermod.ctdapi.status.SignalStates.put(e.signalId(), e.aspectId());
            }
        });
    }
}
