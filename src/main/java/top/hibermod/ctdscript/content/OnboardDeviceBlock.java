package top.hibermod.ctdscript.content;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import top.hibermod.ctdscript.net.HudPrefPayload;
import top.hibermod.ctdscript.server.PlayerHudPrefs;

/**
 * 车载设备方块。每种设备是**独立的方块**。
 *
 * <h2>为什么不用「一个方块 + 型号字段」</h2>
 * 那需要放置钩子把型号写进状态或 NBT，中间任何一环没接上，
 * 现象都是「装了 ATP，引擎以为是速度表」。
 * 独立方块把「这是哪种设备」变成注册表里的事实，没有推断环节。
 *
 * <h2>右键</h2>
 * 切换**这个玩家**看不看指示器：车是大家共用的，眼睛是各人自己的。
 */
public class OnboardDeviceBlock extends Block implements EntityBlock {

    private final DeviceKind kind;

    public OnboardDeviceBlock(Properties properties, DeviceKind kind) {
        super(properties);
        this.kind = kind;
    }

    /** 这块方块是哪一种设备。由方块本身决定。 */
    public DeviceKind kind() {
        return kind;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OnboardDeviceBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }

        boolean now = PlayerHudPrefs.get(serverLevel.getServer()).toggle(sp.getUUID());
        sp.connection.send(new HudPrefPayload(now));

        sp.displayClientMessage(
            Component.translatable(now
                ? "block.ctdscript.onboard_display.on"
                : "block.ctdscript.onboard_display.off"), true);
        return InteractionResult.CONSUME;
    }
}
