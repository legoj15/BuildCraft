/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The {@link AbstractBCBlockEntity} flavour that also carries the standard client-sync pair —
 * {@code getUpdateTag}/{@code getUpdatePacket} plus the {@code <1.21.10} {@code onDataPacket}
 * unconditional-apply fix. Extend this for tiles that push their full saved NBT to the client on every
 * block update and let vanilla's {@link net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket}
 * carry it: {@link TileBC_Neptune} and the raw machine tiles (engines + {@code TileDynamoMJ}, the fluid
 * machines {@code TileTank}/{@code TileDistiller_BC8}/{@code TileHeatExchange}/{@code TileFloodGate}, and
 * {@code TileLaser}).
 *
 * <p>Isolating the pair here collapses ~7 byte-identical copies to one, and retroactively gives every
 * subclass the 1.21.1-node {@code onDataPacket} fix (previously present on only some of them).
 *
 * <p>Tiles that sync through their OWN channel ({@code TileMarker} via its cache, {@code TilePipeHolder}
 * via a per-pluggable sub-tag), or do not client-sync at all ({@code TileSpringOil}), extend the plain
 * {@link AbstractBCBlockEntity} instead so they keep vanilla's no-auto-sync default.
 */
public abstract class AbstractBCSyncedBlockEntity extends AbstractBCBlockEntity {

    @SuppressWarnings("this-escape")
    public AbstractBCSyncedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- Network sync ---

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    //? if <1.21.10 {
    /*// 1.21.1's IBlockEntityExtension.onDataPacket only applies the update tag when it is NON-empty.
    // A tile that serialises to an EMPTY tag in some state (e.g. a tank drained empty, an engine that
    // cleared its buffer) would then never have that state applied on the client — it keeps showing
    // the last non-empty contents until the chunk reloads. Apply the tag unconditionally, matching
    // 26.1.2 (whose onDataPacket has no such guard). 1.21.1-only.
    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt,
            net.minecraft.core.HolderLookup.Provider registries) {
        loadWithComponents(pkt.getTag(), registries);
    }*/
    //?}
}
