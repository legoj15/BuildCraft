/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.List;
import java.util.function.BooleanSupplier;

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
 * gated build-script regex that routes {@code helper.assertTrue(...)} through {@link GameTestUtil} on the
 * 1.21.10 node is textual, and a {@code helper}-named receiver here would be rewritten. Nothing in this class
 * asserts; it reports failures through {@code fail(String)}, which is uniform on every node.
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
     *  entity can sit near the arena's far edge. */
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
        boolean[] fired = { false };
        for (int t = 1; t <= maxTicks; t++) {
            final int tick = t;
            gth.runAfterDelay(tick, () -> {
                if (fired[0]) {
                    return;
                }
                if (condition.getAsBoolean()) {
                    fired[0] = true;
                    then.run();
                } else if (tick == maxTicks) {
                    fired[0] = true;
                    gth.fail(failureMessage + " (waited " + maxTicks + " ticks)");
                }
            });
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
        int[] firstFiredAt = { -1 };
        boolean[] done = { false };
        for (int t = 1; t <= maxTicks; t++) {
            final int tick = t;
            gth.runAfterDelay(tick, () -> {
                if (done[0]) {
                    return;
                }
                if (firstFiredAt[0] < 0) {
                    if (condition.getAsBoolean()) {
                        firstFiredAt[0] = tick;
                        first.run();
                    } else if (tick + gapTicks >= maxTicks) {
                        done[0] = true;
                        gth.fail(failureMessage + " (waited " + (maxTicks - gapTicks) + " ticks)");
                    }
                } else if (tick >= firstFiredAt[0] + gapTicks) {
                    done[0] = true;
                    second.run();
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
