package buildcraft.energy.generation;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.lib.misc.data.Box;

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
     * SurfacePool produces a roughly-circular oil footprint with mild radial noise:
     * centre cell is oil, far corners are unchanged. Exercises the new disc-with-noise
     * pattern.
     */
    public static void testSurfacePoolCleanShape(GameTestHelper helper) {
        // Flat stone floor at relative y=1 (so the heightmap sees it as the surface).
        int floorY = 1;
        int span = 24;
        for (int dx = 0; dx < span; dx++) {
            for (int dz = 0; dz < span; dz++) {
                helper.setBlock(new BlockPos(dx, floorY, dz), Blocks.STONE);
            }
        }
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
        int floorY = 1;
        int span = 24;
        for (int dx = 0; dx < span; dx++) {
            for (int dz = 0; dz < span; dz++) {
                helper.setBlock(new BlockPos(dx, floorY, dz), Blocks.STONE);
            }
        }
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
}
