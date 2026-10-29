package top.hibermod.ctdscript.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import top.hibermod.ctdscript.content.TrainDevices;
import top.hibermod.ctdscript.server.PlayerHudPrefs;

/**
 * {@code /ctd device} —— 报告准星那列车「引擎认为装了哪些设备」。
 *
 * <h2>为什么需要它</h2>
 * HUD 的闸门有三层（坐车 / 车上有设备 / 我开过开关），任何一层不满足，
 * 玩家看到的现象都是**面板不出现**——三种原因长得一模一样。
 * 这条命令把这三种原因摊开说清楚，省掉来回猜。
 */
public final class DeviceCommand {

    private DeviceCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("device").executes(DeviceCommand::report);
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        Player player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("must be run by a player"));
            return 0;
        }

        // ① 车：先看准星，再看脚下坐着的
        AbstractContraptionEntity target = null;
        var hit = player.pick(8.0, 0, false);
        if (hit instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof AbstractContraptionEntity ace) {
            target = ace;
        } else if (player.getVehicle() instanceof AbstractContraptionEntity riding) {
            target = riding;
        }

        final AbstractContraptionEntity car = target;
        if (car == null) {
            ctx.getSource().sendFailure(Component.literal(
                "aim at a train or ride one (no contraption found)"));
            return 0;
        }

        String devices = TrainDevices.summary(car);
        String system = TrainDevices.signalSystemOf(car);
        boolean enabled = PlayerHudPrefs.get(ctx.getSource().getServer()).isEnabled(player.getUUID());

        ctx.getSource().sendSuccess(() -> Component.literal("CTD onboard devices"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  contraption : " + car.getId()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  devices     : " + devices), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "  signal system: " + (system == null ? "(none -> fixed block)" : system)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "  my indicator: " + (enabled ? "ON" : "off  <- right-click the device")), false);

        return 1;
    }
}
