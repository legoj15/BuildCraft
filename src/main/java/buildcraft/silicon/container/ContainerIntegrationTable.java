/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.lib.net.PacketBufferBC;

import buildcraft.robotics.container.RoboticsTableTransfer;

import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.tile.TileIntegrationTable;

@SuppressWarnings("this-escape")
public class ContainerIntegrationTable extends ContainerBCTile<TileIntegrationTable> {

    // Client-side constructor (from network)
    public ContainerIntegrationTable(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv.player, resolveTile(playerInv, buf, TileIntegrationTable.class));
    }

    // Server-side constructor
    public ContainerIntegrationTable(int containerId, Player player, TileIntegrationTable tile) {
        super(BCSiliconMenuTypes.INTEGRATION_TABLE.get(), containerId, player, tile);

        // 3x3 grid, added in reading order: center is target, surrounding 8 are ingredients
        for (int y = 0; y < IntegrationTableLayout.RING_ROWS; y++) {
            for (int x = 0; x < IntegrationTableLayout.RING_COLS; x++) {
                boolean centre = IntegrationTableLayout.isCentre(x, y);
                addSlot(new SlotBase(
                    centre ? tile.invTarget : tile.invToIntegrate,
                    centre ? 0 : IntegrationTableLayout.ringSlot(x, y),
                    IntegrationTableLayout.ringX(x), IntegrationTableLayout.ringY(y)
                ));
            }
        }

        // Display slot showing recipe output preview
        addSlot(new SlotDisplay(i -> tile.getOutput(), 0,
                IntegrationTableLayout.PREVIEW_X, IntegrationTableLayout.PREVIEW_Y));

        // Actual output slot
        addSlot(new SlotOutput(tile.invResult, 0, IntegrationTableLayout.RESULT_X, IntegrationTableLayout.RESULT_Y));

        addFullPlayerInventory(IntegrationTableLayout.PLAYER_INV_X, IntegrationTableLayout.PLAYER_INV_Y);
    }

    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        if (id == NET_JEI_TRANSFER_INTEGRATION && !isClient && tile != null) {
            // JEI "+" on an Integration Table recipe. Payload: the board id; the robot and the board are both
            // checked against the player's real inventory before either moves.
            RoboticsTableTransfer.integrate(player.getInventory(), tile, buffer.readUtf(), true);
            return;
        }
        super.readMessage(id, buffer, isClient, ctx);
    }
}
