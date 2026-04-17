package buildcraft.energy;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class BCEnergyItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("buildcraftunofficial");

    public static final DeferredItem<BlockItem> ENGINE_STONE = ITEMS.registerItem("engine_stone",
            props -> new BlockItem(BCEnergyBlocks.ENGINE_STONE.get(), props) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                        net.minecraft.world.item.Item.TooltipContext context,
                        net.minecraft.world.item.component.TooltipDisplay display,
                        java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip,
                        net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.accept(net.minecraft.network.chat.Component.translatable(
                            "tip.block.engine_stone").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            });

    public static final DeferredItem<BlockItem> ENGINE_IRON = ITEMS.registerItem("engine_iron",
            props -> new BlockItem(BCEnergyBlocks.ENGINE_IRON.get(), props) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                        net.minecraft.world.item.Item.TooltipContext context,
                        net.minecraft.world.item.component.TooltipDisplay display,
                        java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip,
                        net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.accept(net.minecraft.network.chat.Component.translatable(
                            "tip.block.engine_iron").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            });

    public static final DeferredItem<BlockItem> ENGINE_FE = ITEMS.registerItem("engine_rf",
            props -> new BlockItem(BCEnergyBlocks.ENGINE_FE.get(), props) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                        net.minecraft.world.item.Item.TooltipContext context,
                        net.minecraft.world.item.component.TooltipDisplay display,
                        java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip,
                        net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.accept(net.minecraft.network.chat.Component.translatable(
                            "tip.block.engine_rf").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            });

    public static final DeferredItem<BlockItem> DYNAMO_MJ = ITEMS.registerItem("mj_dynamo",
            props -> new BlockItem(BCEnergyBlocks.DYNAMO_MJ.get(), props) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                        net.minecraft.world.item.Item.TooltipContext context,
                        net.minecraft.world.item.component.TooltipDisplay display,
                        java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip,
                        net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.accept(net.minecraft.network.chat.Component.translatable(
                            "tip.block.mj_dynamo").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            });

    // Glob of Oil — registered under a separate registry because its assets are in buildcraftenergy
    private static final DeferredRegister.Items ENERGY_ITEMS = DeferredRegister.createItems("buildcraftunofficial");

    public static final DeferredItem<Item> GLOB_OF_OIL =
            ENERGY_ITEMS.registerSimpleItem("glob_of_oil");

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENERGY_ITEMS.register(modEventBus);
    }
}
