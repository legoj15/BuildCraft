/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.platform.LoaderHook;
import buildcraft.platform.Platform;

/**
 * A {@link MenuProvider} whose client-side container is rebuilt from opening data it writes itself
 * ({@link #writeOpeningData}), read back by the menu type's factory ({@code Platform.menus().create(...)}).
 *
 * <p>A block entity's GUI binds directly to the live tile, so its opening data is the tile's {@code BlockPos}
 * (the default) and the client container reads {@code buf.readBlockPos()} to locate it. A provider that is not a
 * block entity (the list item, the gate, the filter pipes) is built with {@link #of}, its writer supplying the data.
 *
 * <p>Open one with {@link #openFor}, which sends exactly {@link #writeOpeningData}'s bytes. The same data is also
 * written by the {@code writeClientSideData} hook below, because vanilla's <b>spectator</b> path bypasses the block
 * and calls {@code player.openMenu(blockEntity)} with no data writer; without the hook the client factory gets a
 * {@code null} buffer and {@code readBlockPos()} NPEs on the client network thread (the "Network Protocol Error"
 * disconnect a spectator hits on right-click). With it, a spectator opens the GUI read-only, matching how vanilla
 * treats chests and furnaces. {@link #openFor} bypasses the hook, so the data is never written twice.
 */
public interface IBCMenuProvider extends MenuProvider {

    /**
     * Writes the opening data the client container reads. The default writes the position of this provider, which
     * must then be a {@link BlockEntity}; any other provider overrides it.
     */
    default void writeOpeningData(RegistryFriendlyByteBuf buffer) {
        if (!(this instanceof BlockEntity blockEntity)) {
            throw new IllegalStateException(getClass().getName() + " is not a block entity and must override writeOpeningData");
        }
        buffer.writeBlockPos(blockEntity.getBlockPos());
    }

    /** Opens this provider's menu for {@code player} (server side only; a client player opens nothing). */
    default void openFor(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            Platform.menus().open(serverPlayer, this, this::writeOpeningData);
        }
    }

    // The spectator path described above relies on this hook.
    @LoaderHook("neoforge:IMenuProviderExtension#writeClientSideData")
    @Override
    default void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        writeOpeningData(buffer);
    }

    /** A provider that is not a block entity: {@code title}, {@code constructor}, and {@code writer}'s data. */
    static IBCMenuProvider of(Component title, MenuConstructor constructor, Consumer<RegistryFriendlyByteBuf> writer) {
        return new IBCMenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return constructor.createMenu(containerId, inventory, player);
            }

            @Override
            public void writeOpeningData(RegistryFriendlyByteBuf buffer) {
                writer.accept(buffer);
            }
        };
    }
}
