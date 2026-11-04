/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import buildcraft.core.list.ContainerList;
import buildcraft.platform.Platform;
import buildcraft.platform.registry.BCRegistrar;

public class BCCoreMenuTypes {
    public static final BCRegistrar<MenuType<?>> MENU_TYPES =
            BCRegistrar.create(Registries.MENU, BCCore.MODID);

    public static final Supplier<MenuType<ContainerList>> LIST =
            MENU_TYPES.register("list",
                    () -> Platform.menus().create(ContainerList::fromNetwork));

    public static void init() {
        MENU_TYPES.subscribe();
    }
}
