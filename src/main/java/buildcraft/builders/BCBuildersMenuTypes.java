package buildcraft.builders;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import buildcraft.builders.container.ContainerArchitectTable;
import buildcraft.builders.container.ContainerBuilder;
import buildcraft.builders.container.ContainerElectronicLibrary;
import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.container.ContainerReplacer;

public class BCBuildersMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, BCBuilders.MODID);

    public static final Supplier<MenuType<ContainerFiller>> FILLER = MENU_TYPES.register(
            "filler",
            () -> IMenuTypeExtension.create(ContainerFiller::new));

    public static final Supplier<MenuType<ContainerBuilder>> BUILDER = MENU_TYPES.register(
            "builder",
            () -> IMenuTypeExtension.create(ContainerBuilder::new));

    public static final Supplier<MenuType<ContainerArchitectTable>> ARCHITECT = MENU_TYPES.register(
            "architect",
            () -> IMenuTypeExtension.create(ContainerArchitectTable::new));

    public static final Supplier<MenuType<ContainerElectronicLibrary>> LIBRARY = MENU_TYPES.register(
            "library",
            () -> IMenuTypeExtension.create(ContainerElectronicLibrary::new));

    public static final Supplier<MenuType<ContainerReplacer>> REPLACER = MENU_TYPES.register(
            "replacer",
            () -> IMenuTypeExtension.create(ContainerReplacer::new));

    public static void init(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
