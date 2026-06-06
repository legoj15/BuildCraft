/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render.pip;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeEvent;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.client.model.key.PipeModelKey;
import buildcraft.transport.pipe.Pipe;

/**
 * Reconstructs a pipe's render model offline — from a blueprint's captured block-entity NBT, with
 * no live world — so the snapshot 3D preview ({@link BlueprintPipRenderer}) can draw each pipe with
 * its correct type, paint colour, and connection arms.
 * <p>
 * Every pipe shares one {@code pipe_holder} block, so the preview's old {@code new ItemStack(block)}
 * path resolved every pipe to whichever {@code ItemPipeHolder} last won the block→item map (the
 * Wooden Diamond FE Pipe) and drew its connectionless vertical <i>item</i> model. Instead we read
 * the captured pipe NBT (nested under {@code "pipe"} in the tile NBT — see
 * {@code TilePipeHolder.saveAdditional}), build a {@link Pipe} against a render-only stub holder,
 * and ask it for its {@link PipeModelKey}. The {@link Pipe} constructor restores the pipe's
 * scan-time connection state from the packed {@code "con"} field, so {@code getModel()} yields the
 * real per-side connection distances — which {@code ModelPipe} then turns into the same block-model
 * quads the pipe shows in-world.
 */
public final class PipePreviewModel {

    private PipePreviewModel() {}

    /** True if this blockstate is a pipe (the single shared pipe-holder block). */
    public static boolean isPipe(BlockState state) {
        return state != null && state.getBlock() instanceof BlockPipeHolder;
    }

    /**
     * Builds the {@link PipeModelKey} for a pipe cell from its captured tile NBT, or {@code null}
     * if the NBT carries no usable pipe data (missing/unknown definition, malformed payload). The
     * caller falls back to its generic path on null, so a bad capture degrades to "wrong model"
     * rather than a crash.
     */
    @Nullable
    public static PipeModelKey modelKey(@Nullable CompoundTag tileNbt) {
        if (tileNbt == null) {
            return null;
        }
        CompoundTag pipeNbt = NBTUtilBC.getCompound(tileNbt, "pipe");
        if (pipeNbt.isEmpty()) {
            return null;
        }
        try {
            Pipe pipe = new Pipe(STUB_HOLDER, pipeNbt);
            return pipe.getModel();
        } catch (Throwable t) {
            // InvalidInputDataException (unknown def) or any incidental failure reconstructing the
            // pipe offline — skip the bespoke render and let the caller fall back.
            return null;
        }
    }

    /**
     * A do-nothing {@link IPipeHolder} good enough to construct a {@link Pipe} (plus its behaviour
     * and flow) and read its model. Pipe/behaviour/flow constructors only stash the holder
     * reference — world access happens later, during ticking/connection updates, which the preview
     * never triggers — so every method here can safely return a null/empty/no-op default. Keeping
     * it free of any {@code net.minecraft.client} reference lets the model-key path be exercised on
     * a dedicated server (the {@code pipe_preview_model_key} game test); {@link #modelKey} wraps the
     * whole reconstruction in a catch so a pipe type that unexpectedly touches the world degrades to
     * the caller's fallback rather than crashing.
     */
    private static final IPipeHolder STUB_HOLDER = new IPipeHolder() {
        @Override
        public Level getPipeWorld() {
            return null;
        }

        @Override
        public BlockPos getPipePos() {
            return BlockPos.ZERO;
        }

        @Override
        public BlockEntity getPipeTile() {
            return null;
        }

        @Override
        public IPipe getPipe() {
            return null;
        }

        @Override
        public boolean canPlayerInteract(Player player) {
            return false;
        }

        @Override
        public PipePluggable getPluggable(Direction side) {
            return null;
        }

        @Override
        public BlockEntity getNeighbourTile(Direction side) {
            return null;
        }

        @Override
        public IPipe getNeighbourPipe(Direction side) {
            return null;
        }

        @Override
        public <T> T getCapabilityFromPipe(Direction side, Object capability) {
            return null;
        }

        @Override
        public IWireManager getWireManager() {
            return null;
        }

        @Override
        public GameProfile getOwner() {
            return null;
        }

        @Override
        public boolean fireEvent(PipeEvent event) {
            return false;
        }

        @Override
        public void scheduleRenderUpdate() {}

        @Override
        public void scheduleNetworkUpdate(PipeMessageReceiver... parts) {}

        @Override
        public void scheduleNetworkGuiUpdate(PipeMessageReceiver... parts) {}

        @Override
        public void sendMessage(PipeMessageReceiver to, IWriter writer) {}

        @Override
        public void sendGuiMessage(PipeMessageReceiver to, IWriter writer) {}

        @Override
        public void onPlayerOpen(Player player) {}

        @Override
        public void onPlayerClose(Player player) {}

        @Override
        public int getRedstoneInput(Direction side) {
            return 0;
        }

        @Override
        public boolean setRedstoneOutput(Direction side, int value) {
            return false;
        }
    };
}
