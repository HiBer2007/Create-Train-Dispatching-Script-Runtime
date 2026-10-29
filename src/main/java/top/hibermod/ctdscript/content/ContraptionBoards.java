package top.hibermod.ctdscript.content;

import java.util.List;
import java.util.Map;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * 往**已经组装到车上**的显示板写内容。
 *
 * <h2>为什么普通方块实体的写法在这里无效</h2>
 * 方块一旦被组装，它就离开了世界：世界里那个坐标什么都不剩，
 * 真正的数据在 {@code Contraption.getBlocks()} 里的一份快照
 * （{@code StructureBlockInfo.state} + {@code nbt}）。
 * 所以对车上的板子调用 {@code level.getBlockEntity(pos)} 永远是 null。
 *
 * <p>Create 自己也是这么改车载方块的：见 {@code MovingInteractionBehaviour
 * #setContraptionBlockData}——把那份快照换掉，再让客户端重建。
 *
 * <h2>范围</h2>
 * 一次写一块。批量写在调度里没有意义：显示内容是按车、按板子来的。
 */
public final class ContraptionBoards {

    private ContraptionBoards() {}

    /**
     * 找到玩家/脚本指向的那辆车上的显示板并写入。
     *
     * @param level  服务端世界
     * @param entity 构造物实体
     * @param lines  内容
     * @return 是否写入成功
     */
    public static boolean set(ServerLevel level, AbstractContraptionEntity entity, List<String> lines) {
        if (entity == null) return false;
        Contraption contraption = entity.getContraption();
        if (contraption == null) return false;

        int written = 0;
        for (Map.Entry<BlockPos, StructureBlockInfo> e : contraption.getBlocks().entrySet()) {
            StructureBlockInfo info = e.getValue();
            if (!(info.state().getBlock() instanceof DisplayBoardBlock)) continue;

            CompoundTag nbt = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
            nbt.put("Lines", toList(lines));

            // 走 Create 自己的替换通道：它还会把改动同步给客户端
            ContraptionDataWriter.write(entity, e.getKey(),
                new StructureBlockInfo(info.pos(), info.state(), nbt));
            written++;
        }

        if (written == 0) return false;

        return true;
    }

    private static ListTag toList(List<String> lines) {
        ListTag list = new ListTag();
        if (lines != null) {
            for (String line : lines) {
                if (list.size() >= DisplayBoardBlockEntity.MAX_LINES) break;
                String text = line == null ? "" : line;
                if (text.length() > DisplayBoardBlockEntity.MAX_CHARS) {
                    text = text.substring(0, DisplayBoardBlockEntity.MAX_CHARS);
                }
                list.add(StringTag.valueOf(text));
            }
        }
        return list;
    }

    /** 这辆车上有没有显示板。 */
    public static boolean has(AbstractContraptionEntity entity) {
        if (entity == null || entity.getContraption() == null) return false;
        for (StructureBlockInfo info : entity.getContraption().getBlocks().values()) {
            if (info.state().getBlock() instanceof DisplayBoardBlock) return true;
        }
        return false;
    }

    /** 玩家正看着的那辆构造物；没有返回 null。 */
    public static AbstractContraptionEntity aimed(net.minecraft.world.entity.player.Player player) {
        var hit = player.pick(8.0, 0, false);
        if (!(hit instanceof net.minecraft.world.phys.EntityHitResult ehr)) return null;
        Entity target = ehr.getEntity();
        return target instanceof AbstractContraptionEntity ace ? ace : null;
    }

    /** 车上第一块显示板的局部坐标（调试用）。 */
    public static BlockPos firstBoardPos(AbstractContraptionEntity entity) {
        if (entity == null || entity.getContraption() == null) return null;
        for (Map.Entry<BlockPos, StructureBlockInfo> e : entity.getContraption().getBlocks().entrySet()) {
            if (e.getValue().state().getBlock() instanceof DisplayBoardBlock) return e.getKey();
        }
        return null;
    }

    /** 读车上那块板子的内容。 */
    public static List<String> get(AbstractContraptionEntity entity, BlockPos localPos) {
        if (entity == null || entity.getContraption() == null || localPos == null) return List.of();
        StructureBlockInfo info = entity.getContraption().getBlocks().get(localPos);
        if (info == null || info.nbt() == null) return List.of();

        ListTag list = info.nbt().getList("Lines", Tag.TAG_STRING);
        java.util.List<String> out = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }
}
