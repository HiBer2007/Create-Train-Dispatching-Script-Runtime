package top.hibermod.ctdscript.content;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

import com.simibubi.create.content.trains.signal.SignalBlockEntity;

import top.hibermod.ctdapi.signal.SignalSystemHolder;
import top.hibermod.ctdscript.net.SignalPayloads;

/**
 * 信号制度配置器：右键信号机，选它服务于哪套信号制度。
 *
 * <h2>为什么做成物品而不是命令</h2>
 * 命令要求玩家先瞄准、再记 id；而「配置这架信号机」这个动作本身就指向一个方块。
 * 物品的 {@code useOn} 直接拿到被点的方块，没有射线判定、没有射程歧义，
 * 也不会出现「明明对着信号机却说没瞄准」。
 *
 * <p>同时它比命令更容易被发现：它在创造栏里。
 */
public class SignalConfiguratorItem extends Item {

    public SignalConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        var level = ctx.getLevel();
        var pos = ctx.getClickedPos();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;      // 客户端不做事，等服务端推界面
        }

        if (!(ctx.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        var be = level.getBlockEntity(pos);
        if (!(be instanceof SignalBlockEntity signal)) {
            player.displayClientMessage(
                Component.translatable("item.ctdscript.signal_configurator.not_a_signal"), true);
            return InteractionResult.CONSUME;
        }

        // 三种失败要分开说：它们的修法完全不同
        if (signal.edgePoint == null) {
            report(player, "no targeting behaviour", pos);
            player.displayClientMessage(
                Component.translatable("item.ctdscript.signal_configurator.unbound"), true);
            return InteractionResult.CONSUME;
        }

        Object boundary = signal.edgePoint.getEdgePoint();
        if (boundary == null) {
            report(player, "edgePoint is null (signal not on a track)", pos);
            player.displayClientMessage(
                Component.translatable("item.ctdscript.signal_configurator.unbound"), true);
            return InteractionResult.CONSUME;
        }

        SignalSystemHolder holder = SignalSystemHolder.of(boundary);
        if (holder == null) {
            // 走到这里说明 mixin 没生效：边点对象没实现接口
            report(player, "boundary is " + boundary.getClass().getName()
                + " which does NOT implement SignalSystemHolder (mixin missing)", pos);
            player.displayClientMessage(
                Component.translatable("item.ctdscript.signal_configurator.unbound"), true);
            return InteractionResult.CONSUME;
        }

        player.connection.send(new SignalPayloads.Open(
            ((com.simibubi.create.content.trains.signal.SignalBoundary) boundary).id,
            SignalSystemHolder.systemOf(holder)));
        return InteractionResult.CONSUME;
    }

    /** 把失败原因同时写进日志——玩家看不到聊天栏那一行的时候还能查日志。 */
    private static void report(ServerPlayer player, String why, net.minecraft.core.BlockPos pos) {
        System.out.println("[ctdscript] signal configurator at " + pos.toShortString()
            + " failed: " + why);
        player.displayClientMessage(Component.literal("CTD: " + why), false);
    }
}
