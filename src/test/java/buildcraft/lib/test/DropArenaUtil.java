/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The one shape every "break a block, check what it dropped" game test goes through.
 *
 * <p>Asserting on a fixed tick after the break is a race the machine decides. The framework forces only the
 * structure's chunk, so a drop popped into a cell chunk that is not entity-ticking yet is invisible to a scan (and
 * stands still), and a fixed wait long enough to cover that is too long for the other direction: a fragile stack
 * drifts out of a one-block scan after about ten ticks. {@link #awaitDrops} instead gates everything on observed
 * state, in this order:
 * <ol>
 * <li>force-load the cell and wait for it to be entity-ticking ({@link EntityArenaUtil#whenCellTicking});</li>
 * <li>run the test's {@code act} (place, fill, break) in one tick, so nothing ticks between filling a machine and
 *     breaking it;</li>
 * <li>poll until every drop the test expects is in its scan, which is normally the very next tick;</li>
 * <li>settle for {@link #SETTLE_TICKS} more, so a late duplicate or a stray block has time to turn up;</li>
 * <li>only then make the assertions ({@link Drops}), and discard the cell's items on every way out.</li>
 * </ol>
 * The poll's whole budget is {@link #POLL_TICKS}; a manifest using it needs {@code max_ticks} above that, which
 * {@code GameTestPollBudgetTester} checks (it reads the constant from this file).
 *
 * <p>Only drops inside the test's own cell count, so a drop must not be able to leave it before the assertions. A
 * drop popped into a block that is still there (a pluggable or wire taken off a pipe) ejects itself at up to 0.3
 * blocks a tick and travels about 1.5 blocks by then: put that block at least two blocks from every cell edge.
 *
 * <p>NB: the {@link GameTestHelper} parameter is named {@code gth} for the reason given on
 * {@link EntityArenaUtil}: the build script rewrites {@code assertTrue}/{@code assertFalse} on a {@code helper}
 * receiver. Failures go through {@code gth.fail}, which is uniform on every node.
 */
public final class DropArenaUtil {

    private DropArenaUtil() {}

    /** Ticks, once the act has run, for every expected drop to be in its scan. The act spawns them all at once, so
     *  the first poll normally sees them; the margin is for a slow tick, not a settling time. */
    private static final int WORK_TICKS = 10;
    /** Ticks to wait after the last expected drop shows up, before the assertions. Short on purpose: the drops
     *  keep drifting while the test waits, and a one-block scan loses them after about ten ticks (measured on 26.3
     *  over 400 combustion-engine shards, each popped at up to 0.1 blocks/tick with a spawn offset up to 0.25:
     *  none outside a radius of 1 nine ticks after the pop, 2 at ten, 9 at eleven). Five, after a first poll that
     *  sees everything, asserts about six ticks after the pop. */
    private static final int SETTLE_TICKS = 5;
    /** The whole poll: the budget {@code GameTestPollBudgetTester} compares a manifest's {@code max_ticks} with.
     *  {@link EntityArenaUtil#CELL_READY_TICKS} (the cell's wait to become entity-ticking, the machine's clock and
     *  not the code under test's, so generous: it costs nothing unless the cell never ticks) plus the constants
     *  above, a sum which that guard resolves. */
    public static final int POLL_TICKS = EntityArenaUtil.CELL_READY_TICKS + WORK_TICKS + SETTLE_TICKS;

    /**
     * Runs {@code act} once the test's cell is entity-ticking, waits for every drop {@code expected} names to appear,
     * settles, asserts, and passes the test.
     *
     * @param act       places and fills the block, then breaks or removes it ({@link #breakBlock},
     *                  {@link #removeBlock}); runs in a single tick. A failed assertion inside it fails the test.
     * @param expected  what must (and must not) be lying about afterwards.
     */
    public static void awaitDrops(GameTestHelper gth, Runnable act, Drops expected) {
        EntityArenaUtil.forceLoadCell(gth);
        // One poll, two phases: the first call after the cell ticks acts (a drop popped into a chunk that does not
        // tick yet would be invisible), every later call asks whether the drops are all there.
        boolean[] acted = { false };
        EntityArenaUtil.tickUntilThen(gth, POLL_TICKS,
                EntityArenaUtil.whenCellTicking(gth, EntityArenaUtil.CELL_READY_TICKS, WORK_TICKS, () -> {
                    if (!acted[0]) {
                        acted[0] = true;
                        act.run();
                        return false;
                    }
                    return expected.missing(gth).isEmpty();
                }, () -> acted[0] ? "missing " + String.join(", ", expected.missing(gth)) : null),
                () -> { },
                SETTLE_TICKS,
                () -> {
                    expected.assertAll(gth);
                    gth.succeed();
                },
                // Unreachable in practice: the gate above fails first, naming what is missing.
                "the drop poll ran out",
                () -> discardDrops(gth));
    }

    /** Breaks the block the way a survival player holding {@code tool} does ({@link GameTestUtil}), and checks it
     *  is gone. */
    public static void breakBlock(GameTestHelper gth, BlockPos pos, ItemStack tool) {
        GameTestUtil.breakAsSurvivalPlayer(gth, pos, tool);
        gth.assertBlockPresent(Blocks.AIR, pos);
    }

    /** Removes the block with no player and no loot (an explosion, a piston, a mod's {@code setBlock}), and checks it
     *  is gone. */
    public static void removeBlock(GameTestHelper gth, BlockPos pos) {
        gth.getLevel().removeBlock(gth.absolutePos(pos), false);
        gth.assertBlockPresent(Blocks.AIR, pos);
    }

    /** Discards every item in the test's own cell, never wider: the neighbouring arenas run the same tests at the same
     *  time, and a wider sweep discards the drops they are about to assert on. The pass-time cleanup reaches only
     *  about a block, so what a test drops would otherwise outlive it. */
    private static void discardDrops(GameTestHelper gth) {
        gth.getLevel().getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(gth))
                .forEach(ItemEntity::discard);
    }

    /**
     * What a drop test expects to find, as a chain: {@code Drops.near(pos, 2.0).present(A, B).absent(C)}. Every entry
     * counts items in the box reaching {@code radius} blocks along each axis from the centre of {@code pos}
     * ({@link EntityArenaUtil#droppedItems}; a box, not a sphere), clipped to the test's own cell, by total count,
     * not entity count: identical stacks may merge. {@link #present} and {@link #exactly} are also
     * what the poll waits for; {@link #absent} and the exact totals are only checked after the settle.
     */
    public static final class Drops {

        private enum Kind { PRESENT, EXACTLY, ABSENT }

        /** {@code count} is the total {@link Kind#PRESENT} needs (one), {@link Kind#EXACTLY} requires, or zero. */
        private record Entry(BlockPos pos, double radius, Item item, Kind kind, int count) {}

        private final List<Entry> entries = new ArrayList<>();
        private final List<Runnable> checks = new ArrayList<>();
        private BlockPos pos;
        private double radius;

        private Drops(BlockPos pos, double radius) {
            this.pos = pos;
            this.radius = radius;
        }

        /** Starts a chain whose entries scan {@code radius} blocks around {@code pos} (relative to the arena). */
        public static Drops near(BlockPos pos, double radius) {
            return new Drops(pos, radius);
        }

        /** Later entries scan around {@code pos} instead (an engine's gear drops there, not at the Stirling). */
        public Drops alsoNear(BlockPos pos, double radius) {
            this.pos = pos;
            this.radius = radius;
            return this;
        }

        /** Each item must be there at least once. */
        public Drops present(Item... items) {
            for (Item item : items) {
                entries.add(new Entry(pos, radius, item, Kind.PRESENT, 1));
            }
            return this;
        }

        /** The item's total must be exactly {@code count}: not zero (a hook the break never reached), not twice (a
         *  drop on top of a removal hook). */
        public Drops exactly(Item item, int count) {
            entries.add(new Entry(pos, radius, item, Kind.EXACTLY, count));
            return this;
        }

        /** None of the items may be there. */
        public Drops absent(Item... items) {
            for (Item item : items) {
                entries.add(new Entry(pos, radius, item, Kind.ABSENT, 0));
            }
            return this;
        }

        /** An assertion of the test's own (a drop that carries data), run with the others after the settle. */
        public Drops andCheck(Runnable check) {
            checks.add(check);
            return this;
        }

        /** Counts only inside the test's own cell: a radius around a pipe one block from the edge reaches into the
         *  neighbouring arena, whose own cleanup then discards what this test already counted. */
        private static int total(GameTestHelper gth, Entry entry) {
            AABB cell = EntityArenaUtil.cellBounds(gth);
            return EntityArenaUtil.droppedItems(gth, entry.pos(), entry.radius()).stream()
                    .filter(drop -> cell.contains(drop.position()))
                    .filter(drop -> drop.getItem().is(entry.item()))
                    .mapToInt(drop -> drop.getItem().getCount())
                    .sum();
        }

        /** What of the expected drops is not there yet: empty once everything the poll waits for has arrived (more
         *  than expected, and anything {@link #absent}, is {@link #assertAll}'s to reject). Also the text of the
         *  poll's timeout, so it names what never came. */
        List<String> missing(GameTestHelper gth) {
            List<String> missing = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry.kind() == Kind.ABSENT) {
                    continue;
                }
                int total = total(gth, entry);
                if (total < entry.count()) {
                    missing.add(total + " of " + entry.count() + " " + entry.item() + " near " + entry.pos());
                }
            }
            return missing;
        }

        /** The assertions, once the drops have settled. */
        void assertAll(GameTestHelper gth) {
            for (Entry entry : entries) {
                int total = total(gth, entry);
                String where = " within " + entry.radius() + " of " + entry.pos();
                switch (entry.kind()) {
                    case PRESENT -> {
                        if (total < 1) {
                            gth.fail("expected " + entry.item() + where + ", found none");
                        }
                    }
                    case EXACTLY -> {
                        if (total != entry.count()) {
                            gth.fail("expected exactly " + entry.count() + " of " + entry.item() + where
                                    + " but found " + total);
                        }
                    }
                    case ABSENT -> {
                        if (total != 0) {
                            gth.fail("expected no " + entry.item() + where + " but found " + total);
                        }
                    }
                }
            }
            checks.forEach(Runnable::run);
        }
    }
}
