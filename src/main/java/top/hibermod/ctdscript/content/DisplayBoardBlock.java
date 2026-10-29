package top.hibermod.ctdscript.content;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 显示板方块：外壳。
 *
 * <p>内容全在 {@link DisplayBoardBlockEntity} 里，本类只负责把实体接上。
 * 渲染见客户端 {@code DisplayBoardRenderer}。
 */
public class DisplayBoardBlock extends Block implements EntityBlock {

    public DisplayBoardBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayBoardBlockEntity(pos, state);
    }

    /** 服务端每刻回调：显示板在这里**主动**把内容拉下来。 */
    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof DisplayBoardBlockEntity board) {
                DisplayBoardBlockEntity.pullTick(lvl, pos, st, board);
            }
        };
    }
}
