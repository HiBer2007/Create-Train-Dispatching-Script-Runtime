package top.hibermod.ctdscript.server;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

import top.hibermod.ctdscript.CtdScript;
import top.hibermod.ctdscript.net.HudPrefPayload;

/**
 * 下车即收回「我已启用显示」。
 *
 * <h2>为什么必须自动收回</h2>
 * 指示器是「我在这车上要看仪表」，人已经不在车上了，
 * 这个意图就失效了。如果留着，会出现两种错：
 * <ol>
 *   <li>下次随便上另一列车，仪表**自己冒出来**——玩家没同意过；</li>
 *   <li>开关状态随时间越攒越多，最后没人说得清「谁开过」。</li>
 * </ol>
 *
 * <h2>为什么用 {@code EntityMountEvent}</h2>
 * 它是唯一同时覆盖「玩家主动下」和「被请下车」的事件；
 * 监听 {@code Player#stopRiding} 之类的钩子会漏掉服务端强制下车的情况。
 */
@EventBusSubscriber(modid = CtdScript.MOD_ID)
public final class DismountCleanup {

    private DismountCleanup() {}

    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (!event.isDismounting()) return;
        if (event.getLevel().isClientSide) return;

        if (!(event.getEntityBeingMounted() instanceof CarriageContraptionEntity)) return;
        if (!(event.getEntityMounting() instanceof net.minecraft.server.level.ServerPlayer sp)) return;

        boolean wasEnabled = PlayerHudPrefs.get(sp.server).isEnabled(sp.getUUID());
        if (!wasEnabled) return;

        PlayerHudPrefs.get(sp.server).set(sp.getUUID(), false);
        sp.connection.send(new HudPrefPayload(false));      // 客户端立刻收起
    }
}
