/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.common.SoundAction;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.Platform;

/**
 * The bucket sounds BuildCraft plays when a tank swaps fluid with a bucket come from the fluid type, asked about the
 * stack itself ({@code FluidType.getSound(FluidStack, SoundAction)}), as they did before the platform layer. A fluid
 * type that sounds per stack (an amount, a component) is only heard through that overload; the plain
 * {@code getSound(SoundAction)} one has no stack to look at.
 */
public class NeoForgeSoundServicesTest extends VanillaSetupBaseTester {

    /** The only stack size the test fluid type has a sound for. */
    private static final int MARKED_AMOUNT = 1234;

    private static final FluidType PER_STACK_TYPE = new FluidType(FluidType.Properties.create()) {
        @Override
        public SoundEvent getSound(FluidStack stack, SoundAction action) {
            if (stack.getAmount() != MARKED_AMOUNT) {
                return null;
            }
            return action == SoundActions.BUCKET_FILL ? SoundEvents.BUCKET_FILL_FISH : SoundEvents.BUCKET_EMPTY_FISH;
        }
    };

    /** A fluid whose type is {@link #PER_STACK_TYPE}. Never constructed (see {@link #perStackFluid}). */
    private static final class PerStackFluid extends EmptyFluid {
        @Override
        public FluidType getFluidType() {
            return PER_STACK_TYPE;
        }
    }

    /**
     * A {@link PerStackFluid} without running its constructor: a {@code Fluid} registers itself in the (frozen) fluid
     * registry when constructed, and a fluid type is only ever reached through {@code getFluidType()}.
     */
    private static Fluid perStackFluid() throws ReflectiveOperationException {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        return (Fluid) unsafe.allocateInstance(PerStackFluid.class);
    }

    @Test
    public void bucketSoundAsksTheFluidTypeAboutTheStack() throws ReflectiveOperationException {
        Fluid fluid = perStackFluid();

        BCFluidStack marked = new BCFluidStack(fluid, MARKED_AMOUNT);
        assertSame(SoundEvents.BUCKET_FILL_FISH, Platform.sounds().bucketSound(marked, true));
        assertSame(SoundEvents.BUCKET_EMPTY_FISH, Platform.sounds().bucketSound(marked, false));

        // Another stack of the same fluid: the type has no sound for it, so the caller's default plays.
        assertNull(Platform.sounds().bucketSound(new BCFluidStack(fluid, 1000), true));
    }

    @Test
    public void bucketSoundOfAVanillaFluidIsItsTypesSound() {
        BCFluidStack lava = new BCFluidStack(Fluids.LAVA, 1000);
        assertSame(SoundEvents.BUCKET_FILL_LAVA, Platform.sounds().bucketSound(lava, true));
        assertSame(SoundEvents.BUCKET_EMPTY_LAVA, Platform.sounds().bucketSound(lava, false));
    }
}
