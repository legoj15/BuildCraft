/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
//?}

import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.lib.misc.data.Box;

/**
 * Shared harness for game tests that need a <em>live, ticking entity</em> in the arena rather than just blocks.
 *
 * <p>Three things bite entity game tests that never bother block ones, and all three live here:
 * <ul>
 * <li><b>Un-ticked entities.</b> The framework force-loads the chunk(s) of the test STRUCTURE only. An entity
 *     spawned at a relative position can land in a neighbouring chunk that is <em>not</em> force-loaded, and an
 *     entity in an unloaded chunk never ticks — the test then observes a frozen entity and flakes roughly one run
 *     in ten. {@link #forceLoadEntityArena} is the 5x5 chunk force-load that fixes it (the framework unforces
 *     everything it recorded at batch end, so this self-cleans). <b>Force-loading is necessary but NOT
 *     sufficient — see {@link #forceLoadEntityArena} for the timing rule that goes with it.</b></li>
 * <li><b>Waiting for something then doing more work.</b> {@code GameTestHelper.succeedWhen} polls, but it also
 *     ends the test, and it may be used only once. {@link #tickUntil} polls for a condition and then hands
 *     control back so the test can carry on; {@link #tickUntilThen} chains a second phase behind it.</li>
 * <li><b>Cross-node entity APIs.</b> Entity save/load and damage both cliff at 1.21.10
 *     ({@code CompoundTag} -> {@code ValueInput}/{@code ValueOutput}, {@code hurt} -> {@code hurtServer}) and
 *     fluid transfer cliffs there too. The forks are isolated in {@link #saveEntity}, {@link #loadEntity},
 *     {@link #hurt}, {@link #fillTank} and {@link #tankAmount} so test bodies stay version-neutral.</li>
 * </ul>
 *
 * <p>NB: the {@link GameTestHelper} parameter is deliberately named {@code gth}, never {@code helper} — the
 * gated build-script regex that routes assertTrue/assertFalse calls on a {@code helper} receiver through
 * {@link GameTestUtil} on the pre-1.21.11 nodes (1.21.10 and 1.21.1) is textual, and a {@code helper}-named
 * receiver here would be rewritten. Nothing in this class asserts; it reports failures through
 * {@code fail(String)}, which is uniform on every node.
 */
public final class EntityArenaUtil {

    private EntityArenaUtil() {}

    /** Force-loads the 5x5 chunk block centred on the arena origin, so entities anywhere in a small arena
     *  actually tick. See the class javadoc for why this is not optional.
     *
     * <p><b>5x5, not 3x3:</b> a chunk becomes {@code ENTITY_TICKING} — the status that actually makes
     * entities tick — only once all 25 chunks in its radius-2 neighbourhood are {@code FULL}
     * ({@code ChunkMap.prepareEntityTickingChunk} waits on {@code getChunkRangeFuture(chunk, 2, FULL)}).
     * The old 3x3 force left the entity chunk's ring-2 at whatever level the arena's own loading happened to
     * give it: level 33 (allowed, but queued behind the arena's own generation, so entities froze for ~100
     * ticks while it caught up) or level 34 ({@code SPAWN} — generation disallowed, so the promotion
     * resolved to {@code UNLOADED} and the entity never ticked at all: the ~1-in-10 flake of
     * {@code robot_picker_picks_up_dropped_item}). Forcing the whole 5x5 pins every dependency at level 31
     * with a FULL allowance, so the promotion completes in a tick or two.
     *
     * <p><b>Necessary but not sufficient — never assert on a fixed tick after calling this.</b>
     * {@code ServerLevel.setChunkForced} queues the chunk's generation rather than completing it
     * synchronously; the promotion to {@code BLOCK_TICKING}/{@code ENTITY_TICKING} — which is what actually
     * makes block entities and entities tick — is applied a tick or two later, by the chunk source's own
     * update pass. With the old 3x3 force the first ticking tick was measured on the 26.1.2 node across 10
     * runs at <b>1, 1, 3, 1, 2, 1, 1, 2, 1, 1</b>; with the 5x5 it is 1 in every run (measured 20/20 on
     * 26.1.2), because no dependency is ever left at a disallowed level. But the arena still drops at a
     * random world position each run, so there is no upper bound to rely on.
     *
     * <p>So a {@code runAfterDelay(N)} that expects something to have ticked by tick N is a coin flip whose
     * losing side is a confusing assertion failure (or an NPE on state that was never built). Gate on the
     * state itself with {@link #tickUntil}/{@link #tickUntilThen} instead. This bit
     * {@code robot_station_render_state_transitions} (read {@code NONE}: the pluggable's {@code onTick} had
     * not run, so no {@code DockingStation} was registered) and
     * {@code robot_item_rejected_when_station_taken} (a robot still carrying {@code NULL_ROBOT_ID} at tick 5,
     * because it had not ticked yet). */
    public static void forceLoadEntityArena(GameTestHelper gth) {
        forceLoadEntityArena(gth, BlockPos.ZERO);
    }

    /** Force-loads the 5x5 chunk block centred on {@code relPos}'s chunk. 5x5 rather than 1x1 or 3x3 for two
     *  reasons: the framework can drop a test's origin at any chunk alignment, so the arena straddles a chunk
     *  border about as often as not; and an entity's chunk only starts ticking once its whole 5x5
     *  neighbourhood is {@code FULL} (see {@link #forceLoadEntityArena(GameTestHelper)}), so the force must
     *  cover that neighbourhood. Pass the entity's own relative position — not the arena origin — when the
     *  entity can sit near the arena's far edge. Forces only the chunk {@code relPos} is in, so a test whose work
     *  spans the cell (a tree, a drop or a station beside the robot) uses {@link #forceLoadCell} instead and starts
     *  its budget with {@link #whenCellTicking}. */
    public static void forceLoadEntityArena(GameTestHelper gth, BlockPos relPos) {
        BlockPos abs = gth.absolutePos(relPos);
        int chunkX = abs.getX() >> 4;
        int chunkZ = abs.getZ() >> 4;
        // Ring-2 first, centre last: every dependency of the centre chunk's ENTITY_TICKING promotion (the
        // full 5x5 at FULL) must be queued before the centre's own promotion runs.
        for (int r = 2; r >= 0; r--) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                        gth.getLevel().setChunkForced(chunkX + dx, chunkZ + dz, true);
                    }
                }
            }
        }
    }

    /** Polls {@code condition} once per tick for up to {@code maxTicks} ticks and then runs {@code then};
     *  fails the test with {@code failureMessage} if the condition never came true.
     *
     * <p>Every poll is scheduled UP FRONT rather than each one re-scheduling the next: {@code GameTestInfo}
     * runs its scheduled runnables while iterating its own (fastutil) map, so scheduling from inside a
     * scheduled runnable risks a ConcurrentModificationException. */
    public static void tickUntil(GameTestHelper gth, int maxTicks, BooleanSupplier condition, Runnable then,
                                 String failureMessage) {
        tickUntil(gth, maxTicks, condition, then, failureMessage, NO_CLEANUP);
    }

    /** {@link #tickUntil(GameTestHelper, int, BooleanSupplier, Runnable, String)} with a {@code cleanup} that runs
     *  exactly once on EVERY way out of the poll: after {@code then} (whether it passes or throws), when the
     *  condition itself throws (an assertion inside a phased condition), and on the poll's timeout. Discard the
     *  test's entities here, never at the end of {@code then}: a failed assertion skips the rest of {@code then},
     *  and a robot left behind keeps its station reservations and keeps flying into later tests. The cleanup
     *  reads its holders when it runs, so {@code () -> discard(robot[0])} is fine for a robot spawned mid-poll
     *  (and still {@code null} if the test failed before spawning it). */
    public static void tickUntil(GameTestHelper gth, int maxTicks, BooleanSupplier condition, Runnable then,
                                 String failureMessage, Runnable cleanup) {
        schedulePoll(gth::runAfterDelay, gth::fail, maxTicks, condition, then, failureMessage, cleanup);
    }

    /** The {@link GameTestHelper#runAfterDelay} seam, so the poll logic is unit-testable without a live test. */
    @FunctionalInterface
    interface Scheduler {
        void runAfterDelay(int ticks, Runnable runnable);
    }

    private static final Runnable NO_CLEANUP = () -> {};

    /** The GameTestHelper-free core of {@link #tickUntil}. {@code fail} must throw, as {@code GameTestHelper.fail}
     *  does on every node. */
    static void schedulePoll(Scheduler scheduler, Consumer<String> fail, int maxTicks, BooleanSupplier condition,
                             Runnable then, String failureMessage, Runnable cleanup) {
        boolean[] fired = { false };
        for (int t = 1; t <= maxTicks; t++) {
            final int tick = t;
            scheduler.runAfterDelay(tick, () -> {
                if (fired[0]) {
                    return;
                }
                boolean keepPolling = false;
                Throwable failure = null;
                try {
                    if (condition.getAsBoolean()) {
                        fired[0] = true;
                        then.run();
                    } else if (tick == maxTicks) {
                        fired[0] = true;
                        fail.accept(failureMessage + " (waited " + maxTicks + " ticks)");
                    } else {
                        keepPolling = true;
                    }
                } catch (Throwable t2) {
                    failure = t2;
                    throw t2;
                } finally {
                    if (!keepPolling) {
                        fired[0] = true;
                        runCleanup(cleanup, failure);
                    }
                }
            });
        }
    }

    /** Runs {@code cleanup}; a cleanup that throws while the test is already failing is attached to that failure
     *  as suppressed instead of replacing it, so the report still names what actually went wrong. */
    private static void runCleanup(Runnable cleanup, Throwable failure) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error e) {
            if (failure == null) {
                throw e;
            }
            failure.addSuppressed(e);
        }
    }

    /** Extent of a test's own arena cell in blocks: relative x 0..5, y 0..6, z 0..6. The framework spaces the
     *  minecraft:empty arenas 6 apart in X and 7 apart in Z, so anything a test writes outside these bounds lands
     *  in a neighbouring test's cell. */
    public static final int CELL_SIZE_X = 6;
    public static final int CELL_SIZE_Y = 7;
    public static final int CELL_SIZE_Z = 7;

    /** First relative y above every test's arena (the modern cell is {@link #CELL_SIZE_Y} tall, the 1.21.1 empty
     *  arena 16). A block written at or above it cannot land in any other test's cell. A test that needs a flat
     *  floor wider than its own 6x7 cell (the oil pool tests lay a 24x24 one) builds it at this height, never at
     *  y=1: the arenas of the same batch run at the same time, so a wide floor at the arena's own level silently
     *  overwrites a neighbour's rig between its setup and its assertions (the 2026-10 flake of
     *  {@code pipe_force_insert_entry_leg}, whose chest the tall-tree oil test turned to stone). */
    public static final int ABOVE_ARENAS_Y = 32;

    /** The test's own 6x7 arena cell (relative x 0..5, y 0..6, z 0..6) as a robot work / load-unload zone. A
     *  robot running a board's {@code update()} searches the whole dimension unless its zone says otherwise, and
     *  other arenas' stations, items and blocks are fair game to it: pin it here. */
    public static Box cellZone(GameTestHelper gth) {
        return new Box(gth.absolutePos(BlockPos.ZERO),
                gth.absolutePos(new BlockPos(CELL_SIZE_X - 1, CELL_SIZE_Y - 1, CELL_SIZE_Z - 1)));
    }

    /** The same cell as an entity-sweep box (the block cells' outer faces): sweeping any wider reaches into the
     *  neighbouring arenas and discards THEIR drops. */
    public static AABB cellBounds(GameTestHelper gth) {
        return new AABB(Vec3.atLowerCornerOf(gth.absolutePos(BlockPos.ZERO)),
                Vec3.atLowerCornerOf(gth.absolutePos(new BlockPos(CELL_SIZE_X, CELL_SIZE_Y, CELL_SIZE_Z))));
    }

    /** Whether entities at {@code relPos} tick. Implies the entity section is accessible, i.e. an entity added
     *  there is visible to {@code getEntitiesOfClass} at once. The framework waits only for the 1x1 structure's
     *  own chunk, so a position two blocks in can sit in a neighbouring chunk that is not there yet (measured on
     *  26.2 and 1.21.11: 20 of 50 samples crossed a chunk border, 6 of 50 were not yet entity-ticking at the
     *  test's first tick). A test that adds an entity and then scans for it in the same tick must gate on this. */
    public static boolean isEntityTicking(GameTestHelper gth, BlockPos relPos) {
        return gth.getLevel().isPositionEntityTicking(gth.absolutePos(relPos));
    }

    /** Whether every chunk the test's 6x7 cell touches is entity-ticking, read at the cell's four corners.
     *  {@link #isEntityTicking} on the robot's own block is not enough: the cell straddles a chunk border about as
     *  often as not, and the chunk next door starts ticking later than the one the force-load was centred on (on
     *  26.3 with only the robot's chunk forced, the far corners came up on tick 20-23 while the robot's was ticking
     *  on tick 1). Blocks, items and vines in that neighbour neither tick nor random-tick until it does. */
    public static boolean isCellEntityTicking(GameTestHelper gth) {
        return isEntityTicking(gth, BlockPos.ZERO)
                && isEntityTicking(gth, new BlockPos(CELL_SIZE_X - 1, 0, 0))
                && isEntityTicking(gth, new BlockPos(0, 0, CELL_SIZE_Z - 1))
                && isEntityTicking(gth, new BlockPos(CELL_SIZE_X - 1, 0, CELL_SIZE_Z - 1));
    }

    /** {@link #forceLoadEntityArena(GameTestHelper, BlockPos)} for every chunk the test's cell touches (its four
     *  corners), so a cell straddling a chunk border has both halves force-loaded as the centre of their own 5x5,
     *  not just the half the robot stands in. Measured on 26.3 (48 arenas in one batch): with only the robot's
     *  chunk forced the far corners of a straddling cell came up on tick 20-23; with the cell's chunks forced they
     *  were entity-ticking on tick 1 in every arena. */
    public static void forceLoadCell(GameTestHelper gth) {
        forceLoadEntityArena(gth, BlockPos.ZERO);
        forceLoadEntityArena(gth, new BlockPos(CELL_SIZE_X - 1, 0, 0));
        forceLoadEntityArena(gth, new BlockPos(0, 0, CELL_SIZE_Z - 1));
        forceLoadEntityArena(gth, new BlockPos(CELL_SIZE_X - 1, 0, CELL_SIZE_Z - 1));
    }

    /** A {@link #tickUntil} condition that starts the clock only once the cell is entity-ticking
     *  ({@link #isCellEntityTicking}): it answers {@code false} until then, never asking {@code condition}, and then
     *  gives {@code condition} {@code workTicks} polls to come true. Pass {@code readyTicks + workTicks} as the
     *  poll's own budget (a literal or a {@code static final int}, so {@code GameTestPollBudgetTester} can read it)
     *  and keep the manifest's {@code max_ticks} above it.
     *
     *  <p>Chunk promotion is asynchronous, so how long a cell takes to start ticking depends on the machine, not on
     *  the code under test; a budget that includes that wait flakes under load (see the two tests this was made
     *  for). Gating the budget on the observed state separates the two failures: a cell that never ticks fails as
     *  such, naming the machine, and a robot that is slow fails with how long it had once the cell was ticking.
     *  Both fail through {@code gth.fail}, so the poll's cleanup still runs. */
    public static BooleanSupplier whenCellTicking(GameTestHelper gth, int readyTicks, int workTicks,
                                                  BooleanSupplier condition) {
        return gateOnReady(() -> isCellEntityTicking(gth), gth::fail, readyTicks, workTicks, condition);
    }

    /** The GameTestHelper-free core of {@link #whenCellTicking}: one call of the result is one tick's poll.
     *  {@code fail} must throw, as {@code GameTestHelper.fail} does on every node. */
    static BooleanSupplier gateOnReady(BooleanSupplier ready, Consumer<String> fail, int readyTicks, int workTicks,
                                       BooleanSupplier condition) {
        int[] polls = { 0 };
        int[] readyAt = { -1 };
        return () -> {
            int poll = ++polls[0];
            if (readyAt[0] < 0) {
                if (!ready.getAsBoolean()) {
                    if (poll >= readyTicks) {
                        fail.accept("the arena cell never became entity-ticking within " + readyTicks
                                + " ticks: its chunks were still being promoted, which is the machine's load and not"
                                + " the code under test");
                    }
                    return false;
                }
                readyAt[0] = poll;
            }
            if (condition.getAsBoolean()) {
                return true;
            }
            if (poll - readyAt[0] >= workTicks) {
                fail.accept("not done " + workTicks + " ticks after the cell became entity-ticking (on tick "
                        + readyAt[0] + ")");
            }
            return false;
        };
    }

    /** Discards every non-null, still-present entity: the null-safe body of a {@code tickUntil} cleanup. */
    public static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    /** Two-phase {@link #tickUntil}: polls for {@code condition}, runs {@code first} on the tick it comes
     *  true, then runs {@code second} {@code gapTicks} ticks after that.
     *
     * <p>This exists because the second phase must be scheduled relative to the FIRST phase, not to the test
     * clock. A test that waits for a real precondition and then hard-codes {@code runAfterDelay(9)} for its
     * follow-up has re-introduced the very race it just fixed — if phase one lands at tick 4 instead of tick
     * 1, the "later" phase runs first. Every poll is scheduled up front for the reason given on
     * {@link #tickUntil}. */
    public static void tickUntilThen(GameTestHelper gth, int maxTicks, BooleanSupplier condition,
                                     Runnable first, int gapTicks, Runnable second, String failureMessage) {
        tickUntilThen(gth, maxTicks, condition, first, gapTicks, second, failureMessage, NO_CLEANUP);
    }

    /** {@link #tickUntilThen(GameTestHelper, int, BooleanSupplier, Runnable, int, Runnable, String)} with a
     *  {@code cleanup} that runs exactly once on every way out: after {@code second}, when the condition,
     *  {@code first} or {@code second} throws, and on timeout — never between the two phases. A condition that
     *  first comes true on the poll's very last tick fails the poll without running {@code first}: no tick is left
     *  to run {@code second} or the cleanup. See
     *  {@link #tickUntil(GameTestHelper, int, BooleanSupplier, Runnable, String, Runnable)}. */
    public static void tickUntilThen(GameTestHelper gth, int maxTicks, BooleanSupplier condition,
                                     Runnable first, int gapTicks, Runnable second, String failureMessage,
                                     Runnable cleanup) {
        schedulePollThen(gth::runAfterDelay, gth::fail, maxTicks, condition, first, gapTicks, second,
                failureMessage, cleanup);
    }

    /** The GameTestHelper-free core of {@link #tickUntilThen}. */
    static void schedulePollThen(Scheduler scheduler, Consumer<String> fail, int maxTicks, BooleanSupplier condition,
                                 Runnable first, int gapTicks, Runnable second, String failureMessage,
                                 Runnable cleanup) {
        int[] firstFiredAt = { -1 };
        boolean[] done = { false };
        for (int t = 1; t <= maxTicks; t++) {
            final int tick = t;
            scheduler.runAfterDelay(tick, () -> {
                if (done[0]) {
                    return;
                }
                boolean keepPolling = false;
                Throwable failure = null;
                try {
                    if (firstFiredAt[0] < 0) {
                        if (condition.getAsBoolean()) {
                            if (tick >= maxTicks) {
                                // The poll's last tick: no later tick exists to run the second phase, the "second
                                // phase past maxTicks" fail below, or the cleanup. Reachable with a gap of zero
                                // (the give-up check below only trips gapTicks ticks before the end) or a poll of
                                // one tick. Letting the first phase fire would leave the test to the framework's
                                // timeout with its entities still in the level, so fail here instead.
                                done[0] = true;
                                fail.accept(failureMessage + " (condition came true on the last tick, " + maxTicks
                                        + ", leaving none for the second phase)");
                            } else {
                                firstFiredAt[0] = tick;
                                first.run();
                                keepPolling = true;
                            }
                        } else if (tick + gapTicks >= maxTicks) {
                            done[0] = true;
                            fail.accept(failureMessage + " (waited " + (maxTicks - gapTicks) + " ticks)");
                        } else {
                            keepPolling = true;
                        }
                    } else if (tick >= firstFiredAt[0] + gapTicks) {
                        done[0] = true;
                        second.run();
                    } else if (tick == maxTicks) {
                        // Only reachable when gapTicks >= maxTicks (the pre-first-phase check above keeps every
                        // other late start from firing): the second phase cannot land inside the poll, so fail
                        // here and let the finally clean up rather than leave it to the framework timeout.
                        done[0] = true;
                        fail.accept(failureMessage + " (second phase due at tick " + (firstFiredAt[0] + gapTicks)
                                + ", past the poll's " + maxTicks + ")");
                    } else {
                        keepPolling = true;
                    }
                } catch (Throwable t2) {
                    failure = t2;
                    keepPolling = false;
                    throw t2;
                } finally {
                    if (!keepPolling) {
                        done[0] = true;
                        runCleanup(cleanup, failure);
                    }
                }
            });
        }
    }

    /** Serialises {@code entity} exactly the way a chunk save would, minus the type id. */
    public static CompoundTag saveEntity(ServerLevel level, Entity entity) {
        //? if >=1.21.10 {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
        //?} else {
        /*return entity.saveWithoutId(new CompoundTag());*/
        //?}
    }

    /** Deserialises {@code tag} into {@code entity} exactly the way a chunk load would. */
    public static void loadEntity(ServerLevel level, Entity entity, CompoundTag tag) {
        //? if >=1.21.10 {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        //?} else {
        /*entity.load(tag);*/
        //?}
    }

    /** Applies server-side damage. {@code hurt(DamageSource, float)} became
     *  {@code hurtServer(ServerLevel, DamageSource, float)} at 1.21.10. */
    public static boolean hurt(ServerLevel level, Entity entity, DamageSource source, float amount) {
        //? if >=1.21.10 {
        return entity.hurtServer(level, source, amount);
        //?} else {
        /*return entity.hurt(source, amount);*/
        //?}
    }

    /** Fills tank 0 of a BuildCraft fluid handler. The Transfer API (>=1.21.10) replaced the classic
     *  {@code fill(FluidStack, FluidAction)} with an indexed, transactional insert. */
    public static int fillTank(IFluidHandlerAdv handler, Fluid fluid, int amount) {
        //? if >=1.21.10 {
        try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int filled = handler.insert(0, net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluid), amount, tx);
            tx.commit();
            return filled;
        }
        //?} else {
        /*return handler.fill(new net.neoforged.neoforge.fluids.FluidStack(fluid, amount),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);*/
        //?}
    }

    /** @return the contents of tank 0, in mB. */
    public static long tankAmount(IFluidHandlerAdv handler) {
        //? if >=1.21.10 {
        return handler.getAmountAsLong(0);
        //?} else {
        /*return handler.getFluidInTank(0).getAmount();*/
        //?}
    }

    /** Every loose {@link ItemEntity} within {@code radius} of {@code relPos}. Uses
     *  {@code Level.getEntitiesOfClass} rather than an {@code EntityType} constant so no 26.2
     *  {@code EntityType} -> {@code EntityTypes} fork is needed, and keeps the radius tight because the
     *  {@code RobotRegistry}-style shared arena batch means loose drops from a neighbouring test are a real
     *  possibility. */
    public static List<ItemEntity> droppedItems(GameTestHelper gth, BlockPos relPos, double radius) {
        Vec3 centre = Vec3.atCenterOf(gth.absolutePos(relPos));
        return gth.getLevel().getEntitiesOfClass(ItemEntity.class,
                AABB.ofSize(centre, radius * 2, radius * 2, radius * 2));
    }
}
