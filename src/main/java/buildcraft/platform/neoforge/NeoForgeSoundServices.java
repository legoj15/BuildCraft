/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.function.Supplier;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.common.util.DeferredSoundType;

import buildcraft.platform.SoundServices;

/** {@link SoundServices} on NeoForge: {@link DeferredSoundType} and the fluid type's {@link SoundActions}. */
public final class NeoForgeSoundServices implements SoundServices {
    @Override
    public SoundType deferredSoundType(float volume, float pitch, Supplier<SoundEvent> breakSound,
        Supplier<SoundEvent> stepSound, Supplier<SoundEvent> placeSound, Supplier<SoundEvent> hitSound,
        Supplier<SoundEvent> fallSound) {
        return new DeferredSoundType(volume, pitch, breakSound, stepSound, placeSound, hitSound, fallSound);
    }

    @Override
    public SoundEvent bucketSound(Fluid fluid, boolean fill) {
        return fluid.getFluidType().getSound(fill ? SoundActions.BUCKET_FILL : SoundActions.BUCKET_EMPTY);
    }
}
//?}
