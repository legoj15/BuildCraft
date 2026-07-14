/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
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

/**
 * The thin base shared by ALL BuildCraft block entities — both {@link TileBC_Neptune} (the
 * heavyweight base carrying item-handler/owner/player machinery) and the tiles that extend vanilla
 * {@link BlockEntity} directly for their own bespoke state (engines, the fluid machines, the pipe
 * holder, markers, …).
 *
 * <p>It exists to hold the ONE piece of boilerplate every BC tile was previously re-copying: the
 * {@code saveAdditional}/{@code loadAdditional} <em>signature</em> directive, which differs across
 * the MC-1.21.5 API cliff (ValueOutput/ValueInput on 1.21.10+, CompoundTag + HolderLookup.Provider
 * on 1.21.1). Isolating it here collapses ~10 byte-identical copies to one maintenance point.
 * Subclasses override the version-neutral {@link #writeData}/{@link #readData} hooks instead of the
 * platform methods, so their serialization code carries no directives at all.
 *
 * <p>This base deliberately does NOT impose a client-sync pair ({@code getUpdateTag}/
 * {@code getUpdatePacket}) — some tiles (e.g. {@code TileMarker}, {@code TileSpringOil},
 * {@code TilePipeHolder}) sync through their own channels or not at all, and must keep vanilla's
 * no-auto-sync default. Tiles that want the standard BE sync extend the
 * {@link AbstractBCSyncedBlockEntity} subclass, which adds that pair (plus the {@code <1.21.10}
 * {@code onDataPacket} fix) in one place — {@link TileBC_Neptune} and the machine tiles do.
 */
public abstract class AbstractBCBlockEntity extends BlockEntity {

    @SuppressWarnings("this-escape")
    public AbstractBCBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // Platform bridge: vanilla's BlockEntity load/save signature differs across the MC-1.21.5 cliff.
    // It is isolated to THIS file; subclasses override the version-neutral writeData/readData hooks below.
    //? if >=1.21.10 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        writeData(new BCValueOutput(output));
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        readData(new BCValueInput(input));
    }
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(new BCValueOutput(tag));
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readData(new BCValueInput(tag));
    }*/
    //?}

    /** Version-neutral write hook. Subclasses override this (NOT saveAdditional) and call {@code super.writeData(out)}. */
    protected void writeData(BCValueOutput out) {}

    /** Version-neutral read hook. Subclasses override this (NOT loadAdditional) and call {@code super.readData(in)}. */
    protected void readData(BCValueInput in) {}

    // --- Owner tracking ---
    // The owner field + placement entry point shared by TileBC_Neptune and the raw machine tiles. The
    // ownerUUID/ownerName NBT itself is handled by OwnerData, which owner-bearing subclasses call from
    // their writeData/readData — this base deliberately does NOT serialize owner, so ownerless tiles
    // (markers, TileSpringOil) stay byte-identical. Tiles with a bespoke placement flow
    // (e.g. TilePipeHolder) keep their own owner storage.

    @Nullable
    protected GameProfile owner;

    @Nullable
    public GameProfile getOwner() {
        return owner;
    }

    public void setOwner(@Nullable GameProfile owner) {
        this.owner = owner;
    }

    /**
     * Records the placing player as this tile's owner (called by block classes from {@code setPlacedBy}).
     * Marks the chunk dirty so the owner persists even if the tile is never otherwise mutated before the
     * chunk unloads (the setChanged() guard added in commit 9176336eb).
     */
    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player) {
            setOwner(player.getGameProfile());
            setChanged();
        }
    }

    /** Convenience for blocks whose {@code setPlacedBy} doesn't forward the placed stack. */
    public void onPlacedBy(@Nullable LivingEntity placer) {
        onPlacedBy(placer, ItemStack.EMPTY);
    }

    // --- Player tracking + GUI reach (used by ContainerBCTile / ContainerBCCrafting) ---
    // Lives here (not on TileBC_Neptune) so the container base can bind its generic to
    // AbstractBCBlockEntity — the raw machine tiles (engines, fluid machines, dynamo) that skip the
    // heavyweight TileBC_Neptune still get a menu that tracks openers and validates reach.

    private final Set<Player> usingPlayers = new HashSet<>();

    public void onPlayerOpen(Player player) {
        usingPlayers.add(player);
    }

    public void onPlayerClose(Player player) {
        usingPlayers.remove(player);
    }

    /**
     * Whether {@code player} may keep this tile's menu open: the tile must still be the live block
     * entity at its position AND within reach (8 blocks). This unifies the two staleness checks that
     * had drifted apart across the raw-tile menus — the fluid machines only checked
     * {@code getBlockEntity(pos) != this}; the engines only checked {@code isRemoved()} — onto the
     * stronger combined guard (both, plus the reach test), so a menu closes whether the tile is
     * removed, replaced by a different BE at the same position, or the player walked out of range.
     */
    public boolean canInteractWith(Player player) {
        if (isRemoved() || level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
            worldPosition.getX() + 0.5,
            worldPosition.getY() + 0.5,
            worldPosition.getZ() + 0.5
        ) <= 64.0;
    }
}
