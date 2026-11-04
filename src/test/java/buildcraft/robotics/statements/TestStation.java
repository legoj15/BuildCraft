/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.statements;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;


import buildcraft.api.inventory.IItemTransactor;
import buildcraft.lib.inventory.InventoryWrapper;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRequestProvider;
import buildcraft.api.statements.StatementSlot;

/** A {@link DockingStation} whose {@link #getActiveActions()} returns a fixed slot list — the
 *  gate-free fixture the statement predicate tests drive against. The item/fluid/request inputs
 *  default to non-null so the provider tests see a fully equipped station. */
public class TestStation extends DockingStation {

    private final List<StatementSlot> slots;

    public TestStation(List<StatementSlot> slots) {
        super(BlockPos.ZERO, Direction.UP);
        this.slots = slots;
    }

    @Override
    public Iterable<StatementSlot> getActiveActions() {
        return slots;
    }

    @Override
    public IItemTransactor getItemInput() {
        return new InventoryWrapper(new SimpleContainer(27));
    }

    @Override
    public buildcraft.api.core.IFluidHandlerAdv getFluidInput() {
        // An empty storage's view: the provider tests only need the seam non-null.
        return buildcraft.lib.transfer.FluidHandlerAdvs.of(new buildcraft.lib.fluid.BCFluidTank(0, 0));
    }

    @Override
    public IRequestProvider getRequestProvider() {
        return new IRequestProvider() {
            @Override
            public int getRequestsCount() {
                return 0;
            }

            @Override
            public ItemStack getRequest(int slot) {
                return null;
            }

            @Override
            public ItemStack offerItem(int slot, ItemStack stack) {
                return stack;
            }
        };
    }
}
