package top.hibermod.ctdscript.content;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * 改车载方块的**唯一**正确通道。
 *
 * <h2>为什么要这么绕</h2>
 * Create 把「替换构造物里某块方块的数据并同步给客户端」封在
 * {@code MovingInteractionBehaviour#setContraptionBlockData} 里，是 {@code protected}。
 * 手写一遍会漏掉它内部的同步步骤——表现为「服务端改了，客户端看不到」，
 * 而且不报错。
 *
 * <p>所以继承一下，把这层保护方法暴露成一个普通调用。不是 hack，
 * 是复用它的实现；Create 自己所有车载方块（座椅、控制台、储物）都走这条路径。
 */
public final class ContraptionDataWriter extends MovingInteractionBehaviour {

    private static final ContraptionDataWriter INSTANCE = new ContraptionDataWriter();

    private ContraptionDataWriter() {}

    /** 替换某块的数据并同步。 */
    public static void write(AbstractContraptionEntity entity, BlockPos localPos,
                             StructureBlockInfo info) {
        INSTANCE.setContraptionBlockData(entity, localPos, info);
    }

}
