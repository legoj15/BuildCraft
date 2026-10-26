/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;

/**
 * One cell a stalled Builder is skipping, and why.
 *
 * @param pos the cell, in world coordinates
 * @param reason what holds it back
 */
public record StalledCell(BlockPos pos, EnumStallReason reason) {
}
