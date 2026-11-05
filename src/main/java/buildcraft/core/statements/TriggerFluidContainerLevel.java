/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;


import buildcraft.api.fluids.BCFluidStack;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.TransferAccess;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.misc.LocaleUtil;

import buildcraft.core.BCCoreSprites;
import buildcraft.core.BCCoreStatements;

public class TriggerFluidContainerLevel extends AbstractContainerLevelTrigger {

    public TriggerFluidContainerLevel(TriggerType type) {
        // Primary UID uses the collision-free "fluidlevel." prefix (mirrors "inventorylevel." on the
        // item side). The old "buildcraft:fluid." / "buildcraft.fluid." UIDs — which used to be the
        // primary — are kept as LEGACY ALIASES so gates saved in existing worlds (and the guide's
        // trigger cross-references) still resolve. The plain "buildcraft:fluid." prefix now belongs
        // solely to TriggerFluidContainer (empty/contains/space/full); disjoint value names today,
        // but a future FULL-style level value would otherwise silently collide.
        super(type,
            "buildcraft:fluidlevel." + type.name().toLowerCase(Locale.ROOT),
            "buildcraft:fluid." + type.name().toLowerCase(Locale.ROOT),
            "buildcraft.fluid." + type.name().toLowerCase(Locale.ROOT)
        );
    }

    @Override
    public SpriteHolder getSprite() {
        return BCCoreSprites.TRIGGER_FLUID_LEVEL.get(type);
    }

    @Override
    public String getDescription() {
        return String.format(LocaleUtil.localize("gate.trigger.fluidlevel.below"), (int) (type.level * 100));
    }

    @Override
    public boolean isTriggerActive(Level level, BlockPos pos, @Nullable BlockEntity tile, Direction side,
        IStatementContainer statementContainer, IStatementParameter[] parameters) {
        BCFluidStorage handler = TransferAccess.findFluid(level, pos, side != null ? side.getOpposite() : null);
        if (handler == null) {
            return false;
        }

        BCFluidStack searchedFluid = BCFluidStack.EMPTY;

        if (parameters != null && parameters.length >= 1 && parameters[0] != null && !parameters[0].getItemStack().isEmpty()) {
            net.minecraft.world.item.ItemStack stack = parameters[0].getItemStack();
            searchedFluid = FluidUtilBC.getFluidContained(stack);
        }
        return isBelow(handler, searchedFluid, type.level);
    }

    /** Whether the first tank of {@code handler} that holds {@code searchedFluid} (any fluid, if empty) is below
     *  {@code level}, or an empty tank could take it. */
    public static boolean isBelow(BCFluidStorage handler, BCFluidStack searchedFluid, float level) {
        int tanks = handler.size();
        if (tanks == 0) {
            return false;
        }

        for (int i = 0; i < tanks; i++) {
            BCFluidStack fluid = handler.getStack(i);
            int capacity = handler.getCapacity(i, fluid);
            if (capacity <= 0) continue;

            if (fluid.isEmpty()) {
                // Empty tank — if we're searching for a specific fluid, check if we can fill it
                if (searchedFluid.isEmpty()) {
                    return true; // Empty tank is certainly below threshold
                }
                // An unbounded, uncommitted probe (see TriggerFluidContainer): a cauldron refuses a 1 mB one.
                try (BCTransaction tx = BCTransaction.openRoot()) {
                    //? if >=1.21.10 {
                    int accepted = handler.insert(i, searchedFluid, Integer.MAX_VALUE, tx);
                    //?} else {
                    /*// 1.21.1 asked the whole classic handler, which cannot fill one tank on its own.
                    int accepted = handler.insert(searchedFluid, Integer.MAX_VALUE, tx);*/
                    //?}
                    if (accepted > 0) {
                        return true;
                    }
                }
            } else {
                if (searchedFluid.isEmpty() || BCFluidStack.isSameFluidSameComponents(fluid, searchedFluid)) {
                    float percentage = handler.getAmount(i) / (float) capacity;
                    return percentage < level;
                }
            }
        }
        return false;
    }

    @Override
    public IStatement[] getPossible() {
        return BCCoreStatements.TRIGGER_FLUID_ALL;
    }
}
