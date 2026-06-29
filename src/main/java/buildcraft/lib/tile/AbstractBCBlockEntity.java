/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

import net.minecraft.core.BlockPos;
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
 * {@code getUpdatePacket}) — some tiles (e.g. {@code TileMarker}) sync through their own channels and
 * must keep vanilla's no-auto-sync default. Tiles that want the standard BE sync declare it
 * themselves (as {@link TileBC_Neptune} and the machine tiles already do).
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
}
