/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Clearable;
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
public abstract class AbstractBCBlockEntity extends BlockEntity implements Clearable {

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
    // (markers, TileSpringOil) stay byte-identical. TilePipeHolder, whose placement flow is bespoke, also uses
    // this field. No subclass may declare an owner of its own (OwnerFieldShadowGuardTest): it would hide this
    // one, and the inherited setOwner/getOwner would disagree.

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

    // --- Non-player-removal drop hook ---
    // The block base's <1.21.10 onRemove catch-all (and, on >=1.21.10, AbstractBCSyncedBlockEntity's
    // preRemoveSideEffects) call this polymorphically on a non-player removal (explosion / piston /
    // /setblock ... destroy / mod tools). Default no-op — TileBC_Neptune (gated on spillsContentsOnRemoval)
    // and the raw fluid/engine tiles override it with their own spill logic; tiles that never spill inherit
    // this no-op, so the base's catch-all is safe to add uniformly (byte-identical for non-spilling tiles).

    /** Spills this tile's contents on a non-player removal. Default no-op. */
    public void dropContentsOnRemoval(net.minecraft.world.level.Level level, BlockPos pos) {}

    /**
     * Called once when this tile's block is removed for good (a player break, explosion, {@code /setblock}, a
     * different block set over it), from the same two sites as {@link #dropContentsOnRemoval}, but on every such
     * removal, player breaks included, and never for a chunk unload (unlike {@code setRemoved}). For tiles that keep
     * something alive outside themselves, such as a machine's owned volume box. A replace by command skips it on
     * {@code >=1.21.10} (the skip-side-effects flag), so whatever relies on it needs a fallback. Server side only.
     * Default no-op.
     */
    public void onBlockRemoved(net.minecraft.world.level.Level level, BlockPos pos) {}

    // --- Replace-by-command wipe hook ---
    // Vanilla's contract for a command or structure REPLACING a block entity: the contents vanish, they do not
    // spill (a replaced chest keeps nothing). On 1.21.1, /setblock (replace and keep), /fill, /clone and
    // structure placement call Clearable.tryClear on the old block entity and then swap the block with no
    // skip-side-effects flag, so the <1.21.10 onRemove catch-all would spill everything dropContentsOnRemoval
    // covers (and /clone ... move would copy it to the destination AND spill it at the source). The newer nodes
    // place with the skip flag (256) and never reach the catch-all, so nothing calls Clearable there at all.
    // /setblock ... destroy never calls it on any node: that mode is a deliberate break and keeps spilling.

    /**
     * Wipes what {@link #dropContentsOnRemoval} would spill, so a command replace spills nothing. A tile that
     * overrides the drop hook overrides this one over exactly the same contents (and the pipe holder over its
     * in-transit cargo); everything else inherits this no-op. Contents only: no ownership, configuration or
     * phantom (filter / template) slots, which a spill never returns either.
     */
    @Override
    public void clearContent() {}

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
