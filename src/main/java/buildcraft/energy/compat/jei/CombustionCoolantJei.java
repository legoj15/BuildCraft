/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.compat.jei;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One displayed combustion-engine coolant. Two shapes share this record, discriminated by
 * {@link #item}:
 * <ul>
 * <li><b>Fluid coolant</b> (e.g. water): {@code item} is {@link ItemStack#EMPTY}, {@code fluid}
 *     is the coolant fluid, {@code coolingPerMb} its cooling rate (°/mB).</li>
 * <li><b>Solid coolant</b> (e.g. ice): {@code item} is the solid, {@code fluid} is the fluid it
 *     melts into (with its produced amount, e.g. 1500 mB water for ice), {@code coolingPerMb} 0.</li>
 * </ul>
 */
public record CombustionCoolantJei(ItemStack item, FluidStack fluid, float coolingPerMb) {
    public boolean isSolid() {
        return !item.isEmpty();
    }
}
