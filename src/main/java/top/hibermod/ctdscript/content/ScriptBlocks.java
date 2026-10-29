package top.hibermod.ctdscript.content;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 把脚本声明的方块真正注册进 Minecraft：方块、方块实体、物品、创造栏。
 *
 * <h2>时序</h2>
 * 脚本在**模组构造期**跑完，收集成 {@link BlockDefinition}；
 * 本类在随后的 {@code RegisterEvent} 里把每个定义变成注册表里的东西。
 * 注册表关掉之后就再也加不进去了，所以中间只传数据。
 *
 * <h2>为什么要注册四样东西</h2>
 * <table border="1">
 *   <tr><th>注册表</th><th>不注册会怎样</th></tr>
 *   <tr><td>{@code BLOCK}</td><td>方块根本不存在</td></tr>
 *   <tr><td>{@code BLOCK_ENTITY_TYPE}</td><td>能放下但不能存状态、不能响应</td></tr>
 *   <tr><td>{@code ITEM}</td><td>创造模式里拿不到，只能 {@code /setblock}</td></tr>
 *   <tr><td>{@code CREATIVE_MODE_TAB}</td><td>能拿到，但埋在搜索里，玩家找不到</td></tr>
 * </table>
 * 再加上 {@link ScriptPack} 生成的资源包，才是一个「完整」的方块。
 */
public final class ScriptBlocks {

    private static BlockEntityType<ScriptBlockEntity> beType;
    private static CreativeModeTab tab;

    private ScriptBlocks() {}

    public static void attach(IEventBus modBus) {
        modBus.addListener(ScriptBlocks::onRegister);
    }

    /** 供 {@link ScriptBlock#newBlockEntity} 使用；注册完成后才有值。 */
    public static BlockEntityType<ScriptBlockEntity> blockEntityType() {
        return beType;
    }

    /** 脚本方块与引擎方块共用一页。 */
    public static CreativeModeTab tab() {
        return EngineBlocks.tab();
    }

    private static void onRegister(RegisterEvent event) {
        if (ContentRegistry.blocks().isEmpty()) return;

        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            for (BlockDefinition def : ContentRegistry.blocks()) {
                event.register(Registries.BLOCK, def.id(),
                    () -> new ScriptBlock(properties(def), def));
            }

        } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            List<Block> blocks = new ArrayList<>();
            for (BlockDefinition def : ContentRegistry.blocks()) {
                Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(def.id());
                if (b != null) blocks.add(b);
            }
            if (blocks.isEmpty()) return;
            beType = BlockEntityType.Builder.of(ScriptBlockEntity::new, blocks.toArray(new Block[0]))
                .build(null);
            event.register(Registries.BLOCK_ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath("ctdscript", "script_block"), () -> beType);

        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            for (BlockDefinition def : ContentRegistry.blocks()) {
                event.register(Registries.ITEM, def.id(), () -> new BlockItem(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(def.id()),
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

    public static boolean isRegistered(BlockDefinition def) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(def.id());
    }

    /** 注册结果摘要（启动日志用）。 */
    public static String summary() {
        int ok = 0;
        for (BlockDefinition def : ContentRegistry.blocks()) {
            if (isRegistered(def)) ok++;
        }
        return "blocks=" + ok + "/" + ContentRegistry.blocks().size()
            + " beType=" + (beType != null) + " tab=" + (tab != null);
    }
}
