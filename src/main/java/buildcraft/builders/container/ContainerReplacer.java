/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.net.PacketBufferBC;

import buildcraft.builders.BCBuildersMenuTypes;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.tile.TileReplacer;

public class ContainerReplacer extends ContainerBCTile<TileReplacer> {

    /** Client→Server: perform the replacement. Payload = UTF string (new name, may be blank). */
    public static final int NET_REPLACE = 10;

    // Client-side constructor (from network)
    public ContainerReplacer(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerReplacer(int containerId, Inventory playerInv, TileReplacer tile) {
        super(BCBuildersMenuTypes.REPLACER.get(), containerId, playerInv.player, tile);

        if (tile != null) {
            addSlot(new SlotBase(tile.invSnapshot, 0, 8, 115));
            addSlot(new SlotBase(tile.invSchematicFrom, 0, 8, 137));
            addSlot(new SlotBase(tile.invSchematicTo, 0, 56, 137));
        }

        // Player inventory at y=159 (matches 1.12.2 layout)
        addFullPlayerInventory(8, 159, playerInv);
    }

    private static TileReplacer getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileReplacer replacer) {
                return replacer;
            }
        }
        return null;
    }

    /**
     * Current blueprint name as stored on the slot-0 item stack. Works on both sides because
     * slot-0's {@link ItemStack} is automatically synced by the menu machinery. Empty slot or
     * non-snapshot item returns {@code ""} so the EditBox stays empty rather than showing a
     * "<unnamed>" placeholder the user would have to clear.
     */
    public String getBlueprintName() {
        if (this.slots.isEmpty()) {
            return "";
        }
        ItemStack stack = getSlot(0).getItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemSnapshot)) {
            return "";
        }
        Snapshot.Header header = ItemSnapshot.getHeader(stack);
        if (header == null || header.name == null) {
            return "";
        }
        return header.name;
    }

    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        if (id == NET_REPLACE && !isClient) {
            String newName = buffer.readUtf();
            if (tile != null) {
                tile.doReplace(newName);
            }
            return;
        }
        super.readMessage(id, buffer, isClient, ctx);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Defer to vanilla no-op semantics for now. Shift-click moving blueprints around is
        // nice-to-have but not part of the Replacer's core contract, and implementing it here
        // requires knowing the player-inventory slot range — see the Architect's pattern if
        // we want to expand this later.
        return ItemStack.EMPTY;
    }
}
