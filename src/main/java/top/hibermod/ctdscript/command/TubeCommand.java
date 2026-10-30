package top.hibermod.ctdscript.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * {@code /ctd tube} —— 把准星那块的「显示来源」摊开。
 *
 * <h2>为什么需要它</h2>
 * 「信号机的灯」在游戏里可能来自三个不同的地方：
 * 信号机自己的指示灯、贴在上面的辉光管（红石显示）、或者别的模组的附属件。
 * 这三种的外观差不多，但渲染代码完全不同；只凭截图无法判断应该注入哪里。
 *
 * <p>这条命令直接报告：方块 id、方块实体类、信号状态字段、以及我们这边
 * 为这架信号机记下的语义。有了它就不必猜。
 */
public final class TubeCommand {

    private TubeCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tube").executes(TubeCommand::report);
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        Player player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("must be run by a player"));
            return 0;
        }

        var hit = player.pick(8.0, 0, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            ctx.getSource().sendFailure(Component.literal("aim at a block"));
            return 0;
        }

        var level = player.level();
        var pos = bhr.getBlockPos();
        var state = level.getBlockState(pos);
        var be = level.getBlockEntity(pos);

        say(ctx, "pos    : " + pos.toShortString());
        say(ctx, "block  : " + net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(state.getBlock()));
        say(ctx, "be     : " + (be == null ? "none" : be.getClass().getName()));

        if (be != null) {
            say(ctx, "signalState   : " + field(be, "signalState"));
            say(ctx, "computerSignal: " + field(be, "computerSignal"));
            say(ctx, "edgePoint     : " + (field(be, "edgePoint") == null ? "null" : "present"));
        }

        // 这架信号机的语义（如果它是信号机 / 贴着信号机）
        java.util.UUID signalId = signalIdOf(be);
        if (signalId == null) {
            say(ctx, "aspect : (no signal id resolved)");
        } else {
            String aspect = top.hibermod.ctdapi.status.SignalStates.get(signalId);
            say(ctx, "signal : " + signalId);
            say(ctx, "aspect : " + (aspect.isEmpty() ? "(none - kernel has not set one)"
                : aspect));
            say(ctx, "theirs : " + top.hibermod.ctdapi.signal.SignalSystemHolder
                .systemOf(boundaryOf(be)));
        }

        return 1;
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String line) {
        ctx.getSource().sendSuccess(() -> Component.literal("  " + line), false);
    }

    /** 通用字段读取：读不到返回 "?"，而不是抛。 */
    private static Object field(Object target, String name) {
        if (target == null) return null;
        try {
            return target.getClass().getField(name).get(target);
        } catch (Throwable ignored) {
            try {
                var f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (Throwable ignored2) {
                return "?";
            }
        }
    }

    /** 从信号机方块实体或辉光管方块实体上，反查它所服务的信号机 id。 */
    private static java.util.UUID signalIdOf(Object be) {
        if (be == null) return null;

        if (be instanceof com.simibubi.create.content.trains.signal.SignalBlockEntity signal) {
            var boundary = signal.edgePoint == null ? null : signal.edgePoint.getEdgePoint();
            return boundary == null ? null : boundary.id;
        }

        // 辉光管：缓存里可能挂着信号机方块实体
        Object cached = field(be, "cachedSignalTE");
        if (cached instanceof java.lang.ref.WeakReference<?> weak) {
            Object value = weak.get();
            if (value instanceof com.simibubi.create.content.trains.signal.SignalBlockEntity signal) {
                var boundary = signal.edgePoint == null ? null : signal.edgePoint.getEdgePoint();
                return boundary == null ? null : boundary.id;
            }
        }
        return null;
    }

    private static Object boundaryOf(Object be) {
        if (be instanceof com.simibubi.create.content.trains.signal.SignalBlockEntity signal) {
            return signal.edgePoint == null ? null : signal.edgePoint.getEdgePoint();
        }
        Object cached = field(be, "cachedSignalTE");
        if (cached instanceof java.lang.ref.WeakReference<?> weak) {
            Object value = weak.get();
            if (value instanceof com.simibubi.create.content.trains.signal.SignalBlockEntity s) {
                return s.edgePoint == null ? null : s.edgePoint.getEdgePoint();
            }
        }
        return null;
    }
}
