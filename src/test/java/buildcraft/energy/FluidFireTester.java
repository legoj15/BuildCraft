/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.test.EntityArenaUtil;

/**
 * Game tests for oils and fuels around fire ({@link FluidVolatility}): crude oil burns under vanilla fire, and gas
 * detonates with a chain that stays inside its reach.
 *
 * <p>Both run alone (each has its own {@code test_environment}) because they change global state for a while: the
 * fire-tick game rule and the two fire configs.
 */
public class FluidFireTester {

    // ─── Oil burns ────────────────────────────────────────────────────

    /** The work budget of the burn poll, counted from the cell ticking. The poll's own budget adds
     *  {@code EntityArenaUtil.CELL_READY_TICKS}; the manifest's {@code max_ticks} stays above the sum. */
    private static final int OIL_BURN_WORK_TICKS = 60;

    /** How many vanilla fire ticks are run by hand per game tick. Fire ticks itself every 30 to 39 game ticks, and
     *  crude oil is the SLOW band (it catches rarely and burns down slowly, by design), so waiting for it to happen
     *  organically would take minutes. {@code FireBlock.tick} is vanilla's own code, called through the block state:
     *  only the clock is faster. */
    private static final int FIRE_TICKS_PER_POLL = 25;

    /** Interior of {@link OilWaterInteractionTest#buildBasin}: crude oil fills y=1, fire sits on top at y=2. */
    private static final BlockPos FIRE_REL = new BlockPos(2, 2, 2);

    /**
     * Crude oil placed in a pool and lit with vanilla fire burns: the fire survives on the oil (it has a flammable
     * neighbour), and given fire ticks it spreads to more of the pool's surface or consumes a source. Also pins what
     * fire reads from each band through the real block-state API, and that {@code canBurn} switches it all off.
     */
    public static void testOilBurnsWithVanillaFire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos probe = helper.absolutePos(BlockPos.ZERO);
        Block crude = entry("oil").block().get();

        // What fire reads: crude oil burns, the light fuel burns harder, a heated crude as hard as the light fuel,
        // residue never, gas is flammable too (fire must be attracted to it, then it explodes).
        int crudeFlame = flammability(level, probe, crude);
        int crudeSpread = fireSpread(level, probe, crude);
        int lightFlame = flammability(level, probe, entry("fuel_light").block().get());
        int lightSpread = fireSpread(level, probe, entry("fuel_light").block().get());
        require(crudeFlame > 0 && crudeSpread > 0, "crude oil must be flammable (" + crudeFlame + ", " + crudeSpread + ")");
        require(lightFlame > crudeFlame && lightSpread > crudeSpread,
            "a light fuel must catch and burn faster than crude oil (" + lightFlame + "/" + lightSpread + " vs "
                + crudeFlame + "/" + crudeSpread + ")");
        require(flammability(level, probe, entry("oil_heat_1").block().get()) == lightFlame
                && fireSpread(level, probe, entry("oil_heat_1").block().get()) == lightSpread,
            "heated crude oil must burn like a light fuel");
        for (String residue : new String[] {"oil_residue", "oil_residue_heat_1", "oil_residue_heat_2"}) {
            Block block = entry(residue).block().get();
            require(flammability(level, probe, block) == 0 && fireSpread(level, probe, block) == 0,
                residue + " must never burn");
        }
        require(flammability(level, probe, entry("fuel_gaseous").block().get()) > 0,
            "gas must be flammable so fire is drawn to it");

        boolean canBurnBefore = BCEnergyConfig.canBurnEnabled();
        BCEnergyConfig.canBurn.set(false);
        try {
            require(flammability(level, probe, crude) == 0 && fireSpread(level, probe, crude) == 0,
                "crude oil must not burn while canBurn is off");
        } finally {
            BCEnergyConfig.canBurn.set(canBurnBefore);
        }

        // The burn itself. A fire tick runs only in a ticking chunk and, in an unattended world, only with the
        // fire-tick rule enabled, so both are arranged once the cell ticks and undone on every way out.
        EntityArenaUtil.forceLoadCell(helper);
        boolean[] built = { false };
        Runnable[] restoreRule = { null };
        BooleanSupplier burned = () -> {
            if (!built[0]) {
                built[0] = true;
                BCEnergyConfig.canBurn.set(true);
                restoreRule[0] = enableFireTicks(level);
                OilWaterInteractionTest.buildBasin(helper);
                OilWaterInteractionTest.fillPool(helper, crude);
                helper.setBlock(FIRE_REL, Blocks.FIRE);
                return false;
            }
            if (!helper.getBlockState(FIRE_REL).is(Blocks.FIRE) && !fireAnywhereOnPool(helper)) {
                helper.fail("the fire on the oil went out without burning anything: " + helper.getBlockState(FIRE_REL));
            }
            for (int i = 0; i < FIRE_TICKS_PER_POLL; i++) {
                for (int x = 1; x <= 3; x++) {
                    for (int z = 1; z <= 3; z++) {
                        BlockPos pos = helper.absolutePos(new BlockPos(x, 2, z));
                        BlockState state = level.getBlockState(pos);
                        if (state.is(Blocks.FIRE)) {
                            state.tick(level, pos, level.getRandom());
                        }
                    }
                }
            }
            return oilConsumed(helper, crude) || fireSpreadBeyond(helper);
        };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + OIL_BURN_WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, OIL_BURN_WORK_TICKS, burned,
                () -> "the fire is " + helper.getBlockState(FIRE_REL) + " and the pool is untouched"),
            helper::succeed,
            "the crude oil never burned",
            () -> {
                try {
                    if (restoreRule[0] != null) {
                        restoreRule[0].run();
                    }
                } finally {
                    BCEnergyConfig.canBurn.set(canBurnBefore);
                    // No fire left burning once the rule goes back to what it was.
                    for (int x = 1; x <= 3; x++) {
                        for (int z = 1; z <= 3; z++) {
                            BlockPos rel = new BlockPos(x, 2, z);
                            if (built[0] && helper.getBlockState(rel).is(Blocks.FIRE)) {
                                helper.setBlock(rel, Blocks.AIR);
                            }
                        }
                    }
                }
            });
    }

    private static boolean oilConsumed(GameTestHelper helper, Block crude) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                if (!helper.getBlockState(new BlockPos(x, 1, z)).is(crude)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Fire anywhere on the pool's surface (y=2). */
    private static boolean fireAnywhereOnPool(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                if (helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.FIRE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Fire on a surface cell other than the one the test lit. */
    private static boolean fireSpreadBeyond(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                BlockPos rel = new BlockPos(x, 2, z);
                if (!rel.equals(FIRE_REL) && helper.getBlockState(rel).is(Blocks.FIRE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Lets vanilla fire tick in the unattended test world and returns the undo. A test world switches fire off in
     * three different ways: the 1.21.1 test server turns the fire-tick rule off; 1.21.10 also needs a player nearby
     * unless the rule that allows fire ticks away from players is on; and from 1.21.11 a fire only spreads within a
     * radius of a player (default 128). A test world has no player.
     */
    private static Runnable enableFireTicks(ServerLevel level) {
        //? if >=1.21.11 {
        net.minecraft.world.level.gamerules.GameRules rules = level.getGameRules();
        int before = rules.get(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
        rules.set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, -1, null);
        return () -> rules.set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, before, null);
        //?} elif >=1.21.10 {
        /*net.minecraft.world.level.GameRules.BooleanValue tick =
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOFIRETICK);
        net.minecraft.world.level.GameRules.BooleanValue away =
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_ALLOWFIRETICKAWAYFROMPLAYERS);
        boolean tickBefore = tick.get();
        boolean awayBefore = away.get();
        tick.set(true, null);
        away.set(true, null);
        return () -> {
            tick.set(tickBefore, null);
            away.set(awayBefore, null);
        };*/
        //?} else {
        /*net.minecraft.world.level.GameRules.BooleanValue rule =
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOFIRETICK);
        boolean before = rule.get();
        rule.set(true, null);
        return () -> rule.set(before, null);*/
        //?}
    }

    // ─── Gas detonates ────────────────────────────────────────────────

    /** Work budget of the gas test, counted from the cell ticking: build, first blast, chain, the settle that proves
     *  nothing further goes off, the config-off check, the direct fire catch, and the Nether's chunks loading for the
     *  last stage (worldgen runs off-thread while the ticks keep coming, so that wait is most of this). */
    private static final int GAS_WORK_TICKS = 700;

    /** Ticks the guard gas is watched after the chain has finished, before it is declared untouched. The chain is
     *  over within a dozen ticks (delays of 2 to 5 per generation), so this is several times the longest it can run. */
    private static final int SETTLE_TICKS = 40;

    /** How long the guard is watched with {@code gasExplodes} off. */
    private static final int OFF_SWITCH_TICKS = 10;

    // Layout, in the test's own 6x7 cell (all coordinates relative). Solid obsidian throughout, because a blast of this
    // size would otherwise reach the neighbouring arenas, with a few sealed one-block pockets cut into it:
    //   - the trigger and the fire beside it (one pocket of two cells);
    //   - the link, two blocks from the trigger, sealed from it: a blast's fire cannot reach it, so only the chain's
    //     block tick can set it off;
    //   - the guard, four blocks from both gases (x 2..4, y 2..4, z 4..6 is its own 3x3x3 cube), out of the chain's
    //     reach, and one cell beside it for a fire that is lit only once gasExplodes is off.
    private static final BlockPos TRIGGER = new BlockPos(1, 3, 1);
    private static final BlockPos FIRE_BESIDE_TRIGGER = new BlockPos(1, 3, 2);
    private static final BlockPos LINK = new BlockPos(3, 3, 1);
    private static final BlockPos GUARD = new BlockPos(3, 3, 5);
    private static final BlockPos FIRE_BESIDE_GUARD = new BlockPos(3, 3, 6);

    /** Where the last stage drops a gas source in the Nether: absolute, in a chunk the stage loads itself. */
    private static final BlockPos NETHER_SPOT = new BlockPos(8, 90, 8);

    /**
     * Gaseous fuel with fire beside it explodes; the blast sets off the gas two blocks away, sealed from the fire and
     * the blast, a few ticks later (the chain); gas four blocks from any gas that went off is left alone (the chain is
     * bounded); {@code gasExplodes} off stops both fire beside the gas and the fire catching it from detonating it,
     * and with it on the direct fire catch ({@code onCaughtFire}, what {@code FireBlock} calls) detonates it. Last, a lone gas source set down in the
     * Nether goes off with nothing beside it.
     */
    public static void testGasDetonatesAndChainIsBounded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block gas = entry("fuel_gaseous").block().get();
        boolean canBurnBefore = BCEnergyConfig.canBurnEnabled();
        boolean gasExplodesBefore = BCEnergyConfig.gasExplodesEnabled();
        BCEnergyConfig.canBurn.set(true);
        BCEnergyConfig.gasExplodes.set(true);

        // What counts as an ignition source: fire, soul fire, lava and a lit campfire, nothing else.
        require(FluidVolatility.isIgnitionSource(Blocks.FIRE.defaultBlockState()), "fire must ignite gas");
        require(FluidVolatility.isIgnitionSource(Blocks.SOUL_FIRE.defaultBlockState()), "soul fire must ignite gas");
        require(FluidVolatility.isIgnitionSource(Blocks.LAVA.defaultBlockState()), "lava must ignite gas");
        BlockState campfire = Blocks.CAMPFIRE.defaultBlockState();
        require(FluidVolatility.isIgnitionSource(campfire.setValue(CampfireBlock.LIT, true)),
            "a lit campfire must ignite gas");
        require(!FluidVolatility.isIgnitionSource(campfire.setValue(CampfireBlock.LIT, false)),
            "an unlit campfire must not ignite gas");
        require(!FluidVolatility.isIgnitionSource(Blocks.WATER.defaultBlockState())
                && !FluidVolatility.isIgnitionSource(Blocks.AIR.defaultBlockState())
                && !FluidVolatility.isIgnitionSource(Blocks.STONE.defaultBlockState()),
            "water, air and stone must not ignite gas");

        // The Nether's heat is read by the same call that reads every dimension's.
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        if (nether != null) {
            require(FluidVolatility.isUltraWarm(nether, BlockPos.ZERO), "the Nether must count as ultra-warm");
        }
        require(!FluidVolatility.isUltraWarm(level, helper.absolutePos(BlockPos.ZERO)),
            "the test world's overworld must not count as ultra-warm");

        EntityArenaUtil.forceLoadCell(helper);
        int[] stage = { 0 };
        int[] stageStart = { 0 };
        int[] polls = { 0 };
        BooleanSupplier machine = () -> {
            polls[0]++;
            switch (stage[0]) {
                case 0 -> {
                    buildPockets(helper);
                    helper.setBlock(TRIGGER, gas);
                    helper.setBlock(LINK, gas);
                    helper.setBlock(GUARD, gas);
                    helper.setBlock(FIRE_BESIDE_TRIGGER, Blocks.FIRE);
                    advance(stage, stageStart, polls);
                }
                case 1 -> {
                    // The trigger goes off on a fluid tick; its blast chains to the link.
                    if (!FluidVolatility.isExplosive(helper.getBlockState(TRIGGER))
                            && !FluidVolatility.isExplosive(helper.getBlockState(LINK))) {
                        requireShellIntact(helper);
                        advance(stage, stageStart, polls);
                    }
                }
                case 2 -> {
                    if (polls[0] - stageStart[0] >= SETTLE_TICKS) {
                        require(FluidVolatility.isExplosiveSource(helper.getBlockState(GUARD)),
                            "the guard gas, four blocks from the nearest gas that went off, was set off anyway");
                        requireShellIntact(helper);
                        // Fire beside the guard, and fire catching it, with gasExplodes off must not detonate it ...
                        BCEnergyConfig.gasExplodes.set(false);
                        helper.setBlock(FIRE_BESIDE_GUARD, Blocks.FIRE);
                        catchFire(level, helper, GUARD);
                        advance(stage, stageStart, polls);
                    }
                }
                case 3 -> {
                    if (polls[0] - stageStart[0] >= OFF_SWITCH_TICKS) {
                        require(FluidVolatility.isExplosiveSource(helper.getBlockState(GUARD)),
                            "gas with fire beside it, and caught by fire, exploded although gasExplodes is off");
                        // ... and with it on, the same fire catching does.
                        BCEnergyConfig.gasExplodes.set(true);
                        catchFire(level, helper, GUARD);
                        advance(stage, stageStart, polls);
                    }
                }
                case 4 -> {
                    if (!FluidVolatility.isExplosive(helper.getBlockState(GUARD))) {
                        if (nether == null) {
                            return true;
                        }
                        forceLoadNether(nether, true);
                        advance(stage, stageStart, polls);
                    }
                }
                case 5 -> {
                    if (nether.isPositionEntityTicking(NETHER_SPOT)) {
                        nether.setBlock(NETHER_SPOT, gas.defaultBlockState(), 3);
                        advance(stage, stageStart, polls);
                    }
                }
                default -> {
                    return !FluidVolatility.isExplosive(nether.getBlockState(NETHER_SPOT));
                }
            }
            return false;
        };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + GAS_WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, GAS_WORK_TICKS, machine,
                () -> "stuck in stage " + stage[0] + " (0 build, 1 first blast and chain, 2 settle, 3 gasExplodes off,"
                    + " 4 fire catches the guard, 5 the Nether's chunks load, 6 gas in the Nether)"),
            helper::succeed,
            "the gas never finished detonating",
            () -> {
                BCEnergyConfig.canBurn.set(canBurnBefore);
                BCEnergyConfig.gasExplodes.set(gasExplodesBefore);
                // Leave no gas or fire behind in the shared world.
                if (stage[0] > 0) {
                    for (BlockPos rel : POCKETS) {
                        helper.setBlock(rel, Blocks.AIR);
                    }
                }
                if (nether != null && stage[0] >= 5) {
                    nether.setBlock(NETHER_SPOT, Blocks.AIR.defaultBlockState(), 3);
                    forceLoadNether(nether, false);
                }
            });
    }

    /** Forces (or releases) the 5x5 chunks around {@link #NETHER_SPOT}, the same ring an entity-ticking chunk needs
     *  ({@code EntityArenaUtil#forceLoadEntityArena}). */
    private static void forceLoadNether(ServerLevel nether, boolean forced) {
        int chunkX = NETHER_SPOT.getX() >> 4;
        int chunkZ = NETHER_SPOT.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                nether.setChunkForced(chunkX + dx, chunkZ + dz, forced);
            }
        }
    }

    private static void advance(int[] stage, int[] stageStart, int[] polls) {
        stage[0]++;
        stageStart[0] = polls[0];
    }

    private static void buildPockets(GameTestHelper helper) {
        fill(helper, 0, 2, 0, 4, 4, 3, Blocks.OBSIDIAN);
        fill(helper, 2, 2, 4, 4, 4, 6, Blocks.OBSIDIAN);
        for (BlockPos rel : POCKETS) {
            helper.setBlock(rel, Blocks.AIR);
        }
    }

    /** The cells cut out of the obsidian. */
    private static final List<BlockPos> POCKETS =
        List.of(TRIGGER, FIRE_BESIDE_TRIGGER, LINK, GUARD, FIRE_BESIDE_GUARD);

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (BlockPos rel : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            helper.setBlock(rel.immutable(), block);
        }
    }

    /** The blasts must have stayed inside the obsidian: every block of both boxes outside the pockets is still there. */
    private static void requireShellIntact(GameTestHelper helper) {
        requireBoxIntact(helper, new BlockPos(0, 2, 0), new BlockPos(4, 4, 3));
        requireBoxIntact(helper, new BlockPos(2, 2, 4), new BlockPos(4, 4, 6));
    }

    private static void requireBoxIntact(GameTestHelper helper, BlockPos from, BlockPos to) {
        for (BlockPos rel : BlockPos.betweenClosed(from, to)) {
            if (!POCKETS.contains(rel) && !helper.getBlockState(rel).is(Blocks.OBSIDIAN)) {
                helper.fail("the blast broke the obsidian at " + rel + ": " + helper.getBlockState(rel));
            }
        }
    }

    /** Calls {@code onCaughtFire} on the block at {@code rel} exactly as {@code FireBlock} (and a dispenser's flint
     *  and steel) does. The signature changed twice: void on 1.21.1, a boolean from 1.21.10, and 26.3 adds the item. */
    private static void catchFire(ServerLevel level, GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        BlockState state = level.getBlockState(abs);
        //? if >=26.3 {
        state.onCaughtFire(level, abs, Direction.UP, null, net.minecraft.world.item.ItemStack.EMPTY);
        //?} else {
        /*state.onCaughtFire(level, abs, Direction.UP, null);*/
        //?}
    }

    // ─── Shared ───────────────────────────────────────────────────────

    private static BCEnergyFluids.FluidEntry entry(String name) {
        return BCEnergyFluids.ALL.stream().filter(e -> e.name().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalStateException("no BuildCraft energy fluid named " + name));
    }

    private static int flammability(ServerLevel level, BlockPos pos, Block block) {
        return block.defaultBlockState().getFlammability(level, pos, Direction.UP);
    }

    private static int fireSpread(ServerLevel level, BlockPos pos, Block block) {
        return block.defaultBlockState().getFireSpreadSpeed(level, pos, Direction.UP);
    }

    /** Throws (failing the test) when {@code condition} is false. Not {@code helper.assertTrue}: that call is
     *  rerouted textually on the pre-1.21.11 nodes. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
