/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

import buildcraft.platform.hook.FluidBlockExtras;

/^*
 * A BuildCraft fluid's {@code LiquidBlock} on Fabric: the vanilla block with {@link FluidBlockExtras} behind its ticks.
 * Built by {@link FabricFluids#liquidBlock}. Fabric has no patched fire hooks: its {@code FireBlock} and {@code LavaFluid}
 * mixins ask {@link FluidBlockExtras.Host} (a {@code FireReactiveBlock}) directly.
 ^/
public final class FabricFluidBlock extends LiquidBlock implements FluidBlockExtras.Host {
    private final FluidBlockExtras extras;

    public FabricFluidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties, FluidBlockExtras extras) {
        super(fluid, properties);
        this.extras = extras;
    }

    @Override
    public FluidBlockExtras extras() {
        return extras;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        extras.tick(state, level, pos, random);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        extras.animateTick(state, level, pos, random);
    }
}
*///?}
