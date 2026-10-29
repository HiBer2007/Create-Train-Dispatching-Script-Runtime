package top.hibermod.ctdscript.content;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 脚本声明的方块本体。
 *
 * <p>只做一件事：接上 {@link ScriptBlockEntity}。其余行为（硬度、发光、碰撞）
 * 全部来自脚本声明，见 {@link BlockDefinition}。
 *
 * <h2>为什么不是普通 {@code Block}</h2>
 * 普通方块没有实体，存不下状态，也就无法「响应」。脚本方块将来要当仪表、
 * 告示牌、车载设备，所以从第一天起就带实体。
 */
public class ScriptBlock extends Block implements EntityBlock {

    private final BlockDefinition definition;

    public ScriptBlock(Properties properties, BlockDefinition definition) {
        super(properties);
        this.definition = definition;
    }

    public BlockDefinition definition() {
        return definition;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScriptBlockEntity(ScriptBlocks.blockEntityType(), pos, state);
    }
}
