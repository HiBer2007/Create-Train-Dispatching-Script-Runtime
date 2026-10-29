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
 * 引擎自带的方块：车载设备（车辆仪表 / CBTC ATP）与显示板。
 *
 * <h2>为什么每种设备是独立方块</h2>
 * 型号若存在状态或 NBT 里，就必须有人在放置时把它写对。
 * 那条链上有三处会出错（放置钩子、状态同步、构造物快照），
 * 任何一处断了，现象都是「装了 ATP，引擎以为是速度表」。
 *
 * <p>独立方块把它变成注册表里的事实：不同 id、不同模型、不同名字。
 */
public final class EngineBlocks {

    public static final ResourceLocation ONBOARD_SPEEDOMETER =
        ResourceLocation.fromNamespaceAndPath("ctdscript", "onboard_speedometer");

    public static final ResourceLocation ONBOARD_CBTC_ATP =
        ResourceLocation.fromNamespaceAndPath("ctdscript", "onboard_cbtc_atp");

    public static final ResourceLocation DISPLAY_BOARD =
        ResourceLocation.fromNamespaceAndPath("ctdscript", "display_board");

    /** 信号制度配置器（右键信号机打开选择界面）。 */
    public static final ResourceLocation SIGNAL_CONFIGURATOR =
        ResourceLocation.fromNamespaceAndPath("ctdscript", "signal_configurator");

    private static BlockEntityType<OnboardDeviceBlockEntity> onboardType;
    private static BlockEntityType<DisplayBoardBlockEntity> displayType;
    private static CreativeModeTab tab;

    private EngineBlocks() {}

    public static void attach(IEventBus modBus) {
        modBus.addListener(EngineBlocks::onRegister);
    }

    public static BlockEntityType<OnboardDeviceBlockEntity> onboardDeviceType() {
        return onboardType;
    }

    public static BlockEntityType<DisplayBoardBlockEntity> displayBoardType() {
        return displayType;
    }

    public static CreativeModeTab tab() {
        return tab;
    }

    /** 全部车载设备方块（右键行为要逐个注册）。 */
    public static List<Block> onboardBlocks() {
        List<Block> out = new ArrayList<>(2);
        Block speedometer = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .get(ONBOARD_SPEEDOMETER);
        Block atp = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ONBOARD_CBTC_ATP);
        if (speedometer != null) out.add(speedometer);
        if (atp != null) out.add(atp);
        return out;
    }

    public static Block displayBoard() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(DISPLAY_BOARD);
    }

    /**
     * 注册「车上右键」的行为。
     *
     * <p>必须在注册表填完之后调用：构造期调用只能拿到 air。
     */
    public static void registerInteractions() {
        var registry = com.simibubi.create.api.behaviour.interaction
            .MovingInteractionBehaviour.REGISTRY;

        for (Block block : onboardBlocks()) {
            registry.register(block, new OnboardDeviceInteraction());

            var check = registry.get(block.defaultBlockState());
            System.out.println("[ctdscript] onboard interaction for "
                + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block)
                + ": " + (check == null ? "NOT registered" : "ok"));
        }
    }

    private static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            event.register(Registries.BLOCK, ONBOARD_SPEEDOMETER, () -> new OnboardDeviceBlock(
                deviceProperties(), DeviceKind.SPEEDOMETER));
            event.register(Registries.BLOCK, ONBOARD_CBTC_ATP, () -> new OnboardDeviceBlock(
                deviceProperties(), DeviceKind.CBTC_ATP));

            event.register(Registries.BLOCK, DISPLAY_BOARD, () -> new DisplayBoardBlock(
                BlockBehaviour.Properties.of()
                    .strength(1.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

        } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            onboardType = BlockEntityType.Builder.of(OnboardDeviceBlockEntity::new,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ONBOARD_SPEEDOMETER),
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ONBOARD_CBTC_ATP))
                .build(null);
            event.register(Registries.BLOCK_ENTITY_TYPE, ONBOARD_SPEEDOMETER, () -> onboardType);

            displayType = BlockEntityType.Builder.of(DisplayBoardBlockEntity::new,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(DISPLAY_BOARD))
                .build(null);
            event.register(Registries.BLOCK_ENTITY_TYPE, DISPLAY_BOARD, () -> displayType);

        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            event.register(Registries.ITEM, ONBOARD_SPEEDOMETER, () -> new BlockItem(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ONBOARD_SPEEDOMETER),
                new Item.Properties()));
            event.register(Registries.ITEM, ONBOARD_CBTC_ATP, () -> new BlockItem(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ONBOARD_CBTC_ATP),
                new Item.Properties()));
            event.register(Registries.ITEM, DISPLAY_BOARD, () -> new BlockItem(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(DISPLAY_BOARD),
                new Item.Properties()));
            event.register(Registries.ITEM, SIGNAL_CONFIGURATOR,
                () -> new SignalConfiguratorItem(new Item.Properties().stacksTo(1)));

        } else if (event.getRegistryKey().equals(
                com.simibubi.create.api.registry.CreateRegistries.DISPLAY_SOURCE)) {
            event.register(com.simibubi.create.api.registry.CreateRegistries.DISPLAY_SOURCE,
                ResourceLocation.fromNamespaceAndPath("ctdscript", "cbtc"),
                CbtcDisplaySource::new);

        } else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            tab = CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.ctdscript.content"))
                .icon(EngineBlocks::icon)
                .displayItems((params, output) -> {
                    accept(output, ONBOARD_SPEEDOMETER);
                    accept(output, ONBOARD_CBTC_ATP);
                    accept(output, DISPLAY_BOARD);
                    accept(output, SIGNAL_CONFIGURATOR);
                    for (BlockDefinition def : ContentRegistry.blocks()) {
                        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .get(def.id());
                        if (item != null) output.accept(new ItemStack(item));
                    }
                })
                .build();
            event.register(Registries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath("ctdscript", "content"), () -> tab);
        }
    }

    private static void accept(CreativeModeTab.Output output, ResourceLocation id) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        if (item != null) output.accept(new ItemStack(item));
    }

    private static ItemStack icon() {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ONBOARD_CBTC_ATP);
        return item == null ? new ItemStack(net.minecraft.world.item.Items.BARRIER)
            : new ItemStack(item);
    }

    private static BlockBehaviour.Properties deviceProperties() {
        return BlockBehaviour.Properties.of()
            .strength(1.5f)
            .sound(SoundType.METAL)
            .noOcclusion();
    }
}
