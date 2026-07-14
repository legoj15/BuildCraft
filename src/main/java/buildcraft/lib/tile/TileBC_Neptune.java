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

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.tile.item.IBCItemHandler;
import buildcraft.lib.tile.item.ItemHandlerManager;

/**
 * Lightweight stub of the 1.12 TileBC_Neptune.
 * Provides player tracking, owner tracking (with persistence), and item manager hooks
 * needed by ContainerBCTile and the GUI layer. Full networking is deferred.
 */
public abstract class TileBC_Neptune extends AbstractBCSyncedBlockEntity {

    protected final ItemHandlerManager itemManager = new ItemHandlerManager(
        (handler, slot, before, after) -> this.setChanged()
    );

    private final Set<Player> usingPlayers = new HashSet<>();

    /** Set once this tile's contents have been spilled for the current removal, so the non-player
     *  {@link #dropContentsOnRemoval} fallback can't drop them twice — the player-break path drops
     *  in playerWillDestroy, which the subsequent level.removeBlock re-triggers this hook after. */
    private boolean contentsDropped = false;

    @SuppressWarnings("this-escape")
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

    // --- Owner persistence ---
    // The owner field, getOwner/setOwner and onPlacedBy live on AbstractBCBlockEntity; here we only
    // persist it via OwnerData. The saveAdditional/loadAdditional signature directive lives once in
    // AbstractBCBlockEntity too, so we override the version-neutral writeData/readData hooks.
    @Override
    protected void writeData(BCValueOutput out) {
        super.writeData(out);
        OwnerData.writeOwner(out, owner);
    }

    @Override
    protected void readData(BCValueInput in) {
        super.readData(in);
        owner = OwnerData.readOwner(in);
    }

    // --- Item drops ---

    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
        itemManager.addDrops(toDrop);
    }

    /** Tanks whose fluid should spill (as fragile fluid-shard items) when this tile is removed by
     *  any means. Default none; fluid tiles override. Only consulted when
     *  {@link #spillsContentsOnRemoval()} returns true. */
    @Nullable
    protected buildcraft.lib.fluid.BCFluidTank[] getDropTanks() {
        return null;
    }

    /** Whether this tile spills its inventory/tank contents when removed by NON-player means
     *  (explosion, piston, /setblock, mod tools) — restoring the universal drop that 1.12.2's
     *  breakBlock&rarr;onRemove&rarr;addDrops gave every tile. Opt-in (default false) so tiles with
     *  bespoke drop logic aren't double-dropped; flip to true once a tile's full content set is
     *  covered by {@link #addDrops} + {@link #getDropTanks}. The matching block must also call
     *  {@link #markDropsHandled} in playerWillDestroy and route its &lt;1.21.10 onRemove here. */
    protected boolean spillsContentsOnRemoval() {
        return false;
    }

    /** Flags that the player-break path has already handled this removal's drops, so the non-player
     *  {@link #dropContentsOnRemoval} fallback stays quiet (no double drop). */
    public void markDropsHandled() {
        contentsDropped = true;
    }

    /** Spills this tile's inventory (via {@link #addDrops}, skipping phantom slots) and any
     *  {@link #getDropTanks} fluid as fragile shards when removed by non-player means. No-op on the
     *  client, for opted-out tiles, and once drops have been handled (idempotent + guards against the
     *  player path, which drops in playerWillDestroy then re-fires this via level.removeBlock). */
    public void dropContentsOnRemoval(net.minecraft.world.level.Level level, BlockPos pos) {
        if (contentsDropped || level.isClientSide() || !spillsContentsOnRemoval()) {
            return;
        }
        contentsDropped = true;
        buildcraft.lib.misc.BlockDropsUtil.dropTileContents(level, pos, this, getDropTanks());
        dropExtraContentsOnRemoval(level, pos);
    }

    /** Override to spill contents NOT held in the itemManager or {@link #getDropTanks} — e.g. loose
     *  ItemStack fields a tile keeps outside ItemHandlerManager (the Builder's snapshot + resource grid).
     *  Called from {@link #dropContentsOnRemoval} after the itemManager + tank drops, so it only fires
     *  for opted-in tiles on a non-player removal. Mirror the tile's playerWillDestroy drop set exactly. */
    protected void dropExtraContentsOnRemoval(net.minecraft.world.level.Level level, BlockPos pos) {}

    // Non-player removal catch-all (explosion / piston / /setblock / mod tools) on the >=1.21.10 API:
    // the BlockEntity is still alive here (removed right after, before affectNeighborsAfterRemoval).
    // Player breaks set contentsDropped via markDropsHandled in playerWillDestroy, so this is a no-op there.
    //? if >=1.21.10 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            dropContentsOnRemoval(level, pos);
        }
    }
    //?}

    @Nullable
    public IBCItemHandler getItemHandler(net.minecraft.core.Direction facing) {
        return itemManager.getItemHandler(facing);
    }

    // --- Network Sync ---
    // The standard getUpdateTag/getUpdatePacket pair (+ the <1.21.10 onDataPacket fix) lives once in
    // AbstractBCSyncedBlockEntity, which this class extends.
}
