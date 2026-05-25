package buildcraft.transport;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.transport.item.ItemPluggableSimple;
import buildcraft.transport.item.ItemWire;
import buildcraft.transport.plug.PluggableBlocker;
import buildcraft.transport.plug.PluggablePowerAdaptor;

import buildcraft.transport.item.ItemPipeHolder;

public class BCTransportItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCTransport.MODID);

    // -- Data Components --
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BCTransport.MODID);

    /** Carries the paint colour on a pipe ItemStack (pick-block, drops, placement). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DyeColor>> PIPE_COLOUR =
            DATA_COMPONENTS.registerComponentType("pipe_colour",
                    builder -> builder.persistent(DyeColor.CODEC)
                                      .networkSynchronized(DyeColor.STREAM_CODEC));

    // -- Non-pipe items --

    public static final DeferredItem<BlockItem> FILTERED_BUFFER = ITEMS.registerSimpleBlockItem(
            BCTransportBlocks.FILTERED_BUFFER);

    /** Pipe Sealant — used to craft fluid pipes from item pipes. */
    public static final DeferredItem<Item> WATERPROOF = ITEMS.registerSimpleItem("waterproof");

    /** Plug — blocks a pipe face, preventing connections. */
    public static final DeferredItem<ItemPluggableSimple> PLUG_BLOCKER = ITEMS.registerItem("plug_blocker",
            props -> new ItemPluggableSimple(props, BCTransportPlugs.blocker, null,
                    PluggableBlocker::boundingBoxFor));

    /** Power Adaptor Plug — allows MJ to pass into a pipe from adjacents. Only placeable on kinesis pipes. */
    public static final DeferredItem<ItemPluggableSimple> PLUG_POWER_ADAPTOR = ITEMS.registerItem("plug_power_adaptor",
            props -> new ItemPluggableSimple(props, BCTransportPlugs.powerAdaptor,
                    ItemPluggableSimple.PIPE_BEHAVIOUR_ACCEPTS_RS_POWER,
                    PluggablePowerAdaptor::boundingBoxFor));

    // -- Wire Items (one per DyeColor) --
    public static final java.util.Map<DyeColor, DeferredItem<ItemWire>> WIRE_ITEMS;
    static {
        java.util.Map<DyeColor, DeferredItem<ItemWire>> map = new java.util.EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            DyeColor c = color; // effectively final for lambda
            map.put(color, ITEMS.registerItem("wire_" + color.getName(),
                    props -> new ItemWire(props, c)));
        }
        WIRE_ITEMS = java.util.Collections.unmodifiableMap(map);
    }

    // PIPE_WOOD_ITEM uses ItemPipeHolder to tie the item to the pipe_holder block + pipe definition.
    // Other pipes remain simple Item stubs until they are individually ported.

    // Structure
    public static final DeferredItem<ItemPipeHolder> PIPE_STRUCTURE = ITEMS.registerItem("pipe_structure",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.structure, props).registerWithPipeApi());

    // Item transport pipes
    public static final DeferredItem<ItemPipeHolder> PIPE_WOOD_ITEM = ITEMS.registerItem("pipe_wood_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.woodItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_COBBLE_ITEM = ITEMS.registerItem("pipe_cobble_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.cobbleItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_STONE_ITEM = ITEMS.registerItem("pipe_stone_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.stoneItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_QUARTZ_ITEM = ITEMS.registerItem("pipe_quartz_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.quartzItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_IRON_ITEM = ITEMS.registerItem("pipe_iron_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.ironItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_GOLD_ITEM = ITEMS.registerItem("pipe_gold_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.goldItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_CLAY_ITEM = ITEMS.registerItem("pipe_clay_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.clayItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_SANDSTONE_ITEM = ITEMS.registerItem("pipe_sandstone_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.sandstoneItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_VOID_ITEM = ITEMS.registerItem("pipe_void_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.voidItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_OBSIDIAN_ITEM = ITEMS.registerItem("pipe_obsidian_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.obsidianItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_ITEM = ITEMS.registerItem("pipe_diamond_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.diamondItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_WOOD_ITEM = ITEMS.registerItem("pipe_diamond_wood_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.diaWoodItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_LAPIS_ITEM = ITEMS.registerItem("pipe_lapis_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.lapisItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_DAIZULI_ITEM = ITEMS.registerItem("pipe_daizuli_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.daizuliItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_EMZULI_ITEM = ITEMS.registerItem("pipe_emzuli_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.emzuliItem, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_STRIPES_ITEM = ITEMS.registerItem("pipe_stripes_item",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.stripesItem, props).registerWithPipeApi());

    // Fluid transport pipes
    public static final DeferredItem<ItemPipeHolder> PIPE_WOOD_FLUID = ITEMS.registerItem("pipe_wood_fluid",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.woodFluid, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_COBBLE_FLUID = ITEMS.registerItem("pipe_cobble_fluid",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.cobbleFluid, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_STONE_FLUID = ITEMS.registerItem("pipe_stone_fluid",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.stoneFluid, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_QUARTZ_FLUID = ITEMS.registerItem("pipe_quartz_fluid",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.quartzFluid, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_GOLD_FLUID = ITEMS.registerItem("pipe_gold_fluid",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.goldFluid, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_IRON_FLUID = ITEMS.registerItem("pipe_iron_fluid",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.ironFluid, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_CLAY_FLUID = ITEMS.registerItem("pipe_clay_fluid",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.clayFluid, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_SANDSTONE_FLUID = ITEMS.registerItem("pipe_sandstone_fluid",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.sandstoneFluid, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_VOID_FLUID = ITEMS.registerItem("pipe_void_fluid",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.voidFluid, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_FLUID = ITEMS.registerItem("pipe_diamond_fluid",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.diamondFluid, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_WOOD_FLUID = ITEMS.registerItem("pipe_diamond_wood_fluid",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.diaWoodFluid, props).registerWithPipeApi());

    // Power transport pipes
    public static final DeferredItem<ItemPipeHolder> PIPE_WOOD_POWER = ITEMS.registerItem("pipe_wood_power",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.woodPower, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_COBBLE_POWER = ITEMS.registerItem("pipe_cobble_power",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.cobblePower, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_STONE_POWER = ITEMS.registerItem("pipe_stone_power",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.stonePower, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_QUARTZ_POWER = ITEMS.registerItem("pipe_quartz_power",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.quartzPower, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_IRON_POWER = ITEMS.registerItem("pipe_iron_power",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.ironPower, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_GOLD_POWER = ITEMS.registerItem("pipe_gold_power",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.goldPower, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_SANDSTONE_POWER = ITEMS.registerItem("pipe_sandstone_power",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.sandstonePower, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_POWER = ITEMS.registerItem("pipe_diamond_power",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.diamondPower, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_WOOD_POWER = ITEMS.registerItem("pipe_diamond_wood_power",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.diaWoodPower, props).registerWithPipeApi());

    // RF/FE transport pipes
    public static final DeferredItem<ItemPipeHolder> PIPE_WOOD_RF = ITEMS.registerItem("pipe_wood_rf",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.woodRf, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_COBBLE_RF = ITEMS.registerItem("pipe_cobble_rf",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.cobbleRf, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_STONE_RF = ITEMS.registerItem("pipe_stone_rf",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.stoneRf, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_QUARTZ_RF = ITEMS.registerItem("pipe_quartz_rf",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.quartzRf, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_IRON_RF = ITEMS.registerItem("pipe_iron_rf",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.ironRf, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_GOLD_RF = ITEMS.registerItem("pipe_gold_rf",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.goldRf, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_SANDSTONE_RF = ITEMS.registerItem("pipe_sandstone_rf",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.sandstoneRf, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_RF = ITEMS.registerItem("pipe_diamond_rf",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.diamondRf, props).registerWithPipeApi());
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_WOOD_RF = ITEMS.registerItem("pipe_diamond_wood_rf",
            props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(),
                    BCTransportPipes.diaWoodRf, props).registerWithPipeApi());

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
    }
}
