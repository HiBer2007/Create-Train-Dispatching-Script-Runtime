package top.hibermod.ctdscript.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import top.hibermod.ctdscript.server.SignalAssignments;

/**
 * {@code /ctd signal system <制度id>} —— 指定准星那架信号机服务于哪套信号制度。
 *
 * <h2>为什么一架只能选一套</h2>
 * 一架信号机同时显示两套制度的状态，司机无法判断该信谁。
 * 现实中线路也是按制式分区设信号的。
 *
 * <p>不指定（{@code none}）表示「不归任何制度管」，内核不会去点亮它。
 */
public final class SignalCommand {

    private SignalCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("signal")
            .then(Commands.literal("system")
                .then(Commands.argument("id", StringArgumentType.greedyString())
                    .executes(SignalCommand::assign)))
            .then(Commands.literal("list").executes(ctx -> {
                var data = SignalAssignments.get(ctx.getSource().getServer());
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "signals with a system: " + data.size()), false);
                return data.size();
            }));
    }

    private static int assign(CommandContext<CommandSourceStack> ctx) {
        Player player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("must be run by a player (needs aiming)"));
            return 0;
        }

        String systemId = StringArgumentType.getString(ctx, "id").trim();
        if (systemId.equalsIgnoreCase("none")) systemId = "";

        var hit = player.pick(8.0, 0, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            ctx.getSource().sendFailure(Component.literal("aim at a signal (within 8 blocks)"));
            return 0;
        }

        var be = player.level().getBlockEntity(bhr.getBlockPos());
        java.util.UUID signalId = signalIdOf(be);
        if (signalId == null) {
            ctx.getSource().sendFailure(Component.literal("that block is not a signal"));
            return 0;
        }

        SignalAssignments.get(ctx.getSource().getServer()).assign(signalId, systemId);

        String shown = systemId.isEmpty() ? "none" : systemId;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "signal " + signalId + " -> " + shown), false);
        return 1;
    }

    /**
     * 从信号方块实体取边点 id。
     *
     * <p>{@code SignalBlockEntity.edgePoint} 是 {@code TrackTargetingBehaviour}，
     * 取其中的边界对象再读 {@code id}。用反射是因为这一层会随 Create 版本变，
     * 而我们要的东西只有「一个稳定 id」。
     */
    private static java.util.UUID signalIdOf(net.minecraft.world.level.block.entity.BlockEntity be) {
        if (be == null) return null;
        try {
            Object targeting = be.getClass().getField("edgePoint").get(be);
            Object boundary = targeting.getClass().getMethod("getEdgePoint").invoke(targeting);
            if (boundary == null) return null;
            Object id = boundary.getClass().getField("id").get(boundary);
            return id instanceof java.util.UUID uuid ? uuid : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
