/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.misc.MessageUtil;

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

    // --- No-re-mesh client state push ---

    /**
     * Pushes this tile's current state to every client tracking its chunk WITHOUT re-meshing the chunk
     * section, by sending only the {@link #getUpdatePacket() ClientboundBlockEntityDataPacket} via
     * {@link MessageUtil#sendUpdateToTrackingPlayers}. This is the correct tool — <em>never</em>
     * {@link net.minecraft.world.level.Level#sendBlockUpdated} — for a pure block-entity DATA change
     * whose dynamic visuals are drawn by a {@code BlockEntityRenderer} (the engines, whose whole body is
     * BER-drawn from tile fields; the quarry/architect/filler beams and LED overlays) rather than by the
     * static baked block model. {@code sendBlockUpdated} broadcasts the same BE packet <em>plus</em> a
     * blockstate change that makes every tracking client rebuild the whole chunk section's geometry —
     * for these tiles that rebuild is pure waste (their block model never changes) and a severe FPS cost
     * near a busy machine. When the baked model genuinely changes (a pipe's connections/pluggables, a
     * facing/texture-swap blockstate) the re-mesh IS the product — keep {@code sendBlockUpdated} there.
     *
     * <p>The helper lives on <em>this</em> synced base, not the plain {@link AbstractBCBlockEntity}: it
     * only does anything on a tile that produces an update packet, which is exactly what this class adds.
     * On the plain base vanilla's {@code getUpdatePacket()} returns {@code null}, so the push would be a
     * silent no-op — a footgun. Server-side only; {@code sendUpdateToTrackingPlayers} no-ops on the client.
     *
     * <p>{@link #markForRenderUpdate()} and {@link #markForGuiUpdate()} are mechanically IDENTICAL today —
     * both push the full update tag to all chunk-tracking players. The two names exist only to record the
     * call site's intent: a {@code BlockEntityRenderer} needing to redraw vs. a currently-open container
     * menu needing fresh data. Keeping the split leaves room to narrow the GUI push to menu-openers later
     * without churning call sites.
     */
    public void markForRenderUpdate() {
        MessageUtil.sendUpdateToTrackingPlayers(this);
    }

    /**
     * Push fresh block-entity data to tracking clients without a chunk re-mesh, for a data change that
     * feeds an open container GUI (a recipe/progress readout, an owner ledger). Mechanically identical to
     * {@link #markForRenderUpdate()} today — see that method for the full contract and rationale.
     */
    public void markForGuiUpdate() {
        MessageUtil.sendUpdateToTrackingPlayers(this);
    }
}
