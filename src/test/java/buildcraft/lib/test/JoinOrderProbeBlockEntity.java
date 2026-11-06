/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.tile.AbstractBCBlockEntity;

/**
 * Records when it was told it joined its level (BuildCraft's {@code onLoaded}, NeoForge's {@code onLoad}) relative to
 * its block's first scheduled tick. Registered by {@link TestOnlyBlocks}; placed by {@link JoinOrderProbeBlock}.
 */
public final class JoinOrderProbeBlockEntity extends AbstractBCBlockEntity {

    private boolean joined;

    /** Whether it had joined when its block's first scheduled tick ran, or null before that tick. */
    @Nullable
    private Boolean joinedAtFirstBlockTick;

    public JoinOrderProbeBlockEntity(BlockPos pos, BlockState state) {
        super(TestOnlyBlocks.joinOrderProbeType(), pos, state);
    }

    @Override
    protected void onLoaded() {
        super.onLoaded();
        joined = true;
    }

    void blockTicked() {
        if (joinedAtFirstBlockTick == null) {
            joinedAtFirstBlockTick = joined;
        }
    }

    public boolean joined() {
        return joined;
    }

    @Nullable
    public Boolean joinedAtFirstBlockTick() {
        return joinedAtFirstBlockTick;
    }
}
