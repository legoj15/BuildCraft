package buildcraft.energy;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import buildcraft.energy.container.ContainerEngineStone;
import buildcraft.energy.container.ContainerEngineIron;

public class BCEnergyMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, BCEnergy.MODID);

    public static final Supplier<MenuType<ContainerEngineStone>> ENGINE_STONE = MENU_TYPES.register(
            "engine_stone",
            () -> IMenuTypeExtension.create(ContainerEngineStone::new));

    public static final Supplier<MenuType<ContainerEngineIron>> ENGINE_IRON = MENU_TYPES.register(
            "engine_iron",
            () -> IMenuTypeExtension.create(ContainerEngineIron::new));

    public static void init(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}

