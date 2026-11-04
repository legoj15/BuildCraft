/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;

import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.factory.container.ContainerChute;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.factory.container.ContainerHeatExchange;
import buildcraft.factory.container.ContainerTank;
import buildcraft.platform.Platform;
import buildcraft.platform.registry.BCRegistrar;

public class BCFactoryMenuTypes {
    public static final BCRegistrar<MenuType<?>> MENU_TYPES =
            BCRegistrar.create(Registries.MENU, BCFactory.MODID);

    public static final Supplier<MenuType<ContainerAutoCraftItems>> AUTO_WORKBENCH_ITEMS =
            MENU_TYPES.register("auto_workbench_items",
                    () -> Platform.menus().create(ContainerAutoCraftItems::new));

    public static final Supplier<MenuType<ContainerTank>> TANK =
            MENU_TYPES.register("tank",
                    () -> Platform.menus().create(ContainerTank::new));

    public static final Supplier<MenuType<ContainerChute>> CHUTE =
            MENU_TYPES.register("chute",
                    () -> Platform.menus().create(ContainerChute::new));

    public static final Supplier<MenuType<ContainerDistiller>> DISTILLER =
            MENU_TYPES.register("distiller",
                    () -> Platform.menus().create(ContainerDistiller::new));

    public static final Supplier<MenuType<ContainerHeatExchange>> HEAT_EXCHANGE =
            MENU_TYPES.register("heat_exchange",
                    () -> Platform.menus().create(ContainerHeatExchange::new));

    public static void init(IEventBus modEventBus) {
        MENU_TYPES.subscribe();
    }
}
