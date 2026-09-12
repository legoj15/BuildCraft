/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.robotics.tile.TileRequester;

/** The Requester GUI's menu, ported from 7.1.x {@code ContainerRequester}/{@code GuiRequester}: 20 real
 *  delivery slots beside 20 ghost request templates, plus the player inventory. The 7.1.x GUI's custom
 *  RPC ("click a ghost slot with N items held to request N of them") is the phantom-click override below:
 *  the carried stack sets the template ITEM-AND-COUNT, an empty hand clears it. The default
 *  {@code handlePhantomClick} (always count 1) is deliberately bypassed for these slots — the count IS the
 *  requested quantity, the one number this block exists to let you say.
 *
 * <p>Clicks run on both sides against the same carried stack (vanilla prediction + authority), and the
 *  server tile's change flows back through the standard menu slot sync — no 7.1.x-style request-list
 *  packet round trip needed. */
@SuppressWarnings("this-escape")
public class ContainerRequester extends ContainerBCTile<TileRequester> {

    // Client-side constructor (from network)
    public ContainerRequester(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, resolveTile(playerInv, buf, TileRequester.class));
    }

    // Server-side constructor
    public ContainerRequester(int containerId, Inventory playerInv, TileRequester tile) {
        super(BCRoboticsMenuTypes.REQUESTER.get(), containerId, playerInv.player, tile);

        // Delivery slots first: shift-clicks from the player inventory land in them (the phantom
        // templates refuse mayPlace), each gated by the template-matching insertion checker.
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 5; row++) {
                addSlot(new SlotBase(tile.invDeliveries, col * 5 + row, 117 + col * 18, 7 + row * 18));
            }
        }

        // The request templates — 7.1.x layout, mirroring the delivery grid.
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 5; row++) {
                addSlot(new SlotPhantom(tile.invRequests, col * 5 + row, 9 + col * 18, 7 + row * 18));
            }
        }

        addFullPlayerInventory(19, 101);
    }

    /** 7.1.x {@code GuiRequester.slotClicked}/{@code RequestSlot.setItem}: the ghost slot adopts the
     *  carried stack (count included — hold 32 to request 32), or clears on an empty hand. */
    @Override
    //? if >=26.1 {
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput clickType, Player player) {
    //?} else {
    /*public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {*/
    //?}
        Slot slot = slotId >= 0 && slotId < slots.size() ? slots.get(slotId) : null;
        if (slot instanceof SlotPhantom phantom && phantom.itemHandler == tile.invRequests) {
            ItemStack held = getCarried();
            if (held.isEmpty()) {
                tile.setRequest(phantom.handlerIndex, ItemStack.EMPTY);
            } else {
                ItemStack template = held.copy();
                // Clamp at the 7.1.x SimpleInventory limit; the count is the quantity requested.
                template.setCount(Math.min(held.getCount(), held.getMaxStackSize()));
                tile.setRequest(phantom.handlerIndex, template);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }
}
