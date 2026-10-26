/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
//? if >=1.21.11 {
import net.minecraft.world.attribute.EnvironmentAttributes;
//?}

import buildcraft.energy.block.BCFluidBlock;
import buildcraft.lib.misc.BlockUtil;

/**
 * How placed BuildCraft oils and fuels behave around fire: one volatility scale for all thirty fluid variants,
 * derived once at registration from each fluid's own table row ({@link BCEnergyFluids}'s {@code FLUID_DATA}) and
 * its heat tier, never tuned per fluid.
 *
 * <p>The scale is a {@link Band}. It decides two numbers that vanilla fire reads from the block (see
 * {@link Band#fireSpreadSpeed} and {@link Band#flammability}) and, for the top band, whether the fluid explodes.
 * Everything here concerns <b>placed fluid blocks</b> only: tanks and pipes hold their fluid as data, are never
 * ignited, and react to nothing (a nearby blast can still break them like any block).
 *
 * <p><b>Detonation.</b> An {@link Band#EXPLOSIVE} source block explodes when something ignites it: fire catching it
 * ({@code BCFluidBlock#onCaughtFire}), an ignition source beside it ({@link #isIgnitionSource}: fire, soul fire,
 * lava, a lit campfire), or a dimension that evaporates water ({@link #isUltraWarm}, the Nether) -- the last two are
 * noticed by the fluid tick that placing the block or changing a neighbour already schedules. Only source blocks
 * start a detonation; flowing gas burns away like any other fluid but a detonation consumes it too.
 *
 * <p>A detonation is bounded so a spilled refinery cannot freeze the server: one blast clears every explosive fluid
 * block in the 3x3x3 around its trigger and fires <i>one</i> {@link #BLAST_POWER} explosion; the chain then goes on
 * as <b>block ticks</b> (a delay of 2 to 5 ticks) on the explosive sources in the shell out to {@link #CHAIN_REACH}.
 * Liquid blocks have explosion resistance 100, so a blast never removes one itself: the ticks are the only way a
 * detonation spreads, and one block tick per position (scheduled ticks dedupe) caps how often a source can be
 * asked. A source that a blast already cleared simply never ticks.
 */
public final class FluidVolatility {

    private FluidVolatility() {}

    /**
     * The volatility bands, lightest to most violent. The two numbers are the ones vanilla's {@code FireBlock} reads
     * through {@code getFireSpreadSpeed} and {@code getFlammability}, so they only mean something against its
     * formulas:
     * <ul>
     * <li>{@code fireSpreadSpeed} is the odds, per fire tick and empty neighbouring cell, that fire spreads onto the
     *     surface of this block: the cell ignites when {@code random(100) <= (speed + 40 + 7 * difficulty) /
     *     (fireAge + 30)}. Planks and logs use 5 (about 2%), wool 30, leaves and grass 60 (about 4%).</li>
     * <li>{@code flammability} is the odds out of 300 (250 vertically) that fire next to the block burns it away on a
     *     fire tick, which comes every 30 to 39 game ticks. Logs use 5 (under 2%), planks 20 (7%), grass 100 (33%).</li>
     * </ul>
     */
    public enum Band {
        /** Never burns: residue, at every heat. */
        INERT(0, 0),
        /** Crude, heavy and dense oil and the heavier fuels while cool: catches rarely, burns down slowly (log
         *  numbers). */
        SLOW(5, 5),
        /** The light fuels, and any other fuel that has been heated: catches readily and burns away quickly (the
         *  vanilla maximum, grass numbers), so fire races across a pool. */
        FAST(60, 100),
        /** Every gaseous variant: as flammable as {@link #FAST}, but ignition is an explosion. With
         *  {@code gasExplodes} off it simply burns like {@link #FAST}. */
        EXPLOSIVE(60, 100);

        /** What {@code getFireSpreadSpeed} answers for a block of this band. */
        public final int fireSpreadSpeed;
        /** What {@code getFlammability} answers for a block of this band. */
        public final int flammability;

        Band(int fireSpreadSpeed, int flammability) {
            this.fireSpreadSpeed = fireSpreadSpeed;
            this.flammability = flammability;
        }
    }

    /** Fluids that boil at this heat tier or below are the light fuels: volatile even while cool. */
    private static final int LIGHT_BOIL_POINT = 1;

    /** A fluid at or above this heat tier has been heated, which moves a slow fluid up to {@link Band#FAST}. */
    private static final int HEATED = 1;

    /** TNT-strength, the same blast upstream 7.1.x used. */
    private static final float BLAST_POWER = 4.0F;

    /** Chebyshev distance from a detonation out to which explosive sources are scheduled to follow it. The cleared
     *  cube covers distance 1, so the chain reaches the shell between 2 and this value. */
    static final int CHAIN_REACH = 3;

    /** Fewest and most ticks (inclusive) a chained source waits before it detonates; TNT-like, and long enough that
     *  the blast that scheduled it is over. */
    private static final int CHAIN_MIN_DELAY = 2;
    private static final int CHAIN_MAX_DELAY = 5;

    /**
     * The band of a fluid variant, from its table row and heat tier.
     *
     * @param flammable the table's flammable column (0 = never burns)
     * @param boilPoint the table's boil point: the heat tier at which the fluid turns to gas
     * @param heat the variant's heat tier (0 cool, 1 hot, 2 searing)
     * @param gaseous whether this variant is a gas (fluid type density below zero), which overrides everything else
     */
    static Band derive(boolean flammable, int boilPoint, int heat, boolean gaseous) {
        if (gaseous) {
            return Band.EXPLOSIVE;
        }
        if (!flammable) {
            return Band.INERT;
        }
        return boilPoint <= LIGHT_BOIL_POINT || heat >= HEATED ? Band.FAST : Band.SLOW;
    }

    // ─── Ignition ─────────────────────────────────────────────────────

    /** Whether {@code state} sets a neighbouring gas off: fire or soul fire, lava, or a lit campfire. */
    public static boolean isIgnitionSource(BlockState state) {
        return state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA) || CampfireBlock.isLitCampfire(state);
    }

    /** Whether any of the six cells around {@code pos} is an {@linkplain #isIgnitionSource ignition source}. */
    static boolean isIgnitionNearby(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (isIgnitionSource(level.getBlockState(pos.relative(direction)))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the dimension at {@code pos} is hot enough to ignite gas on its own: the Nether, or any dimension a
     *  pack marks as evaporating water. */
    static boolean isUltraWarm(Level level, BlockPos pos) {
        //? if >=1.21.11 {
        return level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos);
        //?} else {
        /*return level.dimensionType().ultraWarm();*/
        //?}
    }

    /** Whether {@code state} is an explosive BuildCraft fluid block, source or flowing. */
    static boolean isExplosive(BlockState state) {
        return state.getBlock() instanceof BCFluidBlock fluid && fluid.volatility() == Band.EXPLOSIVE;
    }

    /** Whether {@code state} is an explosive BuildCraft fluid <i>source</i> block: the only kind that detonates. */
    static boolean isExplosiveSource(BlockState state) {
        return isExplosive(state) && state.getFluidState().isSource();
    }

    // ─── Triggers ─────────────────────────────────────────────────────

    /**
     * The fluid-tick trigger: an explosive source with an ignition source beside it, or in a dimension that evaporates
     * water, detonates. Runs from the tick that {@code LiquidBlock.onPlace}, {@code neighborChanged} and
     * {@code updateShape} schedule, never from {@code onPlace} itself, so the blast does not fire mid-{@code setBlock}.
     * Liquid Nether oil and the liquid fuels refined from it stay usable: only an {@link Band#EXPLOSIVE} fluid reacts
     * to ambient heat.
     *
     * @return whether the fluid was consumed by a blast (the caller must stop ticking it).
     */
    static boolean igniteExposedGas(Level level, BlockPos pos, FluidState fluidState, Band band) {
        if (band != Band.EXPLOSIVE || !fluidState.isSource() || level.isClientSide() || !BCEnergyConfig.gasExplodesEnabled()) {
            return false;
        }
        if (!isIgnitionNearby(level, pos) && !isUltraWarm(level, pos)) {
            return false;
        }
        detonate(level, pos);
        return true;
    }

    /**
     * The fire trigger, for {@code onCaughtFire}: vanilla fire (or a dispenser's flint and steel) caught the block at
     * {@code pos}. An explosive source detonates on the spot. That is safe from inside {@code FireBlock}'s tick, which
     * only goes on to replace the cell with fire or air, and it is the only way to fire a block that fire is about to
     * replace (a block tick scheduled here would find the cell already gone).
     *
     * @return whether the block caught fire, which is what {@code onCaughtFire} reports: {@code true} for anything
     *         that can burn (a source that exploded included), {@code false} when burning is switched off.
     */
    public static boolean catchFire(Level level, BlockPos pos, BlockState state, Band band) {
        if (band == Band.INERT || !BCEnergyConfig.canBurnEnabled()) {
            return false;
        }
        if (band == Band.EXPLOSIVE && !level.isClientSide() && state.getFluidState().isSource()
                && BCEnergyConfig.gasExplodesEnabled()) {
            detonate(level, pos);
        }
        return true;
    }

    /**
     * The chain trigger, for the explosive block's own scheduled block tick: detonates if the cell is still an
     * explosive source. A blast between the scheduling and the tick may have cleared it (the tick is then never
     * delivered) or the config may have been switched off.
     */
    public static void chainTick(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide() && isExplosiveSource(state) && BCEnergyConfig.gasExplodesEnabled()) {
            detonate(level, pos);
        }
    }

    // ─── Detonation ───────────────────────────────────────────────────

    /**
     * Detonates the explosive fluid at {@code at}: clears every explosive fluid block, source or flowing, in the 3x3x3
     * around it, fires one explosion there, and schedules the sources in the shell beyond that cube (out to
     * {@link #CHAIN_REACH}) to follow after a short delay. Only chunks already in memory are read or written.
     */
    static void detonate(Level level, BlockPos at) {
        BlockPos centre = at.immutable();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Clear first without block updates (as upstream did), so the blast sees an empty cell and neighbouring gas
        // does not re-tick against a half-removed block.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    cursor.setWithOffset(centre, dx, dy, dz);
                    if (BlockUtil.isChunkPresent(level, cursor) && isExplosive(level.getBlockState(cursor))) {
                        level.setBlock(cursor, air, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        level.explode(null, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, BLAST_POWER, true,
            Level.ExplosionInteraction.BLOCK);

        // The chain: block ticks, not fluid ticks (LiquidBlock keeps those for flow), one per position at most.
        for (int dx = -CHAIN_REACH; dx <= CHAIN_REACH; dx++) {
            for (int dy = -CHAIN_REACH; dy <= CHAIN_REACH; dy++) {
                for (int dz = -CHAIN_REACH; dz <= CHAIN_REACH; dz++) {
                    if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) < 2) {
                        continue;
                    }
                    cursor.setWithOffset(centre, dx, dy, dz);
                    if (!BlockUtil.isChunkPresent(level, cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (isExplosiveSource(state)) {
                        int delay = CHAIN_MIN_DELAY + level.getRandom().nextInt(CHAIN_MAX_DELAY - CHAIN_MIN_DELAY + 1);
                        // The tick keeps the position it is given, so it must be an immutable copy.
                        level.scheduleTick(cursor.immutable(), state.getBlock(), delay);
                    }
                }
            }
        }
    }
}
