/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.generation;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.FarChunk;

/**
 * Game tests for the oil-generation pipeline's structure primitives:
 * {@link OilGenStructure.SurfacePool} clean disc placement, the tree-aware
 * {@link OilGenStructure.PatternTerrainHeight} path, and the legacy ocean
 * (non-tree-aware) path.
 *
 * <p>Each test builds an arena directly with {@code helper.setBlock} and calls
 * the structure's {@code generate} method against the structure's own box —
 * bypassing biome-driven worldgen so the pure placement behaviour is exercised
 * in isolation.
 */
public class OilGenStructureTester {

    /**
     * Worldgen-deadlock guard: {@link OilGenStructure#setOil} must NOT touch a chunk that isn't
     * loaded. During a real {@code ChunkEvent.Load}, a setBlock into an unloaded neighbour (e.g. a
     * rich-biome spout tube crossing a chunk border) forces a blocking
     * {@code ServerChunkCache.getChunk} that parks the server thread forever — the freeze. The
     * hasChunk guard makes it a no-op. This verifies the guard skips a far, unloaded chunk without
     * loading it (without the guard, setOil would generate that chunk and hasChunk would flip true).
     */
    public static void testSetOilSkipsUnloadedChunk(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        // Far from the arena and spawn — guaranteed unloaded.
        BlockPos arena = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockPos farPos = new BlockPos(arena.getX() + 8000, level.getMinY() + 40, arena.getZ() + 8000);
        int cx = farPos.getX() >> 4;
        int cz = farPos.getZ() >> 4;
        helper.assertFalse(level.hasChunk(cx, cz), "Precondition: the far chunk must start unloaded");
        OilGenStructure.setOil(level, farPos);
        helper.assertFalse(level.hasChunk(cx, cz),
                "setOil must skip an unloaded chunk (not load/generate it) — the worldgen-deadlock guard");
        helper.succeed();
    }

    /**
     * Worldgen-deadlock guard #2 (the leaf that survived the first, write-only fix): oil generation must
     * NOT replace a block that has a {@link net.minecraft.world.level.block.entity.BlockEntity}. Removing
     * one runs {@code Level.removeBlockEntity → updateNeighbourForOutputSignal} (a NeoForge patch fired
     * unconditionally to refresh comparator outputs), which reads the horizontal neighbours and
     * force-loads any that are only ticket-eligible for FULL but not yet generated — parking the server
     * thread forever (the freeze, reached via a comparator update rather than a direct cross-chunk write —
     * confirmed by a live thread dump at {@code SurfacePool.generateWithin}). The fix routes every oil-gen
     * write through {@code setWorldgenBlock}, which skips block-entity blocks. This places a chest and
     * asserts {@code setOil} leaves it intact (without the guard, setOil would replace it with oil and the
     * chest would be gone). In the loaded test arena the neighbours exist so the call returns rather than
     * deadlocking; the preserved chest is the invariant that prevents the deadlock in a live chunk-load.
     */
    public static void testSetOilSkipsBlockEntity(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, Blocks.CHEST);
        helper.assertTrue(level.getBlockState(abs).hasBlockEntity(),
                "Precondition: the placed chest must have a block entity");
        OilGenStructure.setOil(level, abs);
        helper.assertTrue(level.getBlockState(abs).is(Blocks.CHEST),
                "setOil must skip a block-entity block (got " + level.getBlockState(abs)
                        + ") — the comparator-update worldgen-deadlock guard");
        helper.succeed();
    }

    /**
     * Worldgen-deadlock guard #3 (the leaf that survived the second fix): placing an oil FLUID block runs
     * {@code LiquidBlock.onPlace -> FluidInteractionRegistry.canInteract}, which reads the fluid state of the
     * block's HORIZONTAL neighbours. {@code Level.getFluidState} force-loads via
     * {@code getChunkAt(requireChunk=true)} — an UNCONDITIONAL force-load, unlike the {@code load=false}
     * biome read — so an oil block on a chunk edge whose neighbour is ungenerated force-loads that neighbour
     * and parks the server thread; and because the force-loaded neighbour runs its OWN oil-gen (placing oil
     * that reads ITS neighbour) the {@code managedBlock}s nest and cascade into a hard freeze (reproduced live
     * on 1.21.1 with Biomes O'Plenty; GitHub issue #26). {@code setWorldgenBlock} skips a fluid placement
     * whose horizontal neighbour chunk isn't generated. This force-loads ONE isolated chunk (its neighbours
     * stay ungenerated), places oil on its east edge, and asserts the east neighbour is NOT force-loaded —
     * without the guard, {@code canInteract}'s {@code getFluidState} would generate it.
     */
    public static void testSetOilSkipsUnloadedHorizontalNeighbour(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        // An isolated chunk far from the arena/spawn (and from other tests' far-chunk references): loaded ALONE (a
        // forced chunk's ticket would queue its neighbours to load too, and the one next door could turn present
        // before the assertions run), so its four neighbours stay ungenerated.
        FarChunk far = FarChunk.load(helper, 777);
        helper.assertTrue(far.isAbsent(1, 0),
                "Precondition: its east neighbour must stay ungenerated (not present at FULL)");
        // Oil on the east edge (local x = 15) — its east neighbour is the ungenerated chunk (cx+1, cz).
        BlockPos edge = far.pos(15, level.getMinY() + 40, 8);
        OilGenStructure.setOil(level, edge);
        boolean neighbourStillUnloaded = far.isAbsent(1, 0);
        helper.assertTrue(neighbourStillUnloaded,
                "setOil must NOT force-load an ungenerated horizontal neighbour via LiquidBlock.onPlace -> "
                        + "FluidInteractionRegistry.canInteract (the fluid-interaction worldgen-deadlock guard)");
        helper.succeed();
    }

    /** Side of the flat floor the surface-pool tests lay: wide enough for the largest medium pool (radius 8). */
    private static final int POOL_FLOOR_SPAN = 24;

    /** Relative y of that floor: above every arena, so it cannot overwrite (or be covered by) another test's blocks. */
    private static final int POOL_FLOOR_Y = EntityArenaUtil.ABOVE_ARENAS_Y;

    /** Lays the {@link #POOL_FLOOR_SPAN}-square stone floor the surface-pool tests generate on. It is far wider than
     *  the test's own 6x7 cell, so where it goes decides which other tests it overwrites: see
     *  {@link EntityArenaUtil#ABOVE_ARENAS_Y}. */
    private static void layPoolFloor(GameTestHelper helper) {
        for (int dx = 0; dx < POOL_FLOOR_SPAN; dx++) {
            for (int dz = 0; dz < POOL_FLOOR_SPAN; dz++) {
                helper.setBlock(new BlockPos(dx, POOL_FLOOR_Y, dz), Blocks.STONE);
            }
        }
    }

    /**
     * The pool floor must leave every block of the test's own arena cell alone. The arenas of one batch run at the
     * same time on a grid 6 apart in X and 7 apart in Z, so a 24x24 floor at the arena's own level overwrote the
     * cells of up to fifteen neighbours with stone, whenever it was laid after a neighbour had built its rig: the
     * chest of {@code pipe_force_insert_entry_leg} turned to stone one tick into that test and the emerald it had
     * force-inserted was dropped, one run in about forty. Fills the cell with glass, lays the floor, and fails on the
     * first block that is no longer glass.
     */
    public static void testPoolFloorSparesTheArenaCell(GameTestHelper helper) {
        try {
            forEachCellPos(pos -> helper.setBlock(pos, Blocks.GLASS));
            layPoolFloor(helper);
            forEachCellPos(pos -> {
                if (!helper.getBlockState(pos).is(Blocks.GLASS)) {
                    helper.fail("The pool floor overwrote the arena cell at " + pos + " with "
                            + helper.getBlockState(pos) + " - it must be laid above the arenas");
                }
            });
        } finally {
            forEachCellPos(pos -> helper.setBlock(pos, Blocks.AIR));
        }
        helper.succeed();
    }

    private static void forEachCellPos(java.util.function.Consumer<BlockPos> action) {
        for (int x = 0; x < EntityArenaUtil.CELL_SIZE_X; x++) {
            for (int y = 0; y < EntityArenaUtil.CELL_SIZE_Y; y++) {
                for (int z = 0; z < EntityArenaUtil.CELL_SIZE_Z; z++) {
                    action.accept(new BlockPos(x, y, z));
                }
            }
        }
    }

    /**
     * SurfacePool produces a roughly-circular oil footprint with mild radial noise:
     * centre cell is oil, far corners are unchanged. Exercises the new disc-with-noise
     * pattern.
     */
    public static void testSurfacePoolCleanShape(GameTestHelper helper) {
        // Flat stone floor (so the surface scan finds it as the surface).
        int floorY = POOL_FLOOR_Y;
        layPoolFloor(helper);
        // Centre the pool at relative (12, floorY, 12). The factory takes absolutes.
        BlockPos relativeCenter = new BlockPos(12, floorY, 12);
        BlockPos absCenter = helper.absolutePos(relativeCenter);
        // Use a seeded RNG so the noise pattern is deterministic across runs.
        Random rand = new Random(0xCAFEBABEL);
        OilGenStructure pool = OilGenerator.createSurfacePoolMedium(absCenter, rand);
        pool.generate(helper.getLevel(), pool.box);

        helper.succeedIf(() -> {
            // Centre cell should now be oil.
            if (!helper.getBlockState(relativeCenter).is(
                    BCEnergyFluids.OIL_COOL.source().get().defaultFluidState().createLegacyBlock().getBlock())) {
                helper.fail("Pool centre is not oil at " + relativeCenter
                        + " (got " + helper.getBlockState(relativeCenter) + ")");
            }
            // Far corner (well outside any plausible pool radius) should still be stone.
            BlockPos farCorner = new BlockPos(0, floorY, 0);
            if (!helper.getBlockState(farCorner).is(Blocks.STONE)) {
                helper.fail("Pool spilled onto far corner " + farCorner
                        + " — expected STONE, got " + helper.getBlockState(farCorner));
            }
        });
    }

    /**
     * SurfacePool is defensively tree-aware: if a modded biome ends up in the rich
     * tier with trees, the pool must clear them rather than leaving floating logs.
     * Plants a 15-log jungle trunk + dense canopy (~65 tree blocks — enough to
     * overflow the historical 2048-budget-with-26-fanout that caused floating
     * logs in early in-client testing) and asserts the entire trunk is cleared.
     */
    public static void testSurfacePoolClearsTallTreeFully(GameTestHelper helper) {
        int floorY = POOL_FLOOR_Y;
        layPoolFloor(helper);
        BlockPos relativeCenter = new BlockPos(12, floorY, 12);
        for (int dy = 1; dy <= 15; dy++) {
            helper.setBlock(relativeCenter.above(dy), Blocks.JUNGLE_LOG);
        }
        for (int layer = 0; layer < 2; layer++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    helper.setBlock(relativeCenter.offset(dx, 14 + layer, dz), Blocks.JUNGLE_LEAVES);
                }
            }
        }

        BlockPos absCenter = helper.absolutePos(relativeCenter);
        Random rand = new Random(0xC0FFEEL);
        OilGenStructure pool = OilGenerator.createSurfacePoolMedium(absCenter, rand);
        pool.generate(helper.getLevel(), pool.box);

        helper.succeedIf(() -> {
            for (int dy = 1; dy <= 15; dy++) {
                BlockPos pos = relativeCenter.above(dy);
                if (helper.getBlockState(pos).is(BlockTags.LOGS)) {
                    helper.fail("Tall-tree BFS left a log at " + pos
                            + " (dy=" + dy + ") — budget exhausted before reaching the trunk base.");
                }
            }
        });
    }

    /**
     * End-to-end wiring check for the feature-based oil pipeline (the {@code ChunkEvent.Load}
     * replacement): the {@code buildcraftunofficial:add_oil} biome modifier must have attached the
     * {@code buildcraftunofficial:oil} placed feature to EVERY biome at
     * {@code top_layer_modification}. Reads the (arbitrary) arena biome's generation settings — if
     * the feature/modifier registration, any of the three worldgen JSONs, or the modifier's
     * every-biome application broke, the feature is absent here and no oil would generate anywhere.
     */
    public static void testOilFeatureAttachedToBiomes(GameTestHelper helper) {
        net.minecraft.world.level.biome.Biome biome =
                helper.getLevel().getBiome(helper.absolutePos(new BlockPos(0, 1, 0))).value();
        int step = net.minecraft.world.level.levelgen.GenerationStep.Decoration.TOP_LAYER_MODIFICATION.ordinal();
        java.util.List<net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.placement.PlacedFeature>> features =
                biome.getGenerationSettings().features();
        helper.assertTrue(features.size() > step,
                "Biome has no feature list at top_layer_modification — the add_oil biome modifier did not apply");
        boolean found = false;
        for (net.minecraft.core.Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature> holder
                : features.get(step)) {
            if (holder.unwrapKey()
                    .map(key -> "buildcraftunofficial:oil".equals(buildcraft.lib.misc.RegistryKeyUtil.id(key).toString()))
                    .orElse(false)) {
                found = true;
                break;
            }
        }
        helper.assertTrue(found,
                "buildcraftunofficial:oil placed feature missing from the biome's top_layer_modification step "
                        + "— feature registration, a worldgen JSON, or the add_oil biome modifier is broken");
        helper.succeed();
    }
}
