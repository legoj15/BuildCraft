/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.block;

import java.util.Map;
import java.util.TreeMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.mj.MjAPI;
import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TilePump;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadeStateManager;
import buildcraft.silicon.plug.PluggableFacade;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Water must never wash a BuildCraft block away.
 *
 * <p>Vanilla flowing water (and an emptied water bucket) <em>replaces</em> any block that is not
 * "solid" — {@code FlowingFluid.canHoldAnyFluid} lets water into a cell whose state reports
 * {@code blocksMotion()==false}, and {@code spreadTo} then destroys the block unless it is a
 * {@link LiquidBlockContainer}. "Solid" is a legacy heuristic on the <em>static</em> collision shape:
 * the bounding box's <em>average edge length</em> must be at least ~0.73, or it must be a full block
 * tall ({@code forceSolidOn} overrides it; a {@code dynamicShape} block has no static shape and is never
 * solid). So a collision-less marker and a pipe are floodable, while the 9/16-tall laser tables (average
 * edge 0.85), the laser, the tank and the mining tube are not. The fix is the vanilla one —
 * {@code SimpleWaterloggedBlock}, via {@link BCWaterlogging} — which makes the block a
 * {@code LiquidBlockContainer}: a water source then waterlogs it, flowing water is held back, and lava
 * (which it refuses) is held back too.
 *
 * <p>Facades never needed their own fix: a facade only ever exists as a pluggable on a pipe holder (the
 * facade item has no block form, and pluggables can only be placed on a holder that already has a pipe),
 * and the pipe holder has been waterloggable since the pipe fix. {@link #testFacadedPipeWaterlogsBetweenSources}
 * pins that.
 *
 * <p>Basin layout (all within one arena cell, so fluid can never leak into a neighbouring test):
 * <pre>
 *   z=0   #  #  #        y=1: stone floor under x=0..4, z=0..2
 *   z=1   #  W  S  W  #  y=2: W = water/lava source, S = subject block, # = stone wall
 *   z=2   #  #  #
 *        x=0 1  2  3  4
 * </pre>
 * Two water sources flanking the subject on a solid floor make vanilla compute a new <em>source</em>
 * for the subject's cell (the infinite-water rule) — the only kind of water that can waterlog a block.
 */
public class BlockWaterloggingTester {

    private static final String NAMESPACE = "buildcraftunofficial";

    /**
     * BuildCraft blocks that are deliberately left floodable, mapped to the reason. Empty: every
     * non-solid BuildCraft block waterlogs. Adding an entry is a conscious design decision.
     */
    private static final Map<String, String> DELIBERATELY_FLOODABLE = Map.of();

    /** Basin cells — shared with {@code PipeWaterloggingTester}, which reuses the basin. */
    public static final BlockPos WEST_SOURCE = new BlockPos(1, 2, 1);
    public static final BlockPos SUBJECT = new BlockPos(2, 2, 1);
    public static final BlockPos EAST_SOURCE = new BlockPos(3, 2, 1);

    /** Whether flowing water (or an emptied water bucket) would destroy this state instead of
     *  coexisting with it. Mirrors the vanilla rule without the deprecated {@code blocksMotion()}:
     *  {@code canBeReplaced(Fluid)} is exactly "replaceable or not solid". */
    static boolean waterWashesAway(BlockState state) {
        Block block = state.getBlock();
        if (state.isAir() || block instanceof LiquidBlock || block instanceof LiquidBlockContainer) {
            return false;
        }
        return state.canBeReplaced(Fluids.WATER);
    }

    // ---------- Registry sweep: no BuildCraft block state may be washed away ----------

    public static void testNoBlockWashedAwayByWater(GameTestHelper helper) {
        // Liveness: the predicate must see the legacy "solid" flag, or every check below is vacuous.
        helper.assertTrue(waterWashesAway(Blocks.TORCH.defaultBlockState()),
            "sanity: water must wash away a vanilla torch (predicate or block-state cache is broken)");
        helper.assertFalse(waterWashesAway(Blocks.STONE.defaultBlockState()),
            "sanity: water must not wash away vanilla stone");

        Map<String, String> offenders = new TreeMap<>();
        int bcBlocks = 0;
        int containers = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            var key = BuiltInRegistries.BLOCK.getKey(block);
            if (!NAMESPACE.equals(key.getNamespace())) {
                continue;
            }
            bcBlocks++;
            if (block instanceof LiquidBlockContainer) {
                containers++;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (waterWashesAway(state)) {
                    offenders.putIfAbsent(key.getPath(), block.getClass().getSimpleName() + " " + state);
                    break;
                }
            }
        }
        if (bcBlocks == 0) {
            throw new IllegalStateException("No " + NAMESPACE + " blocks in the registry -- registration broke");
        }
        if (containers == 0) {
            throw new IllegalStateException("No BuildCraft LiquidBlockContainer found -- the pipe holder "
                + "should be one; the sweep is not seeing BuildCraft's blocks");
        }

        StringBuilder failures = new StringBuilder();
        for (Map.Entry<String, String> e : offenders.entrySet()) {
            if (!DELIBERATELY_FLOODABLE.containsKey(e.getKey())) {
                failures.append("\n  ").append(e.getKey()).append(" (").append(e.getValue()).append(")");
            }
        }
        for (String allowed : DELIBERATELY_FLOODABLE.keySet()) {
            if (!offenders.containsKey(allowed)) {
                failures.append("\n  stale DELIBERATELY_FLOODABLE entry (no longer floodable): ").append(allowed);
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("BuildCraft block(s) that flowing water would destroy -- make them "
                + "SimpleWaterloggedBlock (see BCWaterlogging) or list them in DELIBERATELY_FLOODABLE:" + failures);
        }
        helper.succeed();
    }

    // ---------- End-to-end: real water sources flood the subject instead of deleting it ----------

    public static void testMarkerWaterlogsBetweenSources(GameTestHelper helper) {
        buildBasin(helper);
        // Default facing is UP: the marker stands on the stone floor below the subject cell.
        helper.setBlock(SUBJECT, BCCoreBlocks.MARKER_VOLUME.get());
        assertWaterloggedBetweenSources(helper, BCCoreBlocks.MARKER_VOLUME.get(), () -> {});
    }

    /** Characterisation for the todo's facade concern: a solid facade on a pipe floods with the pipe and
     *  stays mounted — the facade lives on the waterloggable holder, so there is no separate facade block
     *  for water to destroy. */
    public static void testFacadedPipeWaterlogsBetweenSources(GameTestHelper helper) {
        buildBasin(helper);
        TilePipeHolder tile = placeWoodPipe(helper, SUBJECT);

        FacadeStateManager.ensureInitialized();
        FacadeBlockStateInfo stone = FacadeStateManager.getInfoForBlock(Blocks.STONE);
        helper.assertTrue(stone != null, "stone must be a valid facade state");
        PluggableFacade facade = new PluggableFacade(BCSiliconPlugs.facade, tile, Direction.UP,
            FacadeInstance.createSingle(stone, false));
        tile.replacePluggable(Direction.UP, facade);

        assertWaterloggedBetweenSources(helper, BCTransportBlocks.PIPE_HOLDER.get(), () -> {
            TilePipeHolder after = (TilePipeHolder) helper.getLevel().getBlockEntity(helper.absolutePos(SUBJECT));
            helper.assertTrue(after != null && after.getPluggable(Direction.UP) == facade,
                "the facade must still be mounted on the flooded pipe");
        });
    }

    /** SimpleWaterloggedBlock only accepts water, so lava can neither waterlog the marker nor enter its
     *  cell: it is held back (vanilla lantern/chain behaviour). Before, lava replaced the marker — and lava,
     *  unlike water, drops nothing, so the marker was simply lost. */
    public static void testMarkerHoldsBackLava(GameTestHelper helper) {
        buildBasin(helper);
        helper.setBlock(SUBJECT, BCCoreBlocks.MARKER_PATH.get());
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(SUBJECT);
        BlockEntity before = level.getBlockEntity(abs);
        helper.assertTrue(before != null, "path marker must have a block entity");
        placeSource(helper, WEST_SOURCE, Blocks.LAVA, Fluids.LAVA);
        placeSource(helper, EAST_SOURCE, Blocks.LAVA, Fluids.LAVA);

        helper.succeedWhen(() -> {
            // Gate on observed state: both lava sources have run their spread tick, so the lava has
            // already had its one chance to flow into the marker's cell.
            helper.assertTrue(hasSpread(helper, WEST_SOURCE, Fluids.LAVA), "west lava source has not spread yet");
            helper.assertTrue(hasSpread(helper, EAST_SOURCE, Fluids.LAVA), "east lava source has not spread yet");
            helper.assertBlockPresent(BCCoreBlocks.MARKER_PATH.get(), SUBJECT);
            helper.assertTrue(level.getBlockEntity(abs) == before,
                "the marker's block entity must survive the lava untouched");
            BlockState state = helper.getBlockState(SUBJECT);
            helper.assertTrue(state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && !state.getValue(BlockStateProperties.WATERLOGGED),
                "the marker must be waterloggable, and lava must not waterlog it");
            helper.assertTrue(level.getFluidState(abs).isEmpty(), "lava must not enter the marker's cell");
        });
    }

    /** Heavy oils "sink through" water by deleting the water block beneath them. A waterlogged block also
     *  reports a water fluid state, so that used to delete the whole block — a waterlogged pipe (with its
     *  cargo, plugs and wires) or marker simply vanished under a heavy-oil pool. Only a plain water block
     *  may be displaced; a waterlogged block refuses oil, so the oil must rest on top of it. */
    public static void testWaterloggedPipeSurvivesDenseOil(GameTestHelper helper) {
        buildBasin(helper);
        // Close the cell above the subject too, so the oil can only ever go down into the subject.
        BlockPos oilPos = SUBJECT.above();
        helper.setBlock(oilPos.west(), Blocks.STONE);
        helper.setBlock(oilPos.east(), Blocks.STONE);
        helper.setBlock(oilPos.north(), Blocks.STONE);
        helper.setBlock(oilPos.south(), Blocks.STONE);

        helper.setBlock(SUBJECT, BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, true));
        TilePipeHolder tile = (TilePipeHolder) helper.getLevel().getBlockEntity(helper.absolutePos(SUBJECT));
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));

        BCEnergyFluids.FluidEntry heavy = BCEnergyFluids.ALL.stream()
            .filter(e -> e.baseName().equals("oil_heavy") && e.heat() == 0).findFirst().orElseThrow();
        placeSource(helper, oilPos, heavy.block().get(), heavy.source().get());

        helper.succeedWhen(() -> {
            // Survival first, so a deleted pipe reports as such (once displaced, the oil keeps flowing and
            // re-ticking, so the gate below would otherwise be the last failure message).
            helper.assertBlockPresent(BCTransportBlocks.PIPE_HOLDER.get(), SUBJECT);
            helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(SUBJECT)) == tile,
                "the waterlogged pipe's block entity must survive the oil above it");
            helper.assertTrue(helper.getBlockState(SUBJECT).getValue(BlockStateProperties.WATERLOGGED),
                "the pipe must stay waterlogged");
            // Gate: the oil has run its tick (the one that used to delete the pipe) and settled on top.
            helper.assertTrue(hasSpread(helper, oilPos, heavy.source().get()), "heavy oil has not ticked yet");
            helper.assertBlockPresent(heavy.block().get(), oilPos);
        });
    }

    /** The other half of the dense-oil rule: a PLAIN water block is still displaced, so heavy oil keeps
     *  sinking through water. One-cell pit (the basin with its channel filled), water in it, oil above.
     *
     *  <p>This discriminates {@code BCEnergyFluids.displaceWaterBelow} from vanilla: vanilla does let a
     *  non-water fluid falling DOWN replace water ({@code WaterFluid.canBeReplacedWith}), but every BuildCraft
     *  oil is in the {@code minecraft:water} fluid tag (for NeoForge's swim/physics hooks), so vanilla alone
     *  never sinks it — without the displacement the oil sits on top and this test fails (verified by
     *  no-op'ing the method). The precondition below pins that; if it ever flips, vanilla sinks oil by
     *  itself and {@code displaceWaterBelow} has become redundant. */
    public static void testDenseOilStillSinksThroughPlainWater(GameTestHelper helper) {
        buildBasin(helper);
        helper.setBlock(WEST_SOURCE, Blocks.STONE);
        helper.setBlock(EAST_SOURCE, Blocks.STONE);
        BlockPos oilPos = SUBJECT.above();
        helper.setBlock(oilPos.west(), Blocks.STONE);
        helper.setBlock(oilPos.east(), Blocks.STONE);
        helper.setBlock(oilPos.north(), Blocks.STONE);
        helper.setBlock(oilPos.south(), Blocks.STONE);
        helper.setBlock(SUBJECT, Blocks.WATER);

        BCEnergyFluids.FluidEntry heavy = BCEnergyFluids.ALL.stream()
            .filter(e -> e.baseName().equals("oil_heavy") && e.heat() == 0).findFirst().orElseThrow();
        BlockPos subjectAbs = helper.absolutePos(SUBJECT);
        helper.assertFalse(helper.getLevel().getFluidState(subjectAbs)
                .canBeReplacedWith(helper.getLevel(), subjectAbs, heavy.flowing().get(), Direction.DOWN),
            "precondition: vanilla must NOT sink heavy oil into water by itself (the oils are water-tagged), "
                + "or this test cannot tell whether displaceWaterBelow works");
        placeSource(helper, oilPos, heavy.block().get(), heavy.source().get());

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(heavy.block().get(), SUBJECT);
            helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(SUBJECT)).getType().isSame(heavy.source().get()),
                "heavy oil must have sunk into the plain water's cell");
        });
    }

    // ---------- Draining: pumps and robots take the water, never the block ----------

    /** {@code BlockUtil.drainBlock} is the one drain path of the Pump and the robot pump board. A waterlogged
     *  block reports a water <em>source</em> fluid state, and draining it used to set the cell to air —
     *  deleting the marker or pipe (cargo, plugs, facades) with no drop. It must be un-waterlogged instead,
     *  exactly as a player's bucket does. Always-waterlogged plants (kelp) give up no water to a bucket, so
     *  they are not drainable at all; a plain water block still drains to air. */
    public static void testDrainBlockKeepsWaterloggedBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos markerRel = new BlockPos(1, 2, 4);
        BlockPos pipeRel = new BlockPos(2, 2, 4);
        BlockPos kelpRel = new BlockPos(3, 2, 4);
        BlockPos waterRel = new BlockPos(4, 2, 4);
        for (int x = 1; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), Blocks.STONE);
        }
        helper.setBlock(markerRel, BCCoreBlocks.MARKER_VOLUME.get().defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, true));
        helper.setBlock(pipeRel, BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, true));
        TilePipeHolder pipe = (TilePipeHolder) level.getBlockEntity(helper.absolutePos(pipeRel));
        pipe.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        helper.setBlock(kelpRel, Blocks.KELP);
        helper.setBlock(waterRel, Blocks.WATER);
        helper.assertTrue(level.getFluidState(helper.absolutePos(kelpRel)).isSource(),
            "sanity: kelp must report a water source (or the kelp case below is vacuous)");

        for (BlockPos rel : new BlockPos[] { markerRel, pipeRel }) {
            BlockPos abs = helper.absolutePos(rel);
            Block block = helper.getBlockState(rel).getBlock();
            BlockEntity before = level.getBlockEntity(abs);
            helper.assertTrue(before != null, block + " must have a block entity");

            FluidStack simulated = BlockUtil.drainBlock(level, abs, false);
            helper.assertTrue(simulated != null && simulated.getFluid() == Fluids.WATER && simulated.getAmount() == 1000,
                "a waterlogged " + block + " must offer one bucket of water, got " + simulated);
            helper.assertTrue(helper.getBlockState(rel).getValue(BlockStateProperties.WATERLOGGED),
                "a simulated drain must not touch the " + block);

            FluidStack drained = BlockUtil.drainBlock(level, abs, true);
            helper.assertTrue(drained != null && drained.getFluid() == Fluids.WATER && drained.getAmount() == 1000,
                "draining a waterlogged " + block + " must yield one bucket of water, got " + drained);
            helper.assertBlockPresent(block, rel);
            helper.assertTrue(level.getBlockEntity(abs) == before,
                "draining must keep the " + block + "'s block entity (contents and all)");
            helper.assertFalse(helper.getBlockState(rel).getValue(BlockStateProperties.WATERLOGGED),
                "a drained " + block + " must be dry");
            helper.assertTrue(level.getFluidState(abs).isEmpty(), "a drained " + block + " must hold no fluid");
        }
        helper.assertTrue(pipe.getPipe() != null, "the drained pipe must still be a pipe");

        helper.assertTrue(BlockUtil.drainBlock(level, helper.absolutePos(kelpRel), false) == null,
            "kelp gives up no water to a bucket, so it must not be drainable");
        helper.assertTrue(BlockUtil.drainBlock(level, helper.absolutePos(kelpRel), true) == null,
            "kelp must not be drainable");
        helper.assertBlockPresent(Blocks.KELP, kelpRel);

        FluidStack water = BlockUtil.drainBlock(level, helper.absolutePos(waterRel), true);
        helper.assertTrue(water != null && water.getFluid() == Fluids.WATER && water.getAmount() == 1000,
            "a plain water source must drain one bucket, got " + water);
        helper.assertTrue(helper.getBlockState(waterRel).isAir(), "a drained water source must leave air");

        // Kelp keeps its water: remove it so nothing flows out of this arena cell after the test.
        helper.setBlock(kelpRel, Blocks.AIR);
        helper.succeed();
    }

    /** The Pump end to end: a finite channel of [water][waterlogged marker][kelp]. The pump must drain the
     *  marker's water and the plain water, and leave the marker (dry) and the kelp in place. Before, it
     *  deleted the kelp and then the marker. Drives the pump's own tick synchronously, so no fluid flows
     *  mid-test; stops on observed state, never a fixed tick count. */
    public static void testPumpKeepsWaterloggedMarker(GameTestHelper helper) {
        buildBasin(helper);
        ServerLevel level = helper.getLevel();
        helper.setBlock(WEST_SOURCE, Blocks.WATER);
        helper.setBlock(SUBJECT, BCCoreBlocks.MARKER_PATH.get().defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, true));
        helper.setBlock(EAST_SOURCE, Blocks.KELP);
        BlockEntity marker = level.getBlockEntity(helper.absolutePos(SUBJECT));
        helper.assertTrue(marker != null, "path marker must have a block entity");

        BlockPos pumpRel = WEST_SOURCE.above();
        helper.setBlock(pumpRel, BCFactoryBlocks.PUMP.get());
        TilePump pump = (TilePump) level.getBlockEntity(helper.absolutePos(pumpRel));
        helper.assertTrue(pump != null, "the pump must have a block entity");

        BlockPos westAbs = helper.absolutePos(WEST_SOURCE);
        for (int i = 0; i < 200; i++) {
            BlockState markerState = helper.getBlockState(SUBJECT);
            if (!markerState.is(BCCoreBlocks.MARKER_PATH.get())) {
                break; // deleted: fail below with the precise message
            }
            if (!markerState.getValue(BlockStateProperties.WATERLOGGED) && level.getFluidState(westAbs).isEmpty()) {
                break;
            }
            pump.getBattery().addPowerChecking(10 * MjAPI.MJ, false);
            pump.serverTick();
        }

        helper.assertBlockPresent(BCCoreBlocks.MARKER_PATH.get(), SUBJECT);
        helper.assertTrue(level.getBlockEntity(helper.absolutePos(SUBJECT)) == marker,
            "the pumped marker's block entity must survive");
        helper.assertFalse(helper.getBlockState(SUBJECT).getValue(BlockStateProperties.WATERLOGGED),
            "the pump must have drained the marker's water");
        helper.assertTrue(level.getFluidState(westAbs).isEmpty(), "the pump must have drained the plain water");
        helper.assertBlockPresent(Blocks.KELP, EAST_SOURCE);
        helper.assertTrue(pump.getTank().getAmountMb(0) == 2000,
            "exactly two buckets (marker + plain water) must be pumped, got " + pump.getTank().getAmountMb(0));

        // Kelp keeps its water: remove it so the basin is dry when the test ends.
        helper.setBlock(EAST_SOURCE, Blocks.AIR);
        helper.succeed();
    }

    // ---------- Placement: placing into a water source keeps the water ----------

    public static void testPlacementIntoWaterSourceWaterlogs(GameTestHelper helper) {
        buildBasin(helper);
        ServerLevel level = helper.getLevel();
        Block[] subjects = {
            BCCoreBlocks.MARKER_VOLUME.get(),
            BCCoreBlocks.MARKER_PATH.get(),
            BCTransportBlocks.PIPE_HOLDER.get(),
        };
        BlockPos abs = helper.absolutePos(SUBJECT);
        for (boolean wet : new boolean[] { true, false }) {
            helper.setBlock(SUBJECT, wet ? Blocks.WATER : Blocks.AIR);
            for (Block block : subjects) {
                // Aim at the subject cell itself: water and air are replaceable, so the clicked cell IS the
                // placement cell — exactly what a player clicking into a water source produces.
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
                BlockPlaceContext ctx = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND,
                    new ItemStack(block.asItem()), hit);
                BlockState placed = block.getStateForPlacement(ctx);
                helper.assertTrue(placed != null && placed.hasProperty(BlockStateProperties.WATERLOGGED),
                    block + " must have a WATERLOGGED property");
                helper.assertTrue(placed.getValue(BlockStateProperties.WATERLOGGED) == wet,
                    block + " placed into " + (wet ? "a water source must start waterlogged" : "air must start dry"));
                // Actually place it: the water must still be in the cell afterwards (getFluidState agrees).
                level.setBlock(abs, placed, Block.UPDATE_ALL);
                helper.assertTrue(level.getFluidState(abs).is(Fluids.WATER) == wet,
                    block + " placed into " + (wet ? "a water source must keep the water" : "air must hold no fluid"));
                helper.setBlock(SUBJECT, wet ? Blocks.WATER : Blocks.AIR);
            }
        }
        helper.setBlock(SUBJECT, Blocks.AIR);
        helper.succeed();
    }

    // ---------- shared basin helpers ----------

    /** Stone floor under the whole basin plus a one-high wall ring around the three-cell channel, so no
     *  fluid placed in the channel can ever leave the test's own arena cell. */
    public static void buildBasin(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 2, 1), Blocks.STONE);
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 2, 0), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 2), Blocks.STONE);
        }
    }

    /** Places a fluid source and guarantees it has a pending spread tick regardless of setBlock's onPlace
     *  scheduling, so {@link #hasSpread} becomes a reliable "this source has had its chance" gate. */
    public static void placeSource(GameTestHelper helper, BlockPos relPos, Block fluidBlock, Fluid fluid) {
        helper.setBlock(relPos, fluidBlock);
        helper.getLevel().scheduleTick(helper.absolutePos(relPos), fluid, 1);
    }

    /** True once the source at {@code relPos} has run its pending spread tick — the observed-state gate
     *  for "the fluid has already tried to flow into its neighbours". Spreading is synchronous within that
     *  tick, so anything it was going to destroy is gone by the time this turns true. */
    public static boolean hasSpread(GameTestHelper helper, BlockPos relPos, Fluid fluid) {
        return !helper.getLevel().getFluidTicks().hasScheduledTick(helper.absolutePos(relPos), fluid);
    }

    private static void assertWaterloggedBetweenSources(GameTestHelper helper, Block expected, Runnable extra) {
        BlockPos abs = helper.absolutePos(SUBJECT);
        BlockEntity before = helper.getLevel().getBlockEntity(abs);
        helper.assertTrue(before != null, expected + " must have a block entity");
        placeSource(helper, WEST_SOURCE, Blocks.WATER, Fluids.WATER);
        placeSource(helper, EAST_SOURCE, Blocks.WATER, Fluids.WATER);

        helper.succeedWhen(() -> {
            // Pre-fix the subject is replaced by a water source here and this never passes.
            helper.assertBlockPresent(expected, SUBJECT);
            BlockState state = helper.getBlockState(SUBJECT);
            helper.assertTrue(state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && state.getValue(BlockStateProperties.WATERLOGGED),
                expected + " must end up waterlogged between two water sources");
            helper.assertTrue(helper.getLevel().getFluidState(abs).is(Fluids.WATER),
                "a waterlogged " + expected + " must report a water fluid state");
            helper.assertTrue(helper.getLevel().getBlockEntity(abs) == before,
                expected + "'s block entity must survive being waterlogged");
            extra.run();
        });
    }

    private static TilePipeHolder placeWoodPipe(GameTestHelper helper, BlockPos relPos) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder tile = (TilePipeHolder) helper.getLevel().getBlockEntity(helper.absolutePos(relPos));
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        return tile;
    }
}
