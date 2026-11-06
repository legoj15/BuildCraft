/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;

import buildcraft.api.fluids.BCFluidStack;

/** Sound types whose events resolve late, and the loader's per-fluid bucket sounds. */
public interface SoundServices {
    /** A {@link SoundType} whose five events are looked up when played, so it can be built before registries fill. */
    SoundType deferredSoundType(float volume, float pitch, Supplier<SoundEvent> breakSound,
        Supplier<SoundEvent> stepSound, Supplier<SoundEvent> placeSound, Supplier<SoundEvent> hitSound,
        Supplier<SoundEvent> fallSound);

    /**
     * The sound {@code stack}'s fluid makes when a bucket of it is emptied ({@code fill} false) or filled, or null for
     * the default. It takes the stack, not just the fluid, because a loader may sound per stack (NeoForge asks the
     * fluid type about the stack itself).
     */
    @Nullable
    SoundEvent bucketSound(BCFluidStack stack, boolean fill);
}
