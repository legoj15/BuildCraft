/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;


import buildcraft.platform.Platform;
import buildcraft.silicon.container.ContainerAdvancedCraftingTable;
import buildcraft.silicon.container.ContainerAssemblyTable;
import buildcraft.silicon.container.ContainerIntegrationTable;
import buildcraft.platform.registry.BCRegistrar;

public class BCSiliconMenuTypes {
    public static final BCRegistrar<MenuType<?>> MENU_TYPES =
        BCRegistrar.create(Registries.MENU, BCSilicon.MODID);

    public static final Supplier<MenuType<ContainerAssemblyTable>> ASSEMBLY_TABLE =
        MENU_TYPES.register("assembly_table", () -> Platform.menus().create(ContainerAssemblyTable::new));

    // Shipped alongside the Programming Table since Ph7 registered the robot integration recipe.
    public static final Supplier<MenuType<ContainerIntegrationTable>> INTEGRATION_TABLE =
        MENU_TYPES.register("integration_table", () -> Platform.menus().create(ContainerIntegrationTable::new));

    public static final Supplier<MenuType<ContainerAdvancedCraftingTable>> ADVANCED_CRAFTING_TABLE =
        MENU_TYPES.register("advanced_crafting_table", () -> Platform.menus().create(ContainerAdvancedCraftingTable::new));

    public static final Supplier<MenuType<buildcraft.silicon.container.ContainerGate>> GATE =
        MENU_TYPES.register("gate", () -> Platform.menus().create(buildcraft.silicon.container.ContainerGate::new));

    public static void init() {
        MENU_TYPES.subscribe();
    }
}
