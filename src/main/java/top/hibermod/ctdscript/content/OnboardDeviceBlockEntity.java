package top.hibermod.ctdscript.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 车载设备的方块实体。
 *
 * <h2>它不存「启用」</h2>
 * 是否显示仪表是**玩家自己的偏好**，存在存档的 {@code PlayerHudPrefs} 里，
 * 不在这块方块的 NBT 里。方块只表明「这列车装了设备」。
 *
 * <p>把人的偏好写进方块，就会变成「最后点的那个人决定全车人看到什么」——
 * 既不对，也解释不通。
 *
 * <h2>那它存什么</h2>
 * 一个自由 tag：以后要做「这车装的是速度表还是电压表」，就加在这里。
 * 现在保持空，是为了让「装上设备」这个事实本身可读、可同步。
 */
public class OnboardDeviceBlockEntity extends BlockEntity {

    private CompoundTag deviceState = new CompoundTag();

    public OnboardDeviceBlockEntity(BlockPos pos, BlockState state) {
        this(EngineBlocks.onboardDeviceType(), pos, state);
    }

    public OnboardDeviceBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                    BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 设备自身的状态（暂时为空）。 */
    public CompoundTag deviceState() {
        return deviceState;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Device", deviceState.copy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Device")) {
            deviceState = tag.getCompound("Device").copy();
        }
    }
}
