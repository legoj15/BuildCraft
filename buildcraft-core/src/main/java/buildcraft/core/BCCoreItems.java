package buildcraft.core;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EquipmentSlot;
import buildcraft.core.item.ItemGoggles;
import buildcraft.core.item.ItemWrench_Neptune;

public class BCCoreItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCCore.MODID);

    public static final DeferredItem<ItemWrench_Neptune> WRENCH = ITEMS.registerItem("wrench",
            ItemWrench_Neptune::new, props -> props.stacksTo(1));

    public static final DeferredItem<ItemGoggles> GOGGLES = ITEMS.registerItem("goggles",
            ItemGoggles::new, props -> props.stacksTo(1).durability(0).equippable(EquipmentSlot.HEAD));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> SPRING = ITEMS
            .registerSimpleBlockItem("spring", BCCoreBlocks.SPRING);

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static void preInit() {
    }
}
