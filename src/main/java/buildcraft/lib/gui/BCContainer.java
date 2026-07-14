/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.lib.net.IPayloadWriter;
import buildcraft.lib.net.PacketBufferBC;

/**
 * The shared BuildCraft-menu contract, implemented by BOTH menu bases —
 * {@link ContainerBC_Neptune} (on plain {@code AbstractContainerMenu}) and {@link ContainerBCCrafting}
 * (on {@code RecipeBookMenu}, for the two crafting tables that genuinely use the recipe book). It is
 * the common supertype those two hierarchies would otherwise lack: the {@code GuiBC8} screen bound, the
 * {@link buildcraft.lib.net.MessageContainerPayload} dispatch target, and the type the JEI/REI recipe
 * transfer handlers send through.
 *
 * <p>The machinery (widget list + {@code sendMessage}/{@code readMessage} + phantom-slot handling +
 * {@code addFullPlayerInventory}) lives once in {@link BCContainerSupport}; each base holds one as a
 * field and exposes it through {@link #bcSupport()}. The default methods below forward to it, so a base
 * only re-implements the handful of members that must touch {@code AbstractContainerMenu}'s protected
 * state directly (the {@code quickMoveStack}/{@code clicked}/{@code addFullPlayerInventory} overrides).
 */
public interface BCContainer {

    /** The per-instance state + shared logic backing this menu's BuildCraft machinery. */
    BCContainerSupport bcSupport();

    /** Send a container message to the other side (client↔server). */
    default void sendMessage(int id, IPayloadWriter writer) {
        bcSupport().sendMessage(id, writer);
    }

    /** Register a sync widget (fluid tanks, progress, …). */
    default <W extends Widget_Neptune<?>> W addWidget(W widget) {
        return bcSupport().addWidget(widget);
    }

    /** Immutable snapshot of this menu's widgets, in registration order. */
    default ImmutableList<Widget_Neptune<?>> getWidgets() {
        return bcSupport().getWidgets();
    }

    /** Dispatch an incoming container message. Implemented on the bases (each adds its own IDs). */
    void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx);

    /** From {@code AbstractContainerMenu}; declared here so the message dispatch can reach-check. */
    boolean stillValid(Player player);
}
