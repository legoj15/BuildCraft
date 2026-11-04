/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import buildcraft.builders.container.ContainerArchitectTable;
import buildcraft.builders.container.ContainerBuilder;
import buildcraft.builders.container.ContainerElectronicLibrary;
import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.container.ContainerReplacer;
import buildcraft.platform.Platform;

public class BCBuildersMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, BCBuilders.MODID);

    public static final Supplier<MenuType<ContainerFiller>> FILLER = MENU_TYPES.register(
            "filler",
            () -> Platform.menus().create(ContainerFiller::new));

    public static final Supplier<MenuType<ContainerBuilder>> BUILDER = MENU_TYPES.register(
            "builder",
            () -> Platform.menus().create(ContainerBuilder::new));

    public static final Supplier<MenuType<ContainerArchitectTable>> ARCHITECT = MENU_TYPES.register(
            "architect",
            () -> Platform.menus().create(ContainerArchitectTable::new));

    public static final Supplier<MenuType<ContainerElectronicLibrary>> LIBRARY = MENU_TYPES.register(
            "library",
            () -> Platform.menus().create(ContainerElectronicLibrary::new));

    public static final Supplier<MenuType<ContainerReplacer>> REPLACER = MENU_TYPES.register(
            "replacer",
            () -> Platform.menus().create(ContainerReplacer::new));

    public static void init(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
