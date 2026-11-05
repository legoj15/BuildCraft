/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.function.Supplier;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.SoundServices;

/^* {@link SoundServices} on Fabric: a {@link SoundType} subclass that resolves its events on read, and vanilla's pickup sound. ^/
public final class FabricSoundServices implements SoundServices {
    @Override
    public SoundType deferredSoundType(float volume, float pitch, Supplier<SoundEvent> breakSound,
        Supplier<SoundEvent> stepSound, Supplier<SoundEvent> placeSound, Supplier<SoundEvent> hitSound,
        Supplier<SoundEvent> fallSound) {
        // The vanilla constructor wants live events; EMPTY's stand in, and every getter is overridden to look up late.
        SoundType e = SoundType.EMPTY;
        return new SoundType(volume, pitch, e.getBreakSound(), e.getStepSound(), e.getPlaceSound(), e.getHitSound(),
            e.getFallSound()) {
            @Override
            public SoundEvent getBreakSound() {
                return breakSound.get();
            }

            @Override
            public SoundEvent getStepSound() {
                return stepSound.get();
            }

            @Override
            public SoundEvent getPlaceSound() {
                return placeSound.get();
            }

            @Override
            public SoundEvent getHitSound() {
                return hitSound.get();
            }

            @Override
            public SoundEvent getFallSound() {
                return fallSound.get();
            }
        };
    }

    @Override
    public SoundEvent bucketSound(BCFluidStack stack, boolean fill) {
        // Fabric has no per-fluid sound table: a fill uses vanilla's pickup sound, an empty uses the caller's default.
        return fill ? stack.getFluid().getPickupSound().orElse(null) : null;
    }
}
*///?}
