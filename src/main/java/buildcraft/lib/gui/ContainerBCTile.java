/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.lib.tile.AbstractBCBlockEntity;

/**
 * Base for every tile-backed BuildCraft menu (System A). Bound to {@link AbstractBCBlockEntity} — the
 * thin base shared by BOTH {@code TileBC_Neptune} and the raw machine tiles (engines, fluid machines,
 * dynamo) — so those raw tiles no longer need to re-roll their own {@code tile} field, a per-container
 * {@code getTile(Inventory, FriendlyByteBuf)} resolver, and a drifted {@code stillValid}. Reach +
 * staleness is delegated to {@link AbstractBCBlockEntity#canInteractWith(Player)}.
 *
 * <p>The {@code tile} may be {@code null} (the client-side resolver returns {@code null} if the block
 * entity isn't present); every access here is null-guarded and {@link #stillValid} closes the menu.
 */
public abstract class ContainerBCTile<T extends AbstractBCBlockEntity> extends ContainerBC_Neptune {
    @Nullable
    public final T tile;

    public ContainerBCTile(MenuType<?> menuType, int containerId, Player player, @Nullable T tile) {
        super(menuType, containerId, player);
        this.tile = tile;
        if (tile != null && tile.getLevel() != null && !tile.getLevel().isClientSide()) {
            tile.onPlayerOpen(player);
        }
    }

    /**
     * Shared client-side tile resolver: reads a {@link BlockPos} off the buffer (written by
     * {@code IBCMenuProvider.writeClientSideData}) and returns the block entity at that position if it
     * matches {@code clazz}, else {@code null}. Replaces the per-container {@code getTile} statics.
     */
    @Nullable
    protected static <T extends AbstractBCBlockEntity> T resolveTile(Inventory playerInv, FriendlyByteBuf buf, Class<T> clazz) {
        BlockPos pos = buf.readBlockPos();
        Level level = playerInv.player.level();
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (clazz.isInstance(be)) {
                return clazz.cast(be);
            }
        }
        return null;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (tile != null) {
            tile.onPlayerClose(player);
        }
    }

    @Override
    public final boolean stillValid(Player player) {
        return tile != null && tile.canInteractWith(player);
    }
}
