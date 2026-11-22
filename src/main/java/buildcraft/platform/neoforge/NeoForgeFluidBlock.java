/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
//? if >=26.3 {
import net.minecraft.world.item.ItemStack;
//?}
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

import buildcraft.platform.LoaderHook;
import buildcraft.platform.hook.FluidBlockExtras;

/**
 * A BuildCraft fluid's {@code LiquidBlock} on NeoForge: the vanilla block with {@link FluidBlockExtras} behind its
 * ticks and NeoForge's patched fire hooks ({@code IBlockExtension}). Built by {@link NeoForgeFluids#liquidBlock}.
 * NeoForge has the one {@code LiquidBlock} constructor, which takes the fluid itself.
 */
public final class NeoForgeFluidBlock extends LiquidBlock implements FluidBlockExtras.Host {
    private final FluidBlockExtras extras;

    public NeoForgeFluidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties, FluidBlockExtras extras) {
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

    @LoaderHook("neoforge:IBlockExtension#getFlammability")
    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return fireBurnOdds(state);
    }

    @LoaderHook("neoforge:IBlockExtension#getFireSpreadSpeed")
    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return fireIgniteOdds(state);
    }

    @LoaderHook("neoforge:IBlockExtension#ignitedByLava")
    @Override
    public boolean ignitedByLava(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return lavaIgnites(state);
    }

    // onCaughtFire: void on 1.21.1, boolean from 1.21.10 (true = the block was set on fire), and NeoForge 26.3 adds the
    // item that lit it. All of them hand the work to catchFire.
    //? if >=26.3 {
    @LoaderHook("neoforge:IBlockExtension#onCaughtFire")
    @Override
    public boolean onCaughtFire(BlockState state, Level level, BlockPos pos, Direction direction, LivingEntity igniter,
                                ItemStack ignitionItem) {
        return catchFire(state, level, pos);
    }
    //?} elif >=1.21.10 {
    /*@LoaderHook("neoforge:IBlockExtension#onCaughtFire")
    @Override
    public boolean onCaughtFire(BlockState state, Level level, BlockPos pos, Direction direction, LivingEntity igniter) {
        return catchFire(state, level, pos);
    }*/
    //?} else {
    /*@LoaderHook("neoforge:IBlockExtension#onCaughtFire")
    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, Direction direction, LivingEntity igniter) {
        catchFire(state, level, pos);
    }*/
    //?}
}
//?}
