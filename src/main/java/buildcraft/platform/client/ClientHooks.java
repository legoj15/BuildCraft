/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Client-only game hooks, typed over vanilla and BuildCraft-owned values (docs/platform-layer.md, "Client render").
 * Each call adds one listener, at the loader's default priority unless the method says otherwise; listeners of
 * one hook run in the order they were added.
 */
public interface ClientHooks {

    /**
     * Draws into the world once per frame, after the translucent blocks (NeoForge: {@code SubmitCustomGeometryEvent}
     * from 26.1, {@code RenderLevelStageEvent} at {@code AFTER_TRANSLUCENT_BLOCKS} before it).
     */
    void onLevelRender(Consumer<BCLevelRenderContext> renderer);

    /** Runs at the end of every client tick. */
    void onClientTick(Runnable listener);

    /** Runs when the local player has joined a world (single-player or a server). */
    void onLoggingIn(Runnable listener);

    /**
     * {@link #onLoggingIn}, ahead of the listeners at the default priority (NeoForge: {@code EventPriority.HIGH}), for
     * state other join listeners read.
     */
    void onLoggingInEarly(Runnable listener);

    /** Runs when the local player leaves the world (disconnect, or leaving single-player). */
    void onLoggingOut(Runnable listener);

    /**
     * Runs with every recipe the client has received, on each join and on each datapack reload. Before 1.21.10 this is
     * vanilla's full recipe sync; from 1.21.10 it is the loader's opt-in sync of the recipe types the server asked to
     * send, which may be empty.
     */
    void onRecipesReceived(Consumer<Collection<RecipeHolder<?>>> listener);

    /** Adds lines to an item's tooltip as it is built. */
    void onItemTooltip(ItemTooltipListener listener);

    /** Runs just before an item tooltip is drawn, to draw beside it. */
    void onTooltipPre(Consumer<BCTooltipPreContext> listener);

    /**
     * Replaces the outline of the block under the crosshair: a non-null shape (in block-local coordinates) is drawn in
     * place of the vanilla outline; null leaves the vanilla outline alone.
     */
    void onBlockOutline(BlockOutlineOverride override);

    /** Runs when a client level closes (disconnect, or the old dimension on a dimension change); never for a
     *  server level, even one an integrated server runs in this JVM. */
    void onClientLevelUnload(Runnable listener);

    /** Runs when the client loads a chunk (the server sent it); never for a server-side chunk. */
    void onClientChunkLoad(BiConsumer<Level, ChunkPos> listener);

    /** Runs when the client drops a chunk; never for a server-side chunk. */
    void onClientChunkUnload(BiConsumer<Level, ChunkPos> listener);

    /** Adds lines to an item tooltip. */
    @FunctionalInterface
    interface ItemTooltipListener {
        /** @param player the player the tooltip is built for, or null (a tooltip built with no player, e.g. search) */
        void onTooltip(ItemStack stack, Player player, List<Component> lines);
    }

    /** Picks the outline drawn for the block under the crosshair. */
    @FunctionalInterface
    interface BlockOutlineOverride {
        /** The shape to draw instead of the vanilla outline, or null to keep it. */
        VoxelShape outline(Level level, BlockPos pos, BlockState state, BlockHitResult hit);
    }
}
