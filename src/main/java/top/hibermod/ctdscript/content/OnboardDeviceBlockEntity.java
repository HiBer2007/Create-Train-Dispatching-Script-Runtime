package top.hibermod.ctdscript.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 车载设备的方块实体。
 *
 * <h2>存什么</h2>
 * 只有一个 {@code enabled}。它决定「这块设备所属的列车要不要显示 HUD 指示器」。
 *
 * <h2>为什么要存在顶层而不是嵌一层</h2>
 * 方块被装到列车上时，Create 会把方块实体的 NBT **原样**塞进构造物
 * （{@code StructureBlockInfo.nbt()}）。客户端读的是那份 NBT——
 * 键写在顶层，客户端一句 {@code getBoolean("enabled")} 就能查到，
 * 不必反序列化整个实体。
 */
public class OnboardDeviceBlockEntity extends BlockEntity {

    private boolean enabled;

    public OnboardDeviceBlockEntity(BlockPos pos, BlockState state) {
        this(EngineBlocks.onboardDeviceType(), pos, state);
    }

    public OnboardDeviceBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                    BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (this.enabled == value) return;
        this.enabled = value;
        setChanged();
    }

    public boolean toggle() {
        setEnabled(!enabled);
        return enabled;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // 顶层：构造物里读的就是这一层
        tag.putBoolean("enabled", enabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        enabled = tag.getBoolean("enabled");
    }
}
