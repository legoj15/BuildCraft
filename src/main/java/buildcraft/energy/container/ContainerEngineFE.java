/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;



import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.lib.engine.EngineMenuData;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.WideContainerData;

@SuppressWarnings("this-escape")
public class ContainerEngineFE extends ContainerBCTile<TileEngineFE> {
    // The engine tile is the inherited `tile` field (ContainerBCTile). Screens read it via menu.tile.
    private final EngineMenuData engineData;
    private final WideContainerData.IntValue feStored;

    public ContainerEngineFE(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, resolveTile(playerInv, buf, TileEngineFE.class));
    }

    public ContainerEngineFE(int containerId, Inventory playerInv, TileEngineFE engine) {
        super(BCEnergyMenuTypes.ENGINE_FE.get(), containerId, playerInv.player, engine);

        // WideContainerData keeps every value intact through vanilla's 16-bit menu-data packet (Fabric's only one).
        WideContainerData.Builder sync = WideContainerData.builder();
        engineData = new EngineMenuData(sync, engine, () -> engine.getPower());
        feStored = sync.addInt(() -> engine.getCurrentFe());
        addDataSlots(sync.buildFor(engine));

        if (engine != null) {
            for (int slot = 0; slot < 4; slot++) {
                addSlot(new buildcraft.lib.gui.slot.SlotBase(engine.upgrades, slot, 62 + 18 * slot, 44) {
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
