/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
//? if >=26.1 {
import net.minecraft.world.inventory.ContainerInput;
//?} else {
/*import net.minecraft.world.inventory.ClickType;*/
//?}
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.lib.net.IPayloadWriter;
import buildcraft.lib.net.PacketBufferBC;

/**
 * Base container class for the majority of BuildCraft GUIs — those that do NOT use vanilla's recipe
 * book. Rooted on plain {@link AbstractContainerMenu} (the false {@code extends RecipeBookMenu} that
 * used to type every menu as a recipe-book menu, forcing a version-forked stub block, is gone). The
 * two tables that genuinely use the book — the Auto Workbench and the Advanced Crafting Table — extend
 * {@link ContainerBCCrafting} instead, which carries the recipe-book surface.
 *
 * <p>The shared machinery (shift-click / phantom-slot handling, widget sync via
 * {@link buildcraft.lib.net.MessageContainerPayload}, {@code addFullPlayerInventory}) lives in
 * {@link BCContainerSupport}; {@code sendMessage}/{@code addWidget}/{@code getWidgets} come from the
 * {@link BCContainer} default methods over that support.
 *
 * <p><b>Two container→screen sync mechanisms coexist deliberately — do not collapse them.</b> Vanilla
 * {@link net.minecraft.world.inventory.ContainerData} carries ints (engines hi/lo-split their power
 * because of it); the BC {@link Widget_Neptune}/{@code sendMessage} channel carries fluids, longs and
 * variable payloads that can't ride {@code ContainerData}.
 */
@SuppressWarnings("this-escape")
public abstract class ContainerBC_Neptune extends AbstractContainerMenu implements BCContainer {

    public static final int NET_WIDGET = 0;
    /** Container message ID used by JEI's BlueprintTransferHandler. */
    public static final int NET_JEI_RECIPE_TRANSFER = 100;
    /** Container message ID used by JEI's BCGhostIngredientHandler. */
    public static final int NET_GHOST_SLOT_SET = 101;
    /** Container message ID: JEI "+" transfer of real items into a machine's input inventory
     *  (Assembly Table). Payload: boolean maxTransfer, varInt count, then each chosen stack as NBT.
     *  Handled by the container subclass; see {@link buildcraft.lib.compat.jei.JeiTransferUtil}. */
    public static final int NET_JEI_TRANSFER_ITEMS = 102;
    /** Container message ID: JEI "+" transfer of filled fluid buckets into a machine's container
     *  slots (Distiller, Heat Exchanger). Payload: varInt count, then (varInt slotIndex, utf bucketItemId) pairs. */
    public static final int NET_JEI_TRANSFER_BUCKETS = 103;

    public final Player player;
    private final BCContainerSupport support;

    protected ContainerBC_Neptune(MenuType<?> menuType, int containerId, Player player) {
        super(menuType, containerId);
        this.player = player;
        this.support = new BCContainerSupport(this, player);
    }

    @Override
    public BCContainerSupport bcSupport() {
        return support;
    }

    protected void addFullPlayerInventory(int startX, int startY) {
        support.addFullPlayerInventory(startX, startY, player.getInventory(), this::addSlot);
    }

    protected void addFullPlayerInventory(int startX, int startY, Inventory inv) {
        support.addFullPlayerInventory(startX, startY, inv, this::addSlot);
    }

    // --- Networking ---

    /**
     * Package-private: called by {@link Widget_Neptune#sendWidgetData} to route widget data through
     * the container's networking.
     */
    void sendWidgetData(Widget_Neptune<?> widget, IPayloadWriter writer) {
        support.sendWidgetData(widget, writer);
    }

    /**
     * Handle an incoming container message. The widget + JEI ghost-slot IDs are handled by the shared
     * support; subclasses override this to add their own IDs (and call {@code super.readMessage}).
     * This base has no recipe-book placement to do — that moved to {@link ContainerBCCrafting}.
     */
    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        support.readMessage(id, buffer, isClient, ctx);
    }

    // --- Slot handling ---

    @Override
    //? if >=26.1 {
    public void clicked(int slotId, int dragType, ContainerInput containerInput, Player player) {
    //?} else {
    /*public void clicked(int slotId, int dragType, ClickType containerInput, Player player) {*/
    //?}
        if (support.handlePhantomClick(slotId)) {
            return;
        }
        super.clicked(slotId, dragType, containerInput, player);
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        return support.quickMoveStack(index, this::moveItemStackTo);
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // Subclasses override
    }
}
