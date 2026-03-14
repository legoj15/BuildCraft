package buildcraft.transport;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import buildcraft.transport.item.ItemPipeHolder;

public class BCTransportItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCTransport.MODID);

    // -- Non-pipe items --

    public static final DeferredItem<BlockItem> FILTERED_BUFFER = ITEMS.registerSimpleBlockItem(
            BCTransportBlocks.FILTERED_BUFFER);

    /** Pipe Sealant — used to craft fluid pipes from item pipes. */
    public static final DeferredItem<Item> WATERPROOF = ITEMS.registerItem("waterproof", Item::new);

    /** Plug — blocks a pipe face, preventing connections. */
    public static final DeferredItem<Item> PLUG_BLOCKER = ITEMS.registerItem("plug_blocker", Item::new);

    /** Power Adaptor Plug — allows MJ to pass into a pipe from adjacents. */
    public static final DeferredItem<Item> PLUG_POWER_ADAPTOR = ITEMS.registerItem("plug_power_adaptor", Item::new);

    // -- Pipe Items --
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
    public static final DeferredItem<Item> PIPE_DIAMOND_ITEM = ITEMS.registerItem("pipe_diamond_item", Item::new);
    public static final DeferredItem<Item> PIPE_DIAMOND_WOOD_ITEM = ITEMS.registerItem("pipe_diamond_wood_item", Item::new);
    public static final DeferredItem<Item> PIPE_LAPIS_ITEM = ITEMS.registerItem("pipe_lapis_item", Item::new);
    public static final DeferredItem<Item> PIPE_DAIZULI_ITEM = ITEMS.registerItem("pipe_daizuli_item", Item::new);
    public static final DeferredItem<Item> PIPE_EMZULI_ITEM = ITEMS.registerItem("pipe_emzuli_item", Item::new);
    public static final DeferredItem<Item> PIPE_STRIPES_ITEM = ITEMS.registerItem("pipe_stripes_item", Item::new);

    // Fluid transport pipes
    public static final DeferredItem<Item> PIPE_WOOD_FLUID = ITEMS.registerItem("pipe_wood_fluid", Item::new);
    public static final DeferredItem<Item> PIPE_COBBLE_FLUID = ITEMS.registerItem("pipe_cobble_fluid", Item::new);
    public static final DeferredItem<Item> PIPE_STONE_FLUID = ITEMS.registerItem("pipe_stone_fluid", Item::new);
    public static final DeferredItem<Item> PIPE_QUARTZ_FLUID = ITEMS.registerItem("pipe_quartz_fluid", Item::new);
    public static final DeferredItem<Item> PIPE_GOLD_FLUID = ITEMS.registerItem("pipe_gold_fluid", Item::new);
    public static final DeferredItem<Item> PIPE_IRON_FLUID = ITEMS.registerItem("pipe_iron_fluid", Item::new);
    public static final DeferredItem<Item> PIPE_CLAY_FLUID = ITEMS.registerItem("pipe_clay_fluid", Item::new);
    public static final DeferredItem<Item> PIPE_SANDSTONE_FLUID = ITEMS.registerItem("pipe_sandstone_fluid", Item::new);
    public static final DeferredItem<Item> PIPE_VOID_FLUID = ITEMS.registerItem("pipe_void_fluid", Item::new);
    public static final DeferredItem<Item> PIPE_DIAMOND_FLUID = ITEMS.registerItem("pipe_diamond_fluid", Item::new);
    public static final DeferredItem<Item> PIPE_DIAMOND_WOOD_FLUID = ITEMS.registerItem("pipe_diamond_wood_fluid", Item::new);

    // Power transport pipes
    public static final DeferredItem<Item> PIPE_WOOD_POWER = ITEMS.registerItem("pipe_wood_power", Item::new);
    public static final DeferredItem<Item> PIPE_COBBLE_POWER = ITEMS.registerItem("pipe_cobble_power", Item::new);
    public static final DeferredItem<Item> PIPE_STONE_POWER = ITEMS.registerItem("pipe_stone_power", Item::new);
    public static final DeferredItem<Item> PIPE_QUARTZ_POWER = ITEMS.registerItem("pipe_quartz_power", Item::new);
    public static final DeferredItem<Item> PIPE_IRON_POWER = ITEMS.registerItem("pipe_iron_power", Item::new);
    public static final DeferredItem<Item> PIPE_GOLD_POWER = ITEMS.registerItem("pipe_gold_power", Item::new);
    public static final DeferredItem<Item> PIPE_SANDSTONE_POWER = ITEMS.registerItem("pipe_sandstone_power", Item::new);
    public static final DeferredItem<Item> PIPE_DIAMOND_POWER = ITEMS.registerItem("pipe_diamond_power", Item::new);
    public static final DeferredItem<Item> PIPE_DIAMOND_WOOD_POWER = ITEMS.registerItem("pipe_diamond_wood_power", Item::new);

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
