package buildcraft.transport;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import buildcraft.lib.misc.RegistrationUtilBC;

import buildcraft.api.transport.pipe.PipeDefinition;

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
    public static final DeferredItem<ItemPluggableSimple> PLUG_BLOCKER = RegistrationUtilBC.registerItem(ITEMS,"plug_blocker",
            props -> new ItemPluggableSimple(props, BCTransportPlugs.blocker, null,
                    PluggableBlocker::boundingBoxFor));

    /** Power Adaptor Plug — allows MJ to pass into a pipe from adjacents. Only placeable on kinesis pipes. */
    public static final DeferredItem<ItemPluggableSimple> PLUG_POWER_ADAPTOR = RegistrationUtilBC.registerItem(ITEMS,"plug_power_adaptor",
            props -> new ItemPluggableSimple(props, BCTransportPlugs.powerAdaptor,
                    ItemPluggableSimple.PIPE_BEHAVIOUR_ACCEPTS_RS_POWER,
                    PluggablePowerAdaptor::boundingBoxFor));

    // -- Wire Items (one per DyeColor) --
    public static final Map<DyeColor, DeferredItem<ItemWire>> WIRE_ITEMS;
    static {
        Map<DyeColor, DeferredItem<ItemWire>> map = new java.util.EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            DyeColor c = color; // effectively final for lambda
            map.put(color, RegistrationUtilBC.registerItem(ITEMS,"wire_" + color.getName(),
                    props -> new ItemWire(props, c)));
        }
        WIRE_ITEMS = Collections.unmodifiableMap(map);
    }

    // -- Pipe Items --
    // Every pipe is the same ItemPipeHolder registration differing only by (registry id, PipeDefinition).
    // The named DeferredItem fields below are the public API — recipes (DyedPipeRecipe passes the holder
    // itself), the guide, creative tabs, and tests all reference them by name — so they stay, but the
    // repeated 3-line lambda body collapses into the pipe(id, def) helper. Registration order is preserved,
    // and each holder is recorded in PIPE_ITEMS so callers can look one up from its PipeDefinition (the
    // WIRE_ITEMS precedent, keyed by definition instead of DyeColor). Item ids do NOT match the definition
    // identifiers (e.g. item "pipe_cobble_item" vs definition "cobblestone_item"), so the id stays explicit.
    private static final Map<PipeDefinition, DeferredItem<ItemPipeHolder>> PIPE_ITEMS = new LinkedHashMap<>();

    // Structure
    public static final DeferredItem<ItemPipeHolder> PIPE_STRUCTURE = pipe("pipe_structure", BCTransportPipes.structure);

    // Item transport pipes
    public static final DeferredItem<ItemPipeHolder> PIPE_WOOD_ITEM = pipe("pipe_wood_item", BCTransportPipes.woodItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_COBBLE_ITEM = pipe("pipe_cobble_item", BCTransportPipes.cobbleItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_STONE_ITEM = pipe("pipe_stone_item", BCTransportPipes.stoneItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_QUARTZ_ITEM = pipe("pipe_quartz_item", BCTransportPipes.quartzItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_IRON_ITEM = pipe("pipe_iron_item", BCTransportPipes.ironItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_GOLD_ITEM = pipe("pipe_gold_item", BCTransportPipes.goldItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_CLAY_ITEM = pipe("pipe_clay_item", BCTransportPipes.clayItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_SANDSTONE_ITEM = pipe("pipe_sandstone_item", BCTransportPipes.sandstoneItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_VOID_ITEM = pipe("pipe_void_item", BCTransportPipes.voidItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_OBSIDIAN_ITEM = pipe("pipe_obsidian_item", BCTransportPipes.obsidianItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_ITEM = pipe("pipe_diamond_item", BCTransportPipes.diamondItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_WOOD_ITEM = pipe("pipe_diamond_wood_item", BCTransportPipes.diaWoodItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_LAPIS_ITEM = pipe("pipe_lapis_item", BCTransportPipes.lapisItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_DAIZULI_ITEM = pipe("pipe_daizuli_item", BCTransportPipes.daizuliItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_EMZULI_ITEM = pipe("pipe_emzuli_item", BCTransportPipes.emzuliItem);
    public static final DeferredItem<ItemPipeHolder> PIPE_STRIPES_ITEM = pipe("pipe_stripes_item", BCTransportPipes.stripesItem);

    // Fluid transport pipes
    public static final DeferredItem<ItemPipeHolder> PIPE_WOOD_FLUID = pipe("pipe_wood_fluid", BCTransportPipes.woodFluid);
    public static final DeferredItem<ItemPipeHolder> PIPE_COBBLE_FLUID = pipe("pipe_cobble_fluid", BCTransportPipes.cobbleFluid);
    public static final DeferredItem<ItemPipeHolder> PIPE_STONE_FLUID = pipe("pipe_stone_fluid", BCTransportPipes.stoneFluid);
    public static final DeferredItem<ItemPipeHolder> PIPE_QUARTZ_FLUID = pipe("pipe_quartz_fluid", BCTransportPipes.quartzFluid);
    public static final DeferredItem<ItemPipeHolder> PIPE_GOLD_FLUID = pipe("pipe_gold_fluid", BCTransportPipes.goldFluid);
    public static final DeferredItem<ItemPipeHolder> PIPE_IRON_FLUID = pipe("pipe_iron_fluid", BCTransportPipes.ironFluid);
    public static final DeferredItem<ItemPipeHolder> PIPE_CLAY_FLUID = pipe("pipe_clay_fluid", BCTransportPipes.clayFluid);
    public static final DeferredItem<ItemPipeHolder> PIPE_SANDSTONE_FLUID = pipe("pipe_sandstone_fluid", BCTransportPipes.sandstoneFluid);
    public static final DeferredItem<ItemPipeHolder> PIPE_VOID_FLUID = pipe("pipe_void_fluid", BCTransportPipes.voidFluid);
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_FLUID = pipe("pipe_diamond_fluid", BCTransportPipes.diamondFluid);
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_WOOD_FLUID = pipe("pipe_diamond_wood_fluid", BCTransportPipes.diaWoodFluid);

    // Power transport pipes
    public static final DeferredItem<ItemPipeHolder> PIPE_WOOD_POWER = pipe("pipe_wood_power", BCTransportPipes.woodPower);
    public static final DeferredItem<ItemPipeHolder> PIPE_COBBLE_POWER = pipe("pipe_cobble_power", BCTransportPipes.cobblePower);
    public static final DeferredItem<ItemPipeHolder> PIPE_STONE_POWER = pipe("pipe_stone_power", BCTransportPipes.stonePower);
    public static final DeferredItem<ItemPipeHolder> PIPE_QUARTZ_POWER = pipe("pipe_quartz_power", BCTransportPipes.quartzPower);
    public static final DeferredItem<ItemPipeHolder> PIPE_IRON_POWER = pipe("pipe_iron_power", BCTransportPipes.ironPower);
    public static final DeferredItem<ItemPipeHolder> PIPE_GOLD_POWER = pipe("pipe_gold_power", BCTransportPipes.goldPower);
    public static final DeferredItem<ItemPipeHolder> PIPE_SANDSTONE_POWER = pipe("pipe_sandstone_power", BCTransportPipes.sandstonePower);
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_POWER = pipe("pipe_diamond_power", BCTransportPipes.diamondPower);
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_WOOD_POWER = pipe("pipe_diamond_wood_power", BCTransportPipes.diaWoodPower);

    // RF/FE transport pipes
    public static final DeferredItem<ItemPipeHolder> PIPE_WOOD_RF = pipe("pipe_wood_rf", BCTransportPipes.woodRf);
    public static final DeferredItem<ItemPipeHolder> PIPE_COBBLE_RF = pipe("pipe_cobble_rf", BCTransportPipes.cobbleRf);
    public static final DeferredItem<ItemPipeHolder> PIPE_STONE_RF = pipe("pipe_stone_rf", BCTransportPipes.stoneRf);
    public static final DeferredItem<ItemPipeHolder> PIPE_QUARTZ_RF = pipe("pipe_quartz_rf", BCTransportPipes.quartzRf);
    public static final DeferredItem<ItemPipeHolder> PIPE_IRON_RF = pipe("pipe_iron_rf", BCTransportPipes.ironRf);
    public static final DeferredItem<ItemPipeHolder> PIPE_GOLD_RF = pipe("pipe_gold_rf", BCTransportPipes.goldRf);
    public static final DeferredItem<ItemPipeHolder> PIPE_SANDSTONE_RF = pipe("pipe_sandstone_rf", BCTransportPipes.sandstoneRf);
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_RF = pipe("pipe_diamond_rf", BCTransportPipes.diamondRf);
    public static final DeferredItem<ItemPipeHolder> PIPE_DIAMOND_WOOD_RF = pipe("pipe_diamond_wood_rf", BCTransportPipes.diaWoodRf);

    /** Registers one pipe BlockItem tying {@code def} to the shared pipe-holder block, records it in
     *  {@link #PIPE_ITEMS} for definition&rarr;item lookup, and returns the holder so the named field keeps
     *  its public identity. */
    private static DeferredItem<ItemPipeHolder> pipe(String id, PipeDefinition def) {
        DeferredItem<ItemPipeHolder> item = RegistrationUtilBC.registerItem(ITEMS, id,
                props -> new ItemPipeHolder(BCTransportBlocks.PIPE_HOLDER.get(), def, props).registerWithPipeApi());
        PIPE_ITEMS.put(def, item);
        return item;
    }

    /** @return the registered pipe item for {@code def}, or {@code null} if {@code def} has no item. */
    public static DeferredItem<ItemPipeHolder> getPipeItem(PipeDefinition def) {
        return PIPE_ITEMS.get(def);
    }

    /** All registered pipe items, in registration order. */
    public static Collection<DeferredItem<ItemPipeHolder>> allPipeItems() {
        return Collections.unmodifiableCollection(PIPE_ITEMS.values());
    }

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
    }
}
