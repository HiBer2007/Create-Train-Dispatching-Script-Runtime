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

    public static final ResourceLocation DISPLAY_BOARD =
        ResourceLocation.fromNamespaceAndPath("ctdscript", "display_board");

    /** 速度表设备物品（放置同一个方块，型号不同）。 */
    public static final ResourceLocation ITEM_SPEEDOMETER =
        ResourceLocation.fromNamespaceAndPath("ctdscript", "onboard_speedometer");
    /** CBTC 车载 ATP 物品。 */
    public static final ResourceLocation ITEM_CBTC_ATP =
        ResourceLocation.fromNamespaceAndPath("ctdscript", "onboard_cbtc_atp");

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

    /** 显示板方块本体。 */
    public static Block displayBoard() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(DISPLAY_BOARD);
    }

    public static CreativeModeTab tab() {
        return tab;
    }

    private static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            event.register(Registries.BLOCK, DISPLAY_BOARD, () -> new DisplayBoardBlock(
                BlockBehaviour.Properties.of()
                    .strength(1.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

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

            displayType = BlockEntityType.Builder.of(DisplayBoardBlockEntity::new,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(DISPLAY_BOARD))
                .build(null);
            event.register(Registries.BLOCK_ENTITY_TYPE, DISPLAY_BOARD, () -> displayType);

        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            event.register(Registries.ITEM, ONBOARD_DISPLAY, () -> new BlockItem(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ONBOARD_DISPLAY),
                new Item.Properties()));
            event.register(Registries.ITEM, DISPLAY_BOARD, () -> new BlockItem(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(DISPLAY_BOARD),
                new Item.Properties()));

            // 同一个方块、不同型号：玩家在创造栏里看到的是两种设备
            event.register(Registries.ITEM, ITEM_SPEEDOMETER,
                () -> new KindedDeviceItem(DeviceKind.SPEEDOMETER));
            event.register(Registries.ITEM, ITEM_CBTC_ATP,
                () -> new KindedDeviceItem(DeviceKind.CBTC_ATP));

        } else if (event.getRegistryKey().equals(
                com.simibubi.create.api.registry.CreateRegistries.DISPLAY_SOURCE)) {
            // 复用 Create 的 DisplayLink：我们只提供数据，显示交给辉光管/告示牌
            event.register(com.simibubi.create.api.registry.CreateRegistries.DISPLAY_SOURCE,
                ResourceLocation.fromNamespaceAndPath("ctdscript", "cbtc"),
                CbtcDisplaySource::new);

        } else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            tab = CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.ctdscript.content"))
                .icon(() -> new ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ONBOARD_DISPLAY)))
                .displayItems((params, output) -> {
                    // 引擎方块先列，脚本方块跟后面：玩家不需要知道区别
                    output.accept(new ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ONBOARD_DISPLAY)));
                    output.accept(new ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(DISPLAY_BOARD)));
                    output.accept(new ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ITEM_SPEEDOMETER)));
                    output.accept(new ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ITEM_CBTC_ATP)));
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

    /**
     * 注册「车上右键」的行为。
     *
     * <p>必须在世界加载前完成——构造物落地时就会去 {@code interactors} 里查，
     * 那时候注册表已经要齐了。
     */
    public static void registerInteractions() {
        Block block = onboardBlock();
        if (block == null) return;
        com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY
            .register(block, new OnboardDeviceInteraction());

        // 自检：注册完立刻查一次。查不到就意味着「挂了但没生效」，
        // 那种情况在游戏里表现为右键没反应，且不报错。
        var check = com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour
            .REGISTRY.get(block.defaultBlockState());
        System.out.println("[ctdscript] onboard interaction behaviour "
            + (check == null ? "NOT registered" : "registered: " + check.getClass().getSimpleName()));
    }

    /**
     * 放置时写入型号的设备物品。
     *
     * <p>三种设备共用一块方块，是为了让「设备」这个概念只有一个实现；
     * 型号在放置那一刻写进方块实体，之后拆装、存档都跟着走。
     */
    private static final class KindedDeviceItem extends BlockItem {
        private final DeviceKind kind;

        KindedDeviceItem(DeviceKind kind) {
            super(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ONBOARD_DISPLAY),
                new Item.Properties());
            this.kind = kind;
        }

        @Override
        public net.minecraft.world.InteractionResult place(
                net.minecraft.world.item.context.BlockPlaceContext ctx) {
            var result = super.place(ctx);
            if (result.consumesAction()) {
                var pos = ctx.getClickedPos();
                if (ctx.getLevel().getBlockEntity(pos) instanceof OnboardDeviceBlockEntity be) {
                    be.setKind(kind);
                }
            }
            return result;
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
