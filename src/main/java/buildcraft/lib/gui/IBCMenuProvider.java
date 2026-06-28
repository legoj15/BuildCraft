/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * A {@link MenuProvider} backed by a block entity whose client-side container constructor rebuilds the
 * tile from a {@link BlockPos} read off the open-screen buffer.
 *
 * <p>BuildCraft GUIs bind directly to the live tile, so the client menu factory (registered via
 * {@code IMenuTypeExtension.create(...)}) reads {@code buf.readBlockPos()} to locate it. A block's normal
 * right-click supplies that pos through {@code player.openMenu(tile, pos)} — but vanilla's <b>spectator</b>
 * interaction path bypasses the block entirely and calls the single-arg {@code player.openMenu(menuProvider)}
 * with no data writer. That hands the client factory a {@code null} buffer, and {@code readBlockPos()} then
 * NPEs on the client network thread — the "Network Protocol Error" disconnect a spectator hits on right-click.
 *
 * <p>{@link #writeClientSideData} is invoked on <i>every</i> open path, including the spectator one, so writing
 * the pos here guarantees the client factory always receives it. Any block-entity menu whose client ctor reads
 * a {@code BlockPos} should implement this instead of raw {@link MenuProvider} to stay spectator-safe; opening
 * the GUI then works read-only for spectators, matching how vanilla treats chests and furnaces.
 */
public interface IBCMenuProvider extends MenuProvider {
    /** Supplied by {@link net.minecraft.world.level.block.entity.BlockEntity#getBlockPos()}. */
    BlockPos getBlockPos();

    @Override
    default void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }
}
