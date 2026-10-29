package top.hibermod.ctdscript.net;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import top.hibermod.ctdscript.CtdScript;

/**
 * 网络注册。目前只有一条：告诉客户端「你的仪表开关是开还是关」。
 *
 * <p>版本号写成 {@code 1}：以后加字段必须升版本，
 * 否则新旧客户端之间会静默解错。宁可连不上，也不要读错。
 */
@EventBusSubscriber(modid = CtdScript.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CtdNetwork {

    private CtdNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(HudPrefPayload.TYPE, HudPrefPayload.STREAM_CODEC,
            HudPrefPayload::handle);
    }
}
