/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.StatementParameterItemStack;

/**
 * Shared base for the four "container content" external triggers — the inventory/fluid pairs that
 * probe an adjacent block's item or fluid capability. It splits along the query axis into
 * {@link AbstractContainerStateTrigger} (empty / contains / space / full) and
 * {@link AbstractContainerLevelTrigger} (fill-percentage thresholds).
 *
 * <p>All four take a single optional item-filter parameter, so {@link #createParameter(int)} lives
 * here once. Everything genuinely shared (the query enums, the parameter count) is hoisted into the
 * two intermediate bases; the per-content-kind capability scan is NOT.
 *
 * <p><b>Directive boundary (load-bearing):</b> each concrete trigger keeps its own
 * {@code //? if >=1.21.10} directive-gated {@code isTriggerActive} scan. The item capability
 * ({@code ItemResource}/{@code IItemHandler}) and the fluid capability
 * ({@code FluidResource}/{@code IFluidHandler}) diverge both across content kind AND across the
 * 1.21.10 transfer-API cliff, so the probes are intentionally not fused into one method here.
 */
public abstract class AbstractContainerContentTrigger extends BCStatement implements ITriggerExternal {

    protected AbstractContainerContentTrigger(String... uniqueTag) {
        super(uniqueTag);
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return new StatementParameterItemStack();
    }
}
