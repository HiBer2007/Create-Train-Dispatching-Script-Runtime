package top.hibermod.ctdscript.content;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import top.hibermod.ctdscript.net.HudPrefPayload;
import top.hibermod.ctdscript.server.PlayerHudPrefs;

/**
 * 车载设备**装在车上之后**被右键时的处理。
 *
 * <h2>为什么需要单独一个类</h2>
 * 方块装到列车上就成了「动态结构里的一份快照」，
 * 世界里的 {@code useWithoutItem} 根本不会被调用——
 * Create 走的是 {@link MovingInteractionBehaviour} 这条路
 * （{@code AbstractContraptionEntity#handlePlayerInteraction:287} →
 * {@code contraption.interactors} 里的行为对象）。
 *
 * <p>不注册这个行为，右键车上设备就是**没反应**，而且不报错。
 *
 * <h2>改的是什么</h2>
 * 不改方块状态，改**这个玩家**的显示偏好。
 * 车是大家共用的，眼睛是各人自己的。
 */
public class OnboardDeviceInteraction extends MovingInteractionBehaviour {

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
                                           AbstractContraptionEntity contraptionEntity) {
        if (player.level().isClientSide) {
            return true;                     // 客户端先吃掉这次点击，等服务端回话
        }
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }

        boolean now = PlayerHudPrefs.get(sp.server).toggle(sp.getUUID());
        sp.connection.send(new HudPrefPayload(now));
        sp.displayClientMessage(
            Component.translatable(now
                ? "block.ctdscript.onboard_display.on"
                : "block.ctdscript.onboard_display.off"), true);
        return true;
    }
}
