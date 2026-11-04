/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.energy.BCEnergyConfig.ContainedDetonation;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.platform.Platform;
import buildcraft.platform.event.HookOrder;

/**
 * Gaseous fuel stored in a tank or fluid pipe exploding (issue #61), as the {@code containedFluidDetonation} config
 * decides. {@link FluidVolatility} covers fluid placed in the world; this covers fluid held as data by a
 * {@link Container}.
 *
 * <p><b>Triggers.</b> A container holding {@linkplain FluidVolatility.Band#EXPLOSIVE explosive} fluid goes off when an
 * {@linkplain FluidVolatility#isIgnitionSource ignition source} is beside it (checked from its own tick every
 * {@link #CHECK_INTERVAL} ticks, and only while it holds gas), or when a blast is about to destroy it. A hot dimension
 * alone never sets a container off: it is sealed, unlike a pool of gas.
 *
 * <p><b>Yield.</b> See {@link #blastPower}. {@code PER_BLOCK} treats every container holding at least a bucket as one
 * gas block; {@code BY_VOLUME} scales the blast's power with the cube root of the stored volume, so the volume a blast
 * destroys (which grows with the cube of its power) grows in step with the gas: one bucket is TNT, eight buckets are
 * twice TNT's power, a full tank about two and a half times.
 *
 * <p><b>Bounds.</b> A triggered container is emptied on the spot (the gas is spent, and a blast-destroyed tank must not
 * also spill it as shards) and its blast is queued for a short fuse, never fired from inside a tick or another blast.
 * The queue fires at most {@link #MAX_BLASTS_PER_TICK} blasts per level per tick, so a stack of tanks that blow each
 * other up goes off as a quick ripple instead of in one tick.
 */
public final class ContainedFluidDetonation {

    private ContainedFluidDetonation() {}

    /** A block entity whose stored fluid can detonate. */
    public interface Container {
        /** Everything the container holds, as one stack of its whole amount (empty when it holds nothing). */
        BCFluidStack containedFluid();

        /** Empties the container: its contents went up in the blast. */
        void ventContainedFluid();
    }

    /** One bucket: the least {@code PER_BLOCK} needs, and the volume that {@code BY_VOLUME} gives TNT's power. */
    static final int BUCKET = 1000;

    /** Strongest blast any one container makes, whatever it holds: a lag guard against huge tanks. */
    static final float MAX_BLAST_POWER = 12.0F;

    /** Ticks between two looks for an ignition source around a container that holds gas. */
    static final int CHECK_INTERVAL = 10;

    /** Most queued blasts fired in one level per tick; the rest wait for the next. */
    static final int MAX_BLASTS_PER_TICK = 4;

    /** Fewest and most ticks (inclusive) a triggered container waits before its blast. */
    private static final int FUSE_MIN = 2;
    private static final int FUSE_MAX = 5;

    private static final class Pending {
        final BlockPos pos;
        final float power;
        int fuse;

        Pending(BlockPos pos, float power, int fuse) {
            this.pos = pos;
            this.power = power;
            this.fuse = fuse;
        }
    }

    private static final Map<Level, List<Pending>> PENDING = new WeakHashMap<>();

    public static void init() {
        // Lowest, so claim and robot protection have already taken the blocks they keep out of the list
        Platform.gameHooks().onExplosionDetonate(HookOrder.LATE, ContainedFluidDetonation::onExplosionDetonate);
        Platform.gameHooks().onLevelTickEnd(ContainedFluidDetonation::onLevelTick);
        // Server levels only: an integrated client's levels post the same event, on the client thread
        Platform.gameHooks().onLevelUnload(unloaded -> {
            if (unloaded instanceof Level level && !level.isClientSide()) {
                PENDING.remove(level);
            }
        });
    }

    /**
     * The power of the blast a container holding {@code millibuckets} of explosive fluid makes, or 0 for none.
     * {@code PER_BLOCK}: a gas block's blast from one bucket up. {@code BY_VOLUME}: TNT's power times the cube root of
     * the volume in buckets, up to {@link #MAX_BLAST_POWER}.
     */
    static float blastPower(ContainedDetonation mode, int millibuckets) {
        if (millibuckets <= 0) {
            return 0;
        }
        return switch (mode) {
            case OFF -> 0;
            case PER_BLOCK -> millibuckets >= BUCKET ? FluidVolatility.BLAST_POWER : 0;
            case BY_VOLUME -> Math.min(MAX_BLAST_POWER,
                (float) (FluidVolatility.BLAST_POWER * Math.cbrt(millibuckets / (double) BUCKET)));
        };
    }

    /** Whether {@code stack} is a BuildCraft fluid in the {@link FluidVolatility.Band#EXPLOSIVE} band, source or
     *  flowing form (read from the block the fluid places, which carries the band). */
    static boolean isExplosive(BCFluidStack stack) {
        return !stack.isEmpty() && FluidVolatility.isExplosive(stack.getFluid().defaultFluidState().createLegacyBlock());
    }

    /** The power {@code container} would blow up with now, or 0 when it holds no gas or the config is off. */
    private static float powerOf(Container container) {
        BCFluidStack contents = container.containedFluid();
        if (!isExplosive(contents)) {
            return 0;
        }
        return blastPower(BCEnergyConfig.containedDetonationMode(), contents.getAmount());
    }

    /**
     * The fire trigger, for the container's server tick: every {@link #CHECK_INTERVAL} ticks (staggered by position),
     * a container holding gas with an ignition source beside it is emptied and its blast queued.
     */
    public static void tickContainer(Level level, BlockPos pos, Container container) {
        if (level.isClientSide() || Math.floorMod(level.getGameTime() + pos.hashCode(), CHECK_INTERVAL) != 0) {
            return;
        }
        float power = powerOf(container);
        if (power > 0 && FluidVolatility.isIgnitionNearby(level, pos)) {
            trigger(level, pos, container, power);
        }
    }

    /** The blast trigger: every container a block-breaking blast is about to destroy that holds gas goes off after
     *  it. */
    private static void onExplosionDetonate(Level level, Explosion explosion, List<BlockPos> affectedBlocks,
            List<Entity> affectedEntities) {
        if (level.isClientSide() || BCEnergyConfig.containedDetonationMode() == ContainedDetonation.OFF) {
            return;
        }
        // The list is filled even for a blast that breaks nothing (a wind charge, a creeper with mobGriefing off)
        Explosion.BlockInteraction interaction = explosion.getBlockInteraction();
        if (interaction != Explosion.BlockInteraction.DESTROY
                && interaction != Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
            return;
        }
        for (BlockPos pos : affectedBlocks) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                float power = powerOf(container);
                if (power > 0) {
                    trigger(level, pos, container, power);
                }
            }
        }
    }

    private static void trigger(Level level, BlockPos pos, Container container, float power) {
        container.ventContainedFluid();
        int fuse = FUSE_MIN + level.getRandom().nextInt(FUSE_MAX - FUSE_MIN + 1);
        PENDING.computeIfAbsent(level, l -> new ArrayList<>()).add(new Pending(pos.immutable(), power, fuse));
    }

    private static void onLevelTick(ServerLevel level) {
        List<Pending> pending = PENDING.get(level);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        // Take this tick's blasts out first: each blast can queue more containers onto the same list
        List<Pending> ready = new ArrayList<>();
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending blast = it.next();
            if (--blast.fuse <= 0 && ready.size() < MAX_BLASTS_PER_TICK) {
                ready.add(blast);
                it.remove();
            }
        }
        for (Pending blast : ready) {
            if (BlockUtil.isChunkPresent(level, blast.pos)) {
                level.explode(null, blast.pos.getX() + 0.5, blast.pos.getY() + 0.5, blast.pos.getZ() + 0.5,
                    blast.power, true, Level.ExplosionInteraction.BLOCK);
            }
        }
    }
}
