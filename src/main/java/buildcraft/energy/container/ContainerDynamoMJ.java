/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;



import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.tile.TileDynamoMJ;
import buildcraft.lib.engine.EngineMenuData;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.WideContainerData;

@SuppressWarnings("this-escape")
public class ContainerDynamoMJ extends ContainerBCTile<TileDynamoMJ> {
    // The dynamo tile is the inherited `tile` field (ContainerBCTile). Screens read it via menu.tile.
    private final EngineMenuData engineData;
    private final WideContainerData.IntValue feStored;

    public ContainerDynamoMJ(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, resolveTile(playerInv, buf, TileDynamoMJ.class));
    }

    public ContainerDynamoMJ(int containerId, Inventory playerInv, TileDynamoMJ dynamo) {
        super(BCEnergyMenuTypes.DYNAMO_MJ.get(), containerId, playerInv.player, dynamo);

        // WideContainerData keeps every value intact through vanilla's 16-bit menu-data packet (Fabric's only one).
        WideContainerData.Builder sync = WideContainerData.builder();
        engineData = new EngineMenuData(sync, dynamo, () -> dynamo.getMjBattery().getStored());
        feStored = sync.addInt(() -> dynamo.getCurrentFe());
        addDataSlots(sync.buildFor(dynamo));

        if (dynamo != null) {
            for (int slot = 0; slot < 4; slot++) {
                addSlot(new buildcraft.lib.gui.slot.SlotBase(dynamo.upgrades, slot, 44 + 18 * slot, 44) {
                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }

                    @Override
                    public int getMaxStackSize(net.minecraft.world.item.ItemStack stack) {
                        return 1;
                    }
                });
            }
        }

        addFullPlayerInventory(8, 95, playerInv);
    }

    public long getSyncedPower() {
        return engineData.power();
    }

    public float getSyncedHeat() {
        return engineData.heat();
    }

    public buildcraft.api.enums.EnumPowerStage getSyncedPowerStage() {
        return engineData.stage();
    }

    public boolean isSyncedBurningEngine() {
        return engineData.burning();
    }

    public long getSyncedCurrentOutput() {
        return engineData.output();
    }

    public int getSyncedFeStored() {
        return feStored.get();
    }
}
