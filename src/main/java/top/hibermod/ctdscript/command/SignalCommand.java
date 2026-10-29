package top.hibermod.ctdscript.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import top.hibermod.ctdapi.signal.SignalSystemHolder;

/**
 * {@code /ctd signal system <制度id>} —— 指定准星那架信号机服务于哪套信号制度。
 *
 * <h2>为什么一架只能选一套</h2>
 * 一架信号机同时显示两套制度的状态，司机无法判断该信谁。
 * 现实中线路也是按制式分区设信号的。
 *
 * <h2>数据存在哪</h2>
 * 存在信号机自己身上（{@link SignalSystemHolder}，由宿主的 mixin 提供）。
 * 这样它跟着拆装、存档、同步一起走；外挂表在信号机被拆掉之后只会留下脏数据，
 * 而客户端永远读不到——「选了又变回去」就是这么来的。
 */
public final class SignalCommand {

    private SignalCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("signal")
            .then(Commands.literal("system")
                .then(Commands.argument("id", StringArgumentType.greedyString())
                    .executes(SignalCommand::assign)))
            .then(Commands.literal("show").executes(SignalCommand::show))
            .then(Commands.literal("gui").executes(SignalCommand::gui));
    }

    private static int assign(CommandContext<CommandSourceStack> ctx) {
        Player player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("must be run by a player (needs aiming)"));
            return 0;
        }

        String raw = StringArgumentType.getString(ctx, "id").trim();
        ResourceLocation systemId = raw.equalsIgnoreCase("none") ? null
            : ResourceLocation.tryParse(raw);
        if (systemId == null && !raw.equalsIgnoreCase("none")) {
            ctx.getSource().sendFailure(Component.literal("bad system id: " + raw));
            return 0;
        }

        SignalSystemHolder holder = aimed(player);
        if (holder == null) {
            ctx.getSource().sendFailure(Component.literal("aim at a signal (within 8 blocks)"));
            return 0;
        }

        holder.ctd$setSignalSystem(systemId);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "signal system -> " + (systemId == null ? "none" : systemId.toString())), false);
        return 1;
    }

    /** 打开选择界面：玩家不用记制度 id。 */
    private static int gui(CommandContext<CommandSourceStack> ctx) {
        var player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("must be run by a player"));
            return 0;
        }

        var holder = aimed(player);
        if (holder == null) {
            ctx.getSource().sendFailure(Component.literal("aim at a signal (within 8 blocks)"));
            return 0;
        }

        // 取 id 需要边点对象本身；从图里反查太绕，直接用反射读它的 id 字段
        java.util.UUID signalId = idOf(holder);
        if (signalId == null) {
            ctx.getSource().sendFailure(Component.literal("cannot read signal id"));
            return 0;
        }

        String current = SignalSystemHolder.systemOf(holder);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.connection.send(new top.hibermod.ctdscript.net.SignalPayloads.Open(
                signalId, current));
        }
        return 1;
    }

    private static java.util.UUID idOf(SignalSystemHolder holder) {
        try {
            Object id = holder.getClass().getField("id").get(holder);
            return id instanceof java.util.UUID uuid ? uuid : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        Player player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("must be run by a player"));
            return 0;
        }

        SignalSystemHolder holder = aimed(player);
        if (holder == null) {
            ctx.getSource().sendFailure(Component.literal("aim at a signal"));
            return 0;
        }

        String id = SignalSystemHolder.systemOf(holder);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "this signal serves: " + (id.isEmpty() ? "none" : id)), false);
        return 1;
    }

    /**
     * 准星那架信号机的「所属制度」句柄。
     *
     * <p>{@code SignalBlockEntity.edgePoint}（{@code TrackTargetingBehaviour}）
     * 上的 {@code getEdgePoint()} 才给出真正的边点对象——也就是我们要写属性的那个。
     */
    private static SignalSystemHolder aimed(Player player) {
        var hit = player.pick(8.0, 0, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        var be = player.level().getBlockEntity(bhr.getBlockPos());
        if (be == null) return null;

        try {
            Object targeting = be.getClass().getField("edgePoint").get(be);
            Object boundary = targeting.getClass().getMethod("getEdgePoint").invoke(targeting);
            return SignalSystemHolder.of(boundary);
        } catch (Throwable t) {
            return null;
        }
    }
}
