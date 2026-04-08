/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import java.util.Locale;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;

import buildcraft.core.BCCoreSprites;
import buildcraft.core.BCCoreStatements;

public class TriggerFluidContainerLevel extends BCStatement implements ITriggerExternal {
    public final TriggerType type;

    public TriggerFluidContainerLevel(TriggerType type) {
        super(
            "buildcraft:fluid." + type.name().toLowerCase(Locale.ROOT),
            "buildcraft.fluid." + type.name().toLowerCase(Locale.ROOT)
        );
        this.type = type;
    }

    @Override
    public SpriteHolder getSprite() {
        return BCCoreSprites.TRIGGER_FLUID_LEVEL.get(type);
    }

    @Override
    public int maxParameters() {
        return 1;
    }

    @Override
    public String getDescription() {
        return String.format(LocaleUtil.localize("gate.trigger.fluidlevel.below"), (int) (type.level * 100));
    }

    @Override
    public boolean isTriggerActive(BlockEntity tile, Direction side, IStatementContainer statementContainer, IStatementParameter[] parameters) {
        if (tile == null || tile.getLevel() == null) {
            return false;
        }
        ResourceHandler<FluidResource> handler = tile.getLevel().getCapability(
            Capabilities.Fluid.BLOCK, tile.getBlockPos(), side
        );
        if (handler == null) {
            return false;
        }

        FluidResource searchedFluid = FluidResource.EMPTY;

        if (parameters != null && parameters.length >= 1 && parameters[0] != null && !parameters[0].getItemStack().isEmpty()) {
            net.minecraft.world.item.ItemStack stack = parameters[0].getItemStack();
            ResourceHandler<FluidResource> itemHandler = stack.getCapability(Capabilities.Fluid.ITEM, net.neoforged.neoforge.transfer.access.ItemAccess.forStack(stack));
            if (itemHandler != null && itemHandler.size() > 0) {
                searchedFluid = itemHandler.getResource(0);
            }
        }

        int tanks = handler.size();
        if (tanks == 0) {
            return false;
        }

        for (int i = 0; i < tanks; i++) {
            FluidResource fluid = handler.getResource(i);
            int capacity = (int) handler.getCapacityAsLong(i, fluid);
            if (capacity <= 0) continue;

            if (fluid.isEmpty()) {
                // Empty tank — if we're searching for a specific fluid, check if we can fill it
                if (searchedFluid.isEmpty()) {
                    return true; // Empty tank is certainly below threshold
                }
                try (Transaction tx = Transaction.openRoot()) {
                    if (handler.insert(i, searchedFluid, 1, tx) > 0) {
                        return true;
                    }
                }
            } else {
                if (searchedFluid.isEmpty() || fluid.equals(searchedFluid)) {
                    float percentage = handler.getAmountAsInt(i) / (float) capacity;
                    return percentage < type.level;
                }
            }
        }
        return false;
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return new StatementParameterItemStack();
    }

    @Override
    public IStatement[] getPossible() {
        return BCCoreStatements.TRIGGER_FLUID_ALL;
    }

    public enum TriggerType {
        BELOW25(0.25F),
        BELOW50(0.5F),
        BELOW75(0.75F);

        TriggerType(float level) {
            this.level = level;
        }

        public static final TriggerType[] VALUES = values();

        public final float level;
    }
}
