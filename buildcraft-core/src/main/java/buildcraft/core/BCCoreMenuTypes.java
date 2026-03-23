package buildcraft.core;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.core.list.ContainerList;

public class BCCoreMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, BCCore.MODID);

    public static final Supplier<MenuType<ContainerList>> LIST =
            MENU_TYPES.register("list",
                    () -> IMenuTypeExtension.create(ContainerList::fromNetwork));

    public static void init(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
