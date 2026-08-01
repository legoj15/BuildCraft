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
 *     in ten. {@link #forceLoadEntityArena} is the 3x3 chunk force-load that fixes it (the framework unforces
 *     everything it recorded at batch end, so this self-cleans).</li>
 * <li><b>Waiting for something then doing more work.</b> {@code GameTestHelper.succeedWhen} polls, but it also
 *     ends the test, and it may be used only once. {@link #tickUntil} polls for a condition and then hands
 *     control back so the test can carry on.</li>
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

    /** Force-loads the 3x3 chunk block centred on the arena origin, so entities anywhere in a small arena
     *  actually tick. See the class javadoc for why this is not optional. */
    public static void forceLoadEntityArena(GameTestHelper gth) {
        forceLoadEntityArena(gth, BlockPos.ZERO);
    }

    /** Force-loads the 3x3 chunk block centred on {@code relPos}'s chunk. 3x3 rather than 1x1 because the
     *  framework can drop a test's origin at any chunk alignment, so the arena straddles a chunk border about
     *  as often as not. */
    public static void forceLoadEntityArena(GameTestHelper gth, BlockPos relPos) {
        BlockPos abs = gth.absolutePos(relPos);
        int chunkX = abs.getX() >> 4;
        int chunkZ = abs.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                gth.getLevel().setChunkForced(chunkX + dx, chunkZ + dz, true);
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
