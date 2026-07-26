/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.transport.container.ContainerDiamondPipe;
import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.container.ContainerEmzuliPipe;
import buildcraft.transport.container.ContainerFilteredBuffer;

public class BCTransportMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, BCTransport.MODID);

    public static final Supplier<MenuType<ContainerFilteredBuffer>> FILTERED_BUFFER =
            MENU_TYPES.register("filtered_buffer",
                    () -> IMenuTypeExtension.create(ContainerFilteredBuffer::new));

    public static final Supplier<MenuType<ContainerDiamondPipe>> DIAMOND_PIPE =
            MENU_TYPES.register("diamond_pipe",
                    () -> IMenuTypeExtension.create(ContainerDiamondPipe::new));

    public static final Supplier<MenuType<ContainerDiamondWoodPipe>> DIAMOND_WOOD_PIPE =
            MENU_TYPES.register("diamond_wood_pipe",
                    () -> IMenuTypeExtension.create(ContainerDiamondWoodPipe::new));

    public static final Supplier<MenuType<ContainerEmzuliPipe>> EMZULI_PIPE =
            MENU_TYPES.register("emzuli_pipe",
                    () -> IMenuTypeExtension.create(ContainerEmzuliPipe::new));

    public static void init(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
