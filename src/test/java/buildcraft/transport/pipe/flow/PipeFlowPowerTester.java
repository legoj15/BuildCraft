/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.core.BCCoreBlocks;
import buildcraft.core.tile.TileEngineCreative;
import buildcraft.core.tile.TilePowerConsumerTester;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/** End-to-end test for the power-pipe flow chain.
 *
 *  <p>The "gaps in the energy stream" regression that prompted this test was rooted
 *  in the client-side renderer's geometry math; the geometry helper is unit-tested
 *  directly in {@code PipeFlowRendererPowerGeometryTester}, which is the actual
 *  regression catcher. This game test exists as the server-side bookend — pinning
 *  that the engine→wood-diamond→diamond→tester transfer chain itself still works,
 *  so a future change to {@link PipeFlowPower#onTick()} or one of the hand-offs
 *  (engine→pipe / pipe→pipe / pipe→tile) can't silently break power transport
 *  without anyone noticing.
 *
 *  <p>Sink is the dev-only {@link TilePowerConsumerTester}, gated on {@code BCLib.DEV}.
 *  The {@code gameTestServer} run config in {@code build.gradle} sets
 *  {@code -Dbuildcraft.dev=true} so the tester block is registered for tests; it
 *  beats a bounded sink (e.g. Dynamo MJ) because the dynamo's MAX_MJ battery fills
 *  in ~4 ticks at 256 MJ/t and the chain stops, making the assertion race-sensitive
 *  to pipe tick ordering. */
public class PipeFlowPowerTester {

    /** Creative engine at max output → wood diamond power pipe → diamond power pipe
     *  → power tester. Verifies that after enough ticks for the redstone poll +
     *  engine warm-up + 2-tick query-chain propagation, the tester has received
     *  at least one tick of power. */
    public static void testEngineThroughDiamondPipesPowersTester(GameTestHelper helper) {
        BlockPos redstonePos    = new BlockPos(2, 1, 2);
        BlockPos enginePos      = new BlockPos(2, 2, 2);
        BlockPos woodPipePos    = new BlockPos(2, 3, 2);
        BlockPos diamondPipePos = new BlockPos(2, 4, 2);
        BlockPos testerPos      = new BlockPos(2, 5, 2);

        if (BCCoreBlocks.POWER_TESTER == null) {
            throw new IllegalStateException(
                    "POWER_TESTER block not registered — test JVM was launched without -Dbuildcraft.dev=true. "
                            + "Check build.gradle gameTestServer run config.");
        }
        helper.setBlock(testerPos, BCCoreBlocks.POWER_TESTER.get());
        TilePipeHolder diamondPipe = placePowerPipe(helper, diamondPipePos, BCTransportItems.PIPE_DIAMOND_POWER.get());
        TilePipeHolder woodPipe    = placePowerPipe(helper, woodPipePos,    BCTransportItems.PIPE_DIAMOND_WOOD_POWER.get());

        BlockState engineState = BCCoreBlocks.ENGINE_CREATIVE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP);
        helper.setBlock(enginePos, engineState);
        helper.setBlock(redstonePos, Blocks.REDSTONE_BLOCK);

        TileEngineCreative engine = helper.getBlockEntity(enginePos, TileEngineCreative.class);
        engine.currentOutputIndex = TileEngineCreative.OUTPUTS.length - 1; // 256 MJ/t

        // Connection bookkeeping isn't established by setBlock+onPlacedBy alone — the
        // normal place-pipe-with-item flow relies on the neighbour-changed cascade
        // that follows a player-placement to drive Pipe.markForUpdate(). Trigger it
        // explicitly so the pipes pick up their neighbours on the first tick.
        woodPipe.getPipe().markForUpdate();
        diamondPipe.getPipe().markForUpdate();

        // Poll every tick until the tester has received SOME power. This is intentionally
        // tolerant of game-test arena startup jitter — pipe BE ticking can take a variable
        // number of ticks to start in the test sandbox, so a single-shot delayed assertion
        // is flaky. succeedWhen retries until either the assertion passes or max_ticks
        // expires (set generously high in the JSON manifest). helper.assertTrue throws
        // the GameTestAssertException variant that succeedWhen treats as retry-now.
        helper.succeedWhen(() -> {
            TilePowerConsumerTester tester = helper.getBlockEntity(testerPos, TilePowerConsumerTester.class);
            helper.assertTrue(readTesterTotal(tester) > 0,
                    "Power tester has not yet received MJ through the engine→pipe chain");
        });
    }

    private static TilePipeHolder placePowerPipe(GameTestHelper helper, BlockPos pos, Item pipeItem) {
        helper.setBlock(pos, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder tile = helper.getBlockEntity(pos, TilePipeHolder.class);
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    /** Sniffs the tester's debug output for a non-zero "Total received" line — avoids
     *  needing a package-private accessor on the tester just for tests. Returns 1
     *  if any power has arrived, 0 otherwise. */
    private static long readTesterTotal(TilePowerConsumerTester tester) {
        java.util.List<String> left = new java.util.ArrayList<>();
        java.util.List<String> right = new java.util.ArrayList<>();
        tester.getDebugInfo(left, right, Direction.UP);
        for (String line : left) {
            if (line.startsWith("Total received") && !line.contains(" 0 ")) return 1;
        }
        return 0;
    }

    private static String describeFlow(String label, PipeFlowPower flow, TilePipeHolder holder) {
        StringBuilder sb = new StringBuilder(label).append("[");
        for (Direction d : Direction.values()) {
            PipeFlowPower.Section s = flow.getSection(d);
            sb.append(d).append(":dp=").append(s.displayPower)
              .append(",inp=").append(s.internalPower)
              .append(",pq=").append(s.powerQuery)
              .append(",ct=").append(holder.getPipe().getConnectedType(d))
              .append(" ");
        }
        sb.append("]");
        return sb.toString();
    }
}
