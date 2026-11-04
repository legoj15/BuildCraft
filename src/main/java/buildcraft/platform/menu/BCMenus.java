/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.menu;

import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Menus whose client half needs opening data: the menu type (registered through the ordinary menu registry) and
 * opening it with that data. The data is a raw writer onto a {@link RegistryFriendlyByteBuf}, not a typed value, so
 * the bytes on the wire stay exactly what the containers' network constructors read.
 *
 * <p>This is a contract (see the package documentation of {@code buildcraft.platform}): JDK and vanilla types only.
 */
public interface BCMenus {

    /** A menu type whose client half is built by {@code factory} from the opening data. */
    <T extends AbstractContainerMenu> MenuType<T> create(BufferMenuFactory<T> factory);

    /**
     * Opens {@code provider}'s menu for {@code player}, sending {@code writer}'s bytes as the opening data the
     * type's {@link BufferMenuFactory} reads. The writer is the whole opening data: a loader-side data hook on the
     * provider itself (NeoForge's {@code writeClientSideData}) is not run as well, so nothing is written twice. A
     * fake player opens nothing.
     */
    void open(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer);
}
