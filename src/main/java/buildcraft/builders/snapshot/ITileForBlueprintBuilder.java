/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import buildcraft.api.inventory.IItemTransactor;

import net.neoforged.neoforge.fluids.FluidStack;

public interface ITileForBlueprintBuilder extends ITileForSnapshotBuilder {
    Blueprint.BuildingInfo getBlueprintBuildingInfo();

    IItemTransactor getInvResources();

    //? if >=1.21.10 {
    net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> getTankManager();
    //?} else {
    /*net.neoforged.neoforge.fluids.capability.IFluidHandler getTankManager();*/
    //?}
}
