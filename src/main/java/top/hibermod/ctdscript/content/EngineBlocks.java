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
 * 引擎自带方块：车载设备，以及容纳它们的创造模式物品栏。
 *
 * <h2>为什么引擎要自带方块</h2>
 * HUD 的显示闸门需要「这列车装了设备、且设备被启用」这个事实。
 * 这个事实必须由**引擎自己的**方块承载——不能让脚本决定「什么算装了设备」，
 * 否则换个脚本 HUD 就时有时无，玩家无从判断。
 *
 * <h2>物品栏</h2>
 * 引擎方块与脚本方块放在**同一页**（{@code ctdscript:content}）。
 * 玩家不该关心「这个方块是脚本给的还是引擎给的」。
 */
public final class EngineBlocks {

    public static final ResourceLocation ONBOARD_DISPLAY =
        ResourceLocation.fromNamespaceAndPath("ctdscript", "onboard_display");

    private static BlockEntityType<OnboardDeviceBlockEntity> onboardType;
    private static CreativeModeTab tab;

    private EngineBlocks() {}

    public static void attach(IEventBus modBus) {
        modBus.addListener(EngineBlocks::onRegister);
    }

    public static BlockEntityType<OnboardDeviceBlockEntity> onboardDeviceType() {
        return onboardType;
    }

    public static CreativeModeTab tab() {
        return tab;
    }

    private static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            event.register(Registries.BLOCK, ONBOARD_DISPLAY, () -> new OnboardDeviceBlock(
                BlockBehaviour.Properties.of()
                    .strength(1.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

        } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            onboardType = BlockEntityType.Builder.of(OnboardDeviceBlockEntity::new,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ONBOARD_DISPLAY))
                .build(null);
            event.register(Registries.BLOCK_ENTITY_TYPE, ONBOARD_DISPLAY, () -> onboardType);

        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            event.register(Registries.ITEM, ONBOARD_DISPLAY, () -> new BlockItem(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ONBOARD_DISPLAY),
                new Item.Properties()));

        } else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            tab = CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.ctdscript.content"))
                .icon(() -> new ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ONBOARD_DISPLAY)))
                .displayItems((params, output) -> {
                    // 引擎方块先列，脚本方块跟后面：玩家不需要知道区别
                    output.accept(new ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ONBOARD_DISPLAY)));
                    for (BlockDefinition def : ContentRegistry.blocks()) {
                        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(def.id());
                        if (item != null) output.accept(new ItemStack(item));
                    }
                })
                .build();
            event.register(Registries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath("ctdscript", "content"), () -> tab);
        }
    }

    /** 车载设备方块本体（客户端闸门要按它筛选构造物里的方块）。 */
    public static Block onboardBlock() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ONBOARD_DISPLAY);
    }

    /** 自检：车载设备方块是否进了注册表。 */
    public static boolean onboardRegistered() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(ONBOARD_DISPLAY);
    }

    static List<Block> engineBlocks() {
        List<Block> out = new ArrayList<>();
        Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ONBOARD_DISPLAY);
        if (b != null) out.add(b);
        return out;
    }
}
