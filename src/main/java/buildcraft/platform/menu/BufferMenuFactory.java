/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Builds the client half of a menu from the opening data the server wrote (see {@link BCMenus#open}). It has the
 * shape of a BuildCraft container's network constructor, so {@code ContainerFoo::new} fits.
 */
@FunctionalInterface
public interface BufferMenuFactory<T extends AbstractContainerMenu> {
    /**
     * @param buf the opening data, positioned at its start. It is {@code null} only if a menu of this type is opened
     *            with no data at all, which BuildCraft never does.
     */
    T create(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf);
}
