/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.silicon.container.ContainerAdvancedCraftingTable;
import buildcraft.silicon.container.ContainerAssemblyTable;
import buildcraft.silicon.container.ContainerIntegrationTable;

public class BCSiliconMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, BCSilicon.MODID);

    public static final Supplier<MenuType<ContainerAssemblyTable>> ASSEMBLY_TABLE =
        MENU_TYPES.register("assembly_table", () -> IMenuTypeExtension.create(ContainerAssemblyTable::new));

    public static final Supplier<MenuType<ContainerIntegrationTable>> INTEGRATION_TABLE =
        MENU_TYPES.register("integration_table", () -> IMenuTypeExtension.create(ContainerIntegrationTable::new));

    public static final Supplier<MenuType<ContainerAdvancedCraftingTable>> ADVANCED_CRAFTING_TABLE =
        MENU_TYPES.register("advanced_crafting_table", () -> IMenuTypeExtension.create(ContainerAdvancedCraftingTable::new));

    public static void init(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
