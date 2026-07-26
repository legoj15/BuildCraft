/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
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
 * {@code IMenuTypeExtension.create(...)}) reads {@code buf.readBlockPos()} to locate it. Because
 * {@link #writeClientSideData} runs on <i>every</i> open path — the block's own single-arg
 * {@code player.openMenu(tile)} AND vanilla's <b>spectator</b> path, which bypasses the block and calls
 * {@code player.openMenu(menuProvider)} with no data writer — writing the pos here makes it the single uniform
 * source. Blocks therefore open these tiles with the <em>one-arg</em> {@code openMenu(tile)}: do NOT also route
 * the pos through the {@code openMenu(tile, pos)} / {@code openMenu(tile, writer)} overloads, whose writer runs
 * <i>after</i> {@code writeClientSideData} and would append a redundant SECOND {@code BlockPos} the client never
 * reads.
 *
 * <p>Without this, the spectator path hands the client factory a {@code null} buffer and {@code readBlockPos()}
 * NPEs on the client network thread — the "Network Protocol Error" disconnect a spectator hits on right-click.
 * Any block-entity menu whose client ctor reads a {@code BlockPos} should implement this instead of raw
 * {@link MenuProvider} to stay spectator-safe; opening the GUI then works read-only for spectators, matching how
 * vanilla treats chests and furnaces.
 */
public interface IBCMenuProvider extends MenuProvider {
    /** Supplied by {@link net.minecraft.world.level.block.entity.BlockEntity#getBlockPos()}. */
    BlockPos getBlockPos();

    @Override
    default void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }
}
