package top.hibermod.ctdscript.content;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 显示板：能存几行文字，并让客户端按最新内容画。
 *
 * <h2>为什么不做成「方块状态」</h2>
 * 文字是任意字符串，方块状态只能有有限枚举。而且状态一变就要整块重发，
 * 一条滚动公告会变成网络灾难。所以文字放在方块实体里，只同步 NBT。
 *
 * <h2>为什么限制行数</h2>
 * 4 行 × 每行几十字符已经够站台/车头用。不设上限的话，
 * 一个脚本循环追加就能把区块包撑爆，而报错发生在很远的地方。
 */
public class DisplayBoardBlockEntity extends BlockEntity {

    /** 最多几行。 */
    public static final int MAX_LINES = 4;

    /** 每行最多多少个字符（不是字节——中文也算一个）。 */
    public static final int MAX_CHARS = 48;

    private final List<String> lines = new ArrayList<>();

    public DisplayBoardBlockEntity(BlockPos pos, BlockState state) {
        this(EngineBlocks.displayBoardType(), pos, state);
    }

    public DisplayBoardBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                   BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public List<String> lines() {
        return lines;
    }

    /** 设置内容；超长截断而不是抛异常——站名过长不该让方块报错。 */
    public void setLines(List<String> raw) {
        lines.clear();
        if (raw != null) {
            for (String line : raw) {
                if (lines.size() >= MAX_LINES) break;
                String text = line == null ? "" : line;
                if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS);
                lines.add(text);
            }
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            // 让客户端知道要重画
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (String line : lines) list.add(StringTag.valueOf(line));
        tag.put("Lines", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lines.clear();
        ListTag list = tag.getList("Lines", Tag.TAG_STRING);
        for (int i = 0; i < list.size() && i < MAX_LINES; i++) {
            lines.add(list.getString(i));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
