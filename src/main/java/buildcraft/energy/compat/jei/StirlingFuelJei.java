/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.compat.jei;

import net.minecraft.world.item.ItemStack;

/**
 * One displayed Stirling-engine fuel: a solid furnace fuel and the vanilla burn time (in ticks)
 * the engine gets from it. The Stirling engine's output is PID-regulated up to a fixed
 * {@code MjAPI.MJ} (1 MJ/t) ceiling — see {@code TileEngineStone_BC8} — so the power rate is a
 * constant the category prints rather than a per-fuel field.
 */
public record StirlingFuelJei(ItemStack fuel, int burnTime) {}
