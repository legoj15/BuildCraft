package buildcraft.factory;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.factory.container.ContainerAutoCraftItems;

public class BCFactoryMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, BCFactory.MODID);

    public static final Supplier<MenuType<ContainerAutoCraftItems>> AUTO_WORKBENCH_ITEMS =
            MENU_TYPES.register("auto_workbench_items",
                    () -> IMenuTypeExtension.create(ContainerAutoCraftItems::new));

    public static void init(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
