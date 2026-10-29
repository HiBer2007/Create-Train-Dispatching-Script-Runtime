package top.hibermod.ctdscript.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import top.hibermod.ctdscript.content.DisplayBoardBlockEntity;

/**
 * {@code /ctd display <文本>} —— 给准星指的显示板设置内容。
 *
 * <h2>为什么先做命令再做脚本 API</h2>
 * 命令能立刻验证「存得住、改得动、画得出」这三件事；
 * 脚本 API 只是把同一个 setter 暴露出去。先做 API 的话，
 * 出问题时说不清是接口错了还是渲染错了。
 *
 * <p>文本用 {@code |} 分行，最多 {@link DisplayBoardBlockEntity#MAX_LINES} 行。
 */
public final class DisplayCommand {

    private DisplayCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("display")
            .then(Commands.literal("clear").executes(ctx -> set(ctx, List.of())))
            .then(Commands.argument("text", StringArgumentType.greedyString())
                .executes(ctx -> set(ctx,
                    Arrays.asList(StringArgumentType.getString(ctx, "text").split("\\|")))));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, List<String> lines) {
        // 只有玩家能用：需要准星
        var player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("must be run by a player (needs aiming)"));
            return 0;
        }

        HitResult hit = player.pick(6.0, 0, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            ctx.getSource().sendFailure(Component.literal("aim at a display board (within 6 blocks)"));
            return 0;
        }

        BlockPos pos = bhr.getBlockPos();
        if (!(player.level().getBlockEntity(pos) instanceof DisplayBoardBlockEntity board)) {
            ctx.getSource().sendFailure(Component.literal("that block is not a display board"));
            return 0;
        }

        List<String> cleaned = new ArrayList<>(lines);
        board.setLines(cleaned);

        ctx.getSource().sendSuccess(() -> Component.literal(
            "display set to " + board.lines().size() + " line(s)"), false);
        return board.lines().size();
    }
}
