/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.guide;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/** One thing a robotics laser table makes, as the guide shows it.
 *
 *  @param id          What a page pins this step by: the robot board id the step programs or installs.
 *  @param slots       The stacks for each input slot, top to bottom (a slot with several stacks cycles).
 *  @param outputs     What the table makes first; any further stacks are variants of the same result (a drained
 *                     robot beside the charged one the table crafts), used only for lookups, never drawn.
 *  @param microJoules The laser energy the table needs. */
record LaserTableStep(String id, List<List<ItemStack>> slots, List<ItemStack> outputs, long microJoules) {}
