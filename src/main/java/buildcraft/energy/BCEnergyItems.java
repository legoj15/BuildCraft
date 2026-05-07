package buildcraft.energy;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class BCEnergyItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("buildcraftunofficial");

    public static final DeferredItem<BlockItem> ENGINE_STONE = ITEMS.registerSimpleBlockItem(
            BCEnergyBlocks.ENGINE_STONE);

    public static final DeferredItem<BlockItem> ENGINE_IRON = ITEMS.registerSimpleBlockItem(
            BCEnergyBlocks.ENGINE_IRON);

    /** FE/RF naming-aware BlockItem: flips display name based on {@link BCEnergyConfig#useRfNaming}.
     *  Uses {@code useBlockDescriptionPrefix()} so the auto-generated descriptionId resolves to the
     *  {@code block.buildcraftunofficial.engine_rf} lang entry (matching how registerSimpleBlockItem
     *  sets up other engines), letting {@link BCEnergyConfig#rfFeKey} pick up the {@code .rf} sibling. */
    public static final DeferredItem<BlockItem> ENGINE_FE = ITEMS.registerItem(
            BCEnergyBlocks.ENGINE_FE.getId().getPath(),
            props -> new BlockItem(BCEnergyBlocks.ENGINE_FE.get(), props) {
                @Override
                public Component getName(ItemStack stack) {
                    return Component.translatable(BCEnergyConfig.rfFeKey(getDescriptionId()));
                }
            },
            props -> props.useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> DYNAMO_MJ = ITEMS.registerSimpleBlockItem(
            BCEnergyBlocks.DYNAMO_MJ);

    // Glob of Oil — registered under a separate registry because its assets are in buildcraftenergy
    private static final DeferredRegister.Items ENERGY_ITEMS = DeferredRegister.createItems("buildcraftunofficial");

    public static final DeferredItem<Item> GLOB_OF_OIL =
            ENERGY_ITEMS.registerSimpleItem("glob_of_oil");

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENERGY_ITEMS.register(modEventBus);
    }
}
