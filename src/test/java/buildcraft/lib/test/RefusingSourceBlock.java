/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * A test-only stand-in for a modded {@link BucketPickup} that reports a water source but refuses
 * {@link #pickupBlock}: no vanilla block does that, and it is the only way to reach the "world refused the bucket"
 * branch of {@code BlockUtil.drainBlock}. The block is inert (no fluid ticks, no spreading) and counts every pickup
 * attempt per position, so a test can tell a machine that gave up on the cell from one that keeps retrying it.
 * Registered by {@link TestOnlyBlocks}.
 */
public final class RefusingSourceBlock extends Block implements BucketPickup {

    private static final Map<BlockPos, AtomicInteger> ATTEMPTS = new ConcurrentHashMap<>();

    public RefusingSourceBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** How many times something tried to pick this block up at {@code pos}. Keyed by absolute position. */
    public static int attemptsAt(BlockPos pos) {
        AtomicInteger count = ATTEMPTS.get(pos);
        return count == null ? 0 : count.get();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    //? if >=1.21.10 {
    @Override
    public ItemStack pickupBlock(LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state) {
        ATTEMPTS.computeIfAbsent(pos.immutable(), p -> new AtomicInteger()).incrementAndGet();
        return ItemStack.EMPTY;
    }
    //?} else {
    /*@Override
    public ItemStack pickupBlock(net.minecraft.world.entity.player.Player user, LevelAccessor level, BlockPos pos, BlockState state) {
        ATTEMPTS.computeIfAbsent(pos.immutable(), p -> new AtomicInteger()).incrementAndGet();
        return ItemStack.EMPTY;
    }*/
    //?}

    // BucketPickup#getPickupSound() is @Deprecated (NeoForge wants the state-sensitive variant) but still abstract,
    // so it has to be implemented; nothing ever asks for a sound from a refusing block.
    @Override
    @SuppressWarnings("deprecation")
    public Optional<SoundEvent> getPickupSound() {
        return Optional.empty();
    }
}
