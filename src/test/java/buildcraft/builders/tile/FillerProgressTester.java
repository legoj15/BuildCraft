/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import buildcraft.api.mj.MjAPI;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersStatements;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.FakeLevels;
import buildcraft.lib.test.ValueIoTestUtil;

/**
 * Whether a client can tell that a Filler is really building, which is what hides its preview. A running Filler fed
 * less than the ramped draw limit of an empty battery (256 MJ x (stored + 25.6 MJ) / 8000 MJ, about 0.82 MJ per tick
 * at zero) spends everything it receives in the tick it receives it: the battery it syncs reads empty every time, so
 * "has power" is no sign of work, and a preview keyed on it drew over cells that were being built.
 * <p>
 * This drives a real server-side {@link TileFiller} by hand (never added to the level, so its own ticker does not
 * run a second time) on a 0.5 MJ per tick feed, and pushes its data to a client copy every five ticks, the way
 * {@code TileFiller.tick()} does.
 */
public class FillerProgressTester {

    private static final BlockPos BOX_MIN = new BlockPos(2, 1, 2);
    private static final BlockPos BOX_MAX = new BlockPos(4, 3, 4);
    private static final BlockPos FILLER = new BlockPos(5, 1, 5);

    private static final long FEED = MjAPI.MJ / 2;
    /** Feeding ticks, then ticks with no feed at all. */
    private static final int FEED_TICKS = 40;
    private static final int STARVE_TICKS = 60;
    /** Ticks before the first sync that must read as working: the box is scanned and its tasks queued within three. */
    private static final int WARMUP_TICKS = 12;

    public static void aSlowlyFedFillerSyncsAsWorkingAndAStarvedOneDoesNot(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        ServerLevel level = helper.getLevel();

        TileFiller server = new TileFiller(helper.absolutePos(FILLER), BCBuildersBlocks.FILLER.get().defaultBlockState());
        server.setLevel(level);
        server.box.reset();
        server.box.setMin(helper.absolutePos(BOX_MIN));
        server.box.setMax(helper.absolutePos(BOX_MAX));
        server.patternStatement.set(BCBuildersStatements.PATTERN_FILL);
        server.onStatementChange();
        server.invResources.insert(new ItemStack(Items.STONE, 64), false, false);

        TileFiller client = new TileFiller(BlockPos.ZERO, BCBuildersBlocks.FILLER.get().defaultBlockState());
        client.setLevel(FakeLevels.fake(true, Level.OVERWORLD));

        int[] ticks = { 0 };
        int[] workingSyncs = { 0 };
        EntityArenaUtil.tickUntil(helper, FEED_TICKS + STARVE_TICKS, () -> {
            boolean feeding = ++ticks[0] <= FEED_TICKS;
            if (feeding) {
                server.getBattery().addPower(FEED, false);
            }
            server.tick();
            if (level.getGameTime() % 5 != 0) {
                return false;
            }
            client.readData(ValueIoTestUtil.inputOf(ValueIoTestUtil.write(server::writeData)));
            boolean working = client.hasWorkInFlight();
            if (feeding && ticks[0] > WARMUP_TICKS) {
                helper.assertTrue(working, "tick " + ticks[0] + ": a running Filler fed " + FEED + " uJ a tick and drawing"
                    + " all of it syncs as not working (battery it syncs: " + client.getBattery().getStored() + " uJ, queued: "
                    + client.builder.clientPlaceTasks.size() + ")");
                workingSyncs[0]++;
            }
            if (!feeding && !working) {
                helper.assertTrue(!client.builder.clientPlaceTasks.isEmpty(),
                    "the starved Filler should still hold its queue, or the signal under test is not what ended the work");
                helper.assertTrue(workingSyncs[0] >= 3, "only " + workingSyncs[0] + " working syncs were seen while feeding");
                return true;
            }
            return false;
        }, () -> helper.succeed(), "The starved Filler never synced as not working", () -> cleanUp(helper));
    }

    private static void cleanUp(GameTestHelper helper) {
        for (BlockPos rel : BlockPos.betweenClosed(BOX_MIN, BOX_MAX)) {
            helper.setBlock(rel, Blocks.AIR);
        }
    }
}
