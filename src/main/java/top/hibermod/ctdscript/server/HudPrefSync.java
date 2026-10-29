package top.hibermod.ctdscript.server;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import top.hibermod.ctdscript.CtdScript;
import top.hibermod.ctdscript.net.HudPrefPayload;

/**
 * 玩家进服时把**他自己**的显示偏好发给他。
 *
 * <p>不发的话，客户端默认是「关」，于是出现「我明明上次开过，今天还得再点一次」。
 * 偏好存在存档里，就该在进服时回到玩家手上。
 */
@EventBusSubscriber(modid = CtdScript.MOD_ID)
public final class HudPrefSync {

    private HudPrefSync() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;

        boolean enabled = PlayerHudPrefs.get(sp.server).isEnabled(sp.getUUID());
        sp.connection.send(new HudPrefPayload(enabled));
    }
}
