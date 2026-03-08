/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.tile.item.ItemHandlerManager;

/**
 * Lightweight stub of the 1.12 TileBC_Neptune.
 * Provides only player tracking, owner tracking, and item manager hooks
 * needed by ContainerBCTile and the GUI layer. Full networking is deferred.
 */
public abstract class TileBC_Neptune extends BlockEntity {

    protected final ItemHandlerManager itemManager = new ItemHandlerManager(
        (handler, slot, before, after) -> this.setChanged()
    );

    private final Set<Player> usingPlayers = new HashSet<>();
    @Nullable
    private GameProfile owner;

    public TileBC_Neptune(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- Player tracking (used by ContainerBCTile) ---

    public void onPlayerOpen(Player player) {
        usingPlayers.add(player);
    }

    public void onPlayerClose(Player player) {
        usingPlayers.remove(player);
    }

    public boolean canInteractWith(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    // --- Owner tracking ---

    @Nullable
    public GameProfile getOwner() {
        return owner;
    }

    public void setOwner(@Nullable GameProfile owner) {
        this.owner = owner;
    }

    // --- GUI tick stub (networking deferred) ---

    /** Called by ContainerBCTile.broadcastChanges() — stub until networking is ported. */
    public void sendNetworkGuiTick(Player player) {
        // No-op until full networking is ported
    }

    // --- Item drops ---

    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
        itemManager.addDrops(toDrop);
    }
}
