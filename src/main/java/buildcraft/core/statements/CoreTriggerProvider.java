/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import java.util.Collection;

import javax.annotation.Nonnull;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;

import net.neoforged.neoforge.capabilities.Capabilities;

import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.ITriggerInternalSided;
import buildcraft.api.statements.ITriggerProvider;
import buildcraft.api.statements.containers.IRedstoneStatementContainer;
import buildcraft.api.tiles.IHasWork;
import buildcraft.api.transport.pipe.IPipeHolder;

import buildcraft.core.BCCoreStatements;

public enum CoreTriggerProvider implements ITriggerProvider {
    INSTANCE;

    @Override
    public void addInternalTriggers(Collection<ITriggerInternal> res, IStatementContainer container) {
        res.add(BCCoreStatements.TRIGGER_TRUE);
        if (container instanceof IRedstoneStatementContainer) {
            res.add(BCCoreStatements.TRIGGER_REDSTONE_ACTIVE);
            res.add(BCCoreStatements.TRIGGER_REDSTONE_INACTIVE);
        }

        if (TriggerPower.isTriggeringTile(container.getTile())) {
            res.add(BCCoreStatements.TRIGGER_POWER_HIGH);
            res.add(BCCoreStatements.TRIGGER_POWER_LOW);
        }
    }

    @Override
    public void addInternalSidedTriggers(Collection<ITriggerInternalSided> res, IStatementContainer container,
        @Nonnull Direction side) {}

    @Override
    public void addExternalTriggers(Collection<ITriggerExternal> res, @Nonnull Direction side, BlockEntity tile) {
        // Skip pipe holders — they have their own internal trigger system (TriggerProviderPipes).
        // In 1.12.2, pipe sections returned getTankProperties()=empty so they were naturally filtered
        // by the liquids.length > 0 check. In 26.1.1 ResourceHandler.size() returns 1 unconditionally,
        // so we must explicitly exclude pipes to avoid false-positive fluid/item triggers.
        if (tile instanceof IPipeHolder) {
            return;
        }

        if (TriggerPower.isTriggeringTile(tile, side.getOpposite())) {
            res.add(BCCoreStatements.TRIGGER_POWER_HIGH);
            res.add(BCCoreStatements.TRIGGER_POWER_LOW);
        }

        boolean blockInventoryTriggers = false;
        boolean blockFluidHandlerTriggers = false;

        if (tile instanceof IBlockDefaultTriggers defaults) {
            blockInventoryTriggers = defaults.blockInventoryTriggers(side);
            blockFluidHandlerTriggers = defaults.blockFluidHandlerTriggers(side);
        }

        if (!blockInventoryTriggers && tile != null && tile.getLevel() != null) {
            //? if >=1.21.10 {
            var itemHandler = tile.getLevel().getCapability(Capabilities.Item.BLOCK, tile.getBlockPos(), side.getOpposite());
            boolean hasInventory = itemHandler != null && itemHandler.size() > 0;
            //?} else {
            /*var itemHandler = tile.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, tile.getBlockPos(), side.getOpposite());
            boolean hasInventory = itemHandler != null && itemHandler.getSlots() > 0;*/
            //?}
            if (hasInventory) {
                res.add(BCCoreStatements.TRIGGER_INVENTORY_EMPTY);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_SPACE);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_CONTAINS);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_FULL);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_BELOW_25);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_BELOW_50);
                res.add(BCCoreStatements.TRIGGER_INVENTORY_BELOW_75);
            }
        }

        if (!blockFluidHandlerTriggers && tile != null && tile.getLevel() != null) {
            //? if >=1.21.10 {
            var fluidHandler = tile.getLevel().getCapability(Capabilities.Fluid.BLOCK, tile.getBlockPos(), side.getOpposite());
            boolean hasFluid = fluidHandler != null && fluidHandler.size() > 0;
            //?} else {
            /*var fluidHandler = tile.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, tile.getBlockPos(), side.getOpposite());
            boolean hasFluid = fluidHandler != null && fluidHandler.getTanks() > 0;*/
            //?}
            if (hasFluid) {
                res.add(BCCoreStatements.TRIGGER_FLUID_EMPTY);
                res.add(BCCoreStatements.TRIGGER_FLUID_SPACE);
                res.add(BCCoreStatements.TRIGGER_FLUID_CONTAINS);
                res.add(BCCoreStatements.TRIGGER_FLUID_FULL);
                res.add(BCCoreStatements.TRIGGER_FLUID_BELOW_25);
                res.add(BCCoreStatements.TRIGGER_FLUID_BELOW_50);
                res.add(BCCoreStatements.TRIGGER_FLUID_BELOW_75);
            }
        }

        if (tile instanceof IHasWork) {
            res.add(BCCoreStatements.TRIGGER_MACHINE_ACTIVE);
            res.add(BCCoreStatements.TRIGGER_MACHINE_INACTIVE);
        }

        if (TriggerEnginePowerStage.isTriggeringTile(tile)) {
            res.add(BCCoreStatements.TRIGGER_POWER_BLUE);
            res.add(BCCoreStatements.TRIGGER_POWER_GREEN);
            res.add(BCCoreStatements.TRIGGER_POWER_YELLOW);
            res.add(BCCoreStatements.TRIGGER_POWER_RED);
            res.add(BCCoreStatements.TRIGGER_POWER_OVERHEAT);
        }
    }
}
