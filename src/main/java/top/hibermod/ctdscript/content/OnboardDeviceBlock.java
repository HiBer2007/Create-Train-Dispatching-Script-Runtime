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
 * 车载设备：**这列车具备显示仪表的能力**。
 *
 * <h2>它不是什么</h2>
 * 它不是「某个人要不要看」的开关。开关属于人，见 {@link PlayerHudPrefs}。
 * 如果把这层混进方块，就会出现「司机一开，全车乘客屏幕上都蹦出仪表」——
 * 乘客不想看，也不该看到司机那套读数。
 *
 * <h2>用法</h2>
 * 装在车厢上（或先放地上再和车厢一起组装）。玩家右键它，
 * 切换的是**自己**的显示开关，别的乘客不受影响。
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
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;      // 客户端只回手，不做决定
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }

        boolean now = PlayerHudPrefs.get(serverLevel.getServer()).toggle(sp.getUUID());

        // 立刻把结果发回本人；别的玩家收不到，因为他们没按
        sp.connection.send(new HudPrefPayload(now));

        sp.displayClientMessage(
            Component.translatable(now
                ? "block.ctdscript.onboard_display.on"
                : "block.ctdscript.onboard_display.off"), true);
        return InteractionResult.CONSUME;
    }
}
