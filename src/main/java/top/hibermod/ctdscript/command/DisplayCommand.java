package top.hibermod.ctdscript.command;

import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import top.hibermod.ctdscript.content.ContraptionBoards;
import top.hibermod.ctdscript.content.DisplayBoardBlockEntity;
import top.hibermod.ctdscript.content.DisplaySources;

/**
 * {@code /ctd display ...} —— 显示板的运维命令。
 *
 * <h2>两种用法，对应两种模型</h2>
 * <ul>
 *   <li>{@code bind <source>} —— **推荐**：给板子绑一个数据源，
 *       之后方块实体自己每 20 刻拉一次。新放出来的板子只要绑过源，
 *       第一次 tick 就有内容，不需要任何人记住它的坐标。</li>
 *   <li>{@code <文本>} —— 手动写死一次，调试或临时告示用。
 *       用 {@code |} 分行。</li>
 * </ul>
 *
 * <p>准星指向地面上的板子或一辆载有板子的列车，两者都认。
 */
public final class DisplayCommand {

    private DisplayCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("display")
            .then(Commands.literal("sources").executes(ctx -> {
                ctx.getSource().sendSuccess(
                    () -> Component.literal("display sources: " + DisplaySources.summary()), false);
                return DisplaySources.prefixes().size();
            }))
            .then(Commands.literal("clear").executes(ctx -> set(ctx, List.of())))
            .then(Commands.literal("bind")
                .then(Commands.argument("source", StringArgumentType.greedyString())
                    .executes(DisplayCommand::bind)))
            .then(Commands.argument("text", StringArgumentType.greedyString())
                .executes(ctx -> set(ctx,
                    Arrays.asList(StringArgumentType.getString(ctx, "text").split("\\|")))));
    }

    // ── 绑定数据源 ────────────────────────────────────────────────────────

    private static int bind(CommandContext<CommandSourceStack> ctx) {
        Player player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("must be run by a player (needs aiming)"));
            return 0;
        }

        String source = StringArgumentType.getString(ctx, "source");
        HitResult hit = player.pick(8.0, 0, false);

        // ① 地面上的板子
        if (hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            if (!(player.level().getBlockEntity(pos) instanceof DisplayBoardBlockEntity board)) {
                ctx.getSource().sendFailure(Component.literal("that block is not a display board"));
                return 0;
            }
            board.setSource(source);
            ctx.getSource().sendSuccess(() -> Component.literal(
                "bound '" + source + "' -> " + board.lines()), false);
            return 1;
        }

        // ② 车上的板子（数据在构造物快照里）
        if (hit instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof AbstractContraptionEntity ace) {
            boolean ok = ContraptionBoards.bindSource(
                (ServerLevel) player.level(), ace, source);
            if (!ok) {
                ctx.getSource().sendFailure(Component.literal(
                    "that contraption has no display board"));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                "bound contraption board to '" + source + "'"), false);
            return 1;
        }

        ctx.getSource().sendFailure(Component.literal(
            "aim at a display board or a train carrying one"));
        return 0;
    }

    // ── 手动写内容 ────────────────────────────────────────────────────────

    private static int set(CommandContext<CommandSourceStack> ctx, List<String> lines) {
        Player player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("must be run by a player (needs aiming)"));
            return 0;
        }

        HitResult hit = player.pick(8.0, 0, false);

        // ① 车上
        if (hit instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof AbstractContraptionEntity ace) {
            if (!ContraptionBoards.set((ServerLevel) player.level(), ace, lines)) {
                ctx.getSource().sendFailure(Component.literal(
                    "that contraption has no display board"));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                "display set on contraption (" + lines.size() + " line(s))"), false);
            return lines.size();
        }

        // ② 地面
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            ctx.getSource().sendFailure(Component.literal(
                "aim at a display board or a train carrying one (within 8 blocks)"));
            return 0;
        }

        BlockPos pos = bhr.getBlockPos();
        if (!(player.level().getBlockEntity(pos) instanceof DisplayBoardBlockEntity board)) {
            ctx.getSource().sendFailure(Component.literal("that block is not a display board"));
            return 0;
        }

        // 手动写入会解绑数据源：两者同时生效会互相覆盖，且没人知道最后谁赢
        if (!board.source().isEmpty()) {
            board.setSource("");
        }
        board.setLines(lines);

        ctx.getSource().sendSuccess(() -> Component.literal(
            "display set to " + board.lines().size() + " line(s)"), false);
        return board.lines().size();
    }
}
