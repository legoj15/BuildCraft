/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.factory.BCFactoryBlockEntities;

public class TileAutoWorkbenchItems extends TileAutoWorkbenchBase {
    public TileAutoWorkbenchItems(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(), pos, state, 3, 3);
    }
}
