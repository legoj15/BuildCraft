/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

import buildcraft.platform.LoaderHook;
import buildcraft.platform.hook.FluidBlockExtras;

/^*
 * A BuildCraft fluid's {@code LiquidBlock} on MinecraftForge: the vanilla block with {@link FluidBlockExtras} behind
 * its ticks and Forge's patched fire hooks ({@code IForgeBlock}). Built by {@link ForgeFluids#liquidBlock}.
 *
 * <p><b>Constructor.</b> Forge deprecates the {@code LiquidBlock} constructor that takes the fluid for one that takes a
 * supplier, and keeps the fluid in the supplier until the block first needs it ({@code getFluid}), where the other
 * resolves it as the block is built. The supplier is BuildCraft's source-fluid holder, which is bound once the fluid
 * registry has registered.
 *
 * <p><b>Lava.</b> Forge has no per-block lava-ignition hook: its {@code LavaFluid} tests the block state's own
 * {@code ignitedByLava} flag (set on the properties before the block is built, see {@code BCEnergyFluids}) and then
 * {@code IForgeBlock#isFlammable}, which is {@code getFlammability} above zero, so the pair equals
 * {@code lavaIgnites(state)}: every band that burns has a flammability above zero.
 ^/
public final class ForgeFluidBlock extends LiquidBlock implements FluidBlockExtras.Host {
    private final FluidBlockExtras extras;

    public ForgeFluidBlock(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties properties,
                           FluidBlockExtras extras) {
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

    @LoaderHook("forge:IForgeBlock#getFlammability")
    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return fireBurnOdds(state);
    }

    @LoaderHook("forge:IForgeBlock#getFireSpreadSpeed")
    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return fireIgniteOdds(state);
    }

    // onCaughtFire: void on 1.21.1, boolean from 1.21.10 (true = the block was set on fire); Forge has the 5-argument
    // forms on every line. Both hand the work to catchFire.
    //? if >=1.21.10 {
    @LoaderHook("forge:IForgeBlock#onCaughtFire")
    @Override
    public boolean onCaughtFire(BlockState state, Level level, BlockPos pos, Direction direction, LivingEntity igniter) {
        return catchFire(state, level, pos);
    }
    //?} else {
    /^@LoaderHook("forge:IForgeBlock#onCaughtFire")
    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, Direction direction, LivingEntity igniter) {
        catchFire(state, level, pos);
    }^/
    //?}
}
*///?}
