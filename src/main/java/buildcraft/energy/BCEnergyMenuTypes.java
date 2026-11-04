/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;

import java.util.function.Supplier;

import buildcraft.energy.container.ContainerEngineStone;
import buildcraft.energy.container.ContainerEngineIron;
import buildcraft.energy.container.ContainerEngineFE;
import buildcraft.energy.container.ContainerDynamoMJ;
import buildcraft.platform.Platform;
import buildcraft.platform.registry.BCRegistrar;

public class BCEnergyMenuTypes {
    public static final BCRegistrar<MenuType<?>> MENU_TYPES =
            BCRegistrar.create(Registries.MENU, "buildcraftunofficial");

    public static final Supplier<MenuType<ContainerEngineStone>> ENGINE_STONE = MENU_TYPES.register(
            "engine_stone",
            () -> Platform.menus().create(ContainerEngineStone::new));

    public static final Supplier<MenuType<ContainerEngineIron>> ENGINE_IRON = MENU_TYPES.register(
            "engine_iron",
            () -> Platform.menus().create(ContainerEngineIron::new));

    public static final Supplier<MenuType<ContainerEngineFE>> ENGINE_FE = MENU_TYPES.register(
            "engine_rf",
            () -> Platform.menus().create(ContainerEngineFE::new));

    public static final Supplier<MenuType<ContainerDynamoMJ>> DYNAMO_MJ = MENU_TYPES.register(
            "mj_dynamo",
            () -> Platform.menus().create(ContainerDynamoMJ::new));

    public static void init(IEventBus modEventBus) {
        MENU_TYPES.subscribe();
    }
}

