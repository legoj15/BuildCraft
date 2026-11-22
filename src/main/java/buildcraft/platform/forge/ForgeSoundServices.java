/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.Supplier;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;

import net.minecraftforge.common.SoundActions;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.SoundServices;

/^*
 * {@link SoundServices} on MinecraftForge: {@link ForgeSoundType} (Forge's supplier-based sound type, the twin of
 * NeoForge's deferred one, same parameters) and the fluid type's {@link SoundActions}.
 *
 * <p>Forge's {@code FluidStack} holds a tag, not data components, so the stack handed to the fluid type carries the
 * fluid and the amount only: BuildCraft's component patch has no Forge counterpart. A fluid type that sounds
 * differently per stack by reading components has nothing to read here; Forge's own default asks the plain action.
 *^/
public final class ForgeSoundServices implements SoundServices {
    @Override
    public SoundType deferredSoundType(float volume, float pitch, Supplier<SoundEvent> breakSound,
        Supplier<SoundEvent> stepSound, Supplier<SoundEvent> placeSound, Supplier<SoundEvent> hitSound,
        Supplier<SoundEvent> fallSound) {
        return new ForgeSoundType(volume, pitch, breakSound, stepSound, placeSound, hitSound, fallSound);
    }

    @Override
    public SoundEvent bucketSound(BCFluidStack stack, boolean fill) {
        // The stack overload, as BuildCraft always asked: a fluid type may sound per stack (its default is the plain one).
        FluidStack forgeStack = stack.isEmpty() ? FluidStack.EMPTY : new FluidStack(stack.getFluid(), stack.getAmount());
        return stack.getFluid().getFluidType().getSound(forgeStack,
            fill ? SoundActions.BUCKET_FILL : SoundActions.BUCKET_EMPTY);
    }
}
*///?}
