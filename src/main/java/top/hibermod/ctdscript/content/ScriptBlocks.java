package top.hibermod.ctdscript.content;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 把脚本声明的方块真正注册进 Minecraft。
 *
 * <h2>时序</h2>
 * 脚本在**模组构造期**跑完，收集成 {@link BlockDefinition}；
 * 本类在随后的 {@code RegisterEvent} 里把每个定义变成一个真实的 {@code Block}。
 * 中间只传数据——注册表关掉之后就再也加不进去了。
 *
 * <h2>方块属性从哪来</h2>
 * 全部来自脚本声明（硬度、发光、碰撞），其余用固定值：石质音效。
 * 纹理**不在这里**——纹理走虚拟资源包，由 {@code ScriptPackResources}
 * 合成 blockstate / 模型并直读脚本目录里的 png。
 */
public final class ScriptBlocks {

    private ScriptBlocks() {}

    public static void attach(IEventBus modBus) {
        modBus.addListener(ScriptBlocks::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        if (ContentRegistry.blocks().isEmpty()) return;

        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            for (BlockDefinition def : ContentRegistry.blocks()) {
                event.register(Registries.BLOCK, def.id(), () -> new Block(properties(def)));
            }
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            for (BlockDefinition def : ContentRegistry.blocks()) {
                event.register(Registries.ITEM, def.id(), () -> new BlockItem(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .get(def.id()).defaultBlockState().getBlock(),
                    new Item.Properties()));
            }
        }
    }

    private static BlockBehaviour.Properties properties(BlockDefinition def) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of()
            .strength(def.hardness())
            .sound(SoundType.STONE)
            .lightLevel(state -> def.light());
        if (!def.solid()) {
            p = p.noCollission().noOcclusion();
        }
        return p;
    }

    /** 引擎自检：某个 id 的方块是否真的进了注册表。 */
    public static boolean isRegistered(BlockDefinition def) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(def.id());
    }

    /** 注册结果摘要（启动日志用）。 */
    public static String summary() {
        int ok = 0;
        for (BlockDefinition def : ContentRegistry.blocks()) {
            if (isRegistered(def)) ok++;
        }
        return "registeredBlocks=" + ok + "/" + ContentRegistry.blocks().size();
    }

}
