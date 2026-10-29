package top.hibermod.ctdscript.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import java.util.Map;

/**
 * {@code /ctd fixinteractors} —— 给**已经组装好的车**补上右键行为。
 *
 * <h2>为什么需要这个命令</h2>
 * Create 的交互器表是在**组装那一刻**写进构造物 NBT 的
 * （{@code Contraption#addBlock} 附近：{@code interactors.put(localPos, behaviour)}），
 * 读档时只按那份 NBT 还原（{@code Contraption#readNBT} 里的 {@code "Interactors"} 循环）。
 *
 * <p>推论很硬：一个在「我们的模组还没注册这条行为」时组装的车，
 * **无论重启多少次都不会补上**——它的 NBT 里压根没有这条记录。
 * 玩家看到的现象就是「右键没反应」，而且不报任何错。
 *
 * <p>所以给一个显式的修复入口，而不是让玩家猜「要不要拆了重装」。
 */
public final class FixInteractorsCommand {

    private FixInteractorsCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fixinteractors").executes(FixInteractorsCommand::run);
    }

    /**
     * 取构造物的交互器表。
     *
     * <p>{@code Contraption.interactors} 是 {@code protected}，Create 没有公开访问器。
     * 这里用反射，是**故意**的取舍：这是一个人工触发的修复命令，
     * 失败也只是「没修」，不会污染正常路径。为此给 ctdscript 引入一套 mixin 配置
     * 反而是更重的负担。
     */
    @SuppressWarnings("unchecked")
    private static Map<net.minecraft.core.BlockPos, MovingInteractionBehaviour> interactorsOf(
            Contraption contraption) {
        try {
            var field = Contraption.class.getDeclaredField("interactors");
            field.setAccessible(true);
            return (Map<net.minecraft.core.BlockPos, MovingInteractionBehaviour>) field.get(contraption);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();

        int contraptions = 0;
        int added = 0;

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof AbstractContraptionEntity ace)) continue;
            Contraption contraption = ace.getContraption();
            if (contraption == null) continue;
            contraptions++;

            Map<net.minecraft.core.BlockPos, MovingInteractionBehaviour> interactors = interactorsOf(contraption);
            if (interactors == null) continue;

            for (Map.Entry<net.minecraft.core.BlockPos, StructureBlockInfo> e
                    : contraption.getBlocks().entrySet()) {
                if (interactors.containsKey(e.getKey())) continue;

                MovingInteractionBehaviour behaviour =
                    MovingInteractionBehaviour.REGISTRY.get(e.getValue().state());
                if (behaviour == null) continue;

                interactors.put(e.getKey(), behaviour);
                added++;
            }
        }

        final int c = contraptions;
        final int a = added;
        ctx.getSource().sendSuccess(() -> Component.literal(
            "contraptions=" + c + " interactors added=" + a), false);
        return a;
    }
}
