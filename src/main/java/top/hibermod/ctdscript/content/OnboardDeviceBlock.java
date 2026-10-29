package top.hibermod.ctdscript.content;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 车载设备：决定「这列车要不要显示 HUD 指示器」。
 *
 * <h2>用法</h2>
 * 放在车厢上（或先放地上、右键启用、再和车厢一起组装）。
 * 右键切换启用状态——**这个状态随构造物保存**，所以车开走之后仍然有效，
 * 存档读档也不会丢。
 *
 * <h2>为什么闸门要设在车上而不是玩家身上</h2>
 * 「谁在看」和「该不该有仪表」是两件事：
 * 一列没装设备的货车，乘客不该看到速度表；装了设备的客车，换个人坐上去也该看到。
 * 所以开关属于**车**，不属于人。
 */
public class OnboardDeviceBlock extends Block implements EntityBlock {

    public OnboardDeviceBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OnboardDeviceBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof OnboardDeviceBlockEntity device)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        boolean now = device.toggle();
        player.displayClientMessage(
            Component.translatable(now
                ? "block.ctdscript.onboard_display.on"
                : "block.ctdscript.onboard_display.off"), true);
        return InteractionResult.CONSUME;
    }
}
