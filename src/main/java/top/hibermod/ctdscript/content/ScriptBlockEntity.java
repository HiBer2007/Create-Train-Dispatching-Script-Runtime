package top.hibermod.ctdscript.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 脚本方块共用的方块实体。
 *
 * <h2>为什么每个脚本方块都要有</h2>
 * 没有方块实体的方块只能「存在」，不能「响应」：
 * 不能存状态、不能随构造保存、不能在右键时做事、不能 tick。
 * 脚本注册的方块将来要当仪表、告示牌、车载设备用，所以底座先给好。
 *
 * <h2>它存什么</h2>
 * 一个自由格式的 {@code CompoundTag}：脚本写什么就存什么，
 * 引擎只负责在存档/读档之间把它原样带回来。这样脚本不需要改 Java 就能加状态。
 */
public class ScriptBlockEntity extends BlockEntity {

    /** 脚本可读写的自由状态。 */
    private CompoundTag scriptState = new CompoundTag();

    /** 谁在这里下过单（用于排查「内容没刷新」）。 */
    private long lastTouchGameTime = Long.MIN_VALUE;

    public ScriptBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                             BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 供 {@code BlockEntityType.Builder} 使用的两参构造。 */
    public ScriptBlockEntity(BlockPos pos, BlockState state) {
        this(ScriptBlocks.blockEntityType(), pos, state);
    }

    public CompoundTag scriptState() {
        return scriptState;
    }

    public void setScriptState(CompoundTag tag) {
        this.scriptState = tag == null ? new CompoundTag() : tag;
        setChanged();
    }

    public long lastTouchGameTime() {
        return lastTouchGameTime;
    }

    public void touch(long gameTime) {
        this.lastTouchGameTime = gameTime;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("ScriptState", scriptState.copy());
        if (lastTouchGameTime != Long.MIN_VALUE) {
            tag.putLong("LastTouch", lastTouchGameTime);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ScriptState")) {
            scriptState = tag.getCompound("ScriptState").copy();
        }
        lastTouchGameTime = tag.contains("LastTouch") ? tag.getLong("LastTouch") : Long.MIN_VALUE;
    }
}
