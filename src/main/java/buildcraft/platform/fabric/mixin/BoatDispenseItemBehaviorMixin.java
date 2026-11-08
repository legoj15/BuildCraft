/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.dispenser.BoatDispenseItemBehavior;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import buildcraft.platform.fabric.FabricFluidPhysics;

/^*
 * NeoForge's {@code canBoatInFluid} at a dispenser on Fabric: a dispenser places a boat on the fluid in front of it
 * (or the one under an empty block in front of it) only if boats float on that fluid, and ejects the item otherwise.
 * Vanilla asks the water tag at both places, and every BuildCraft fluid is in it, so a dispenser would set a boat down
 * on a gas that sinks it. Both tests leave out a BuildCraft fluid that declares no boating (as {@link AbstractBoatMixin}
 * does for a boat already afloat) and count every other water-tag fluid as before. Vanilla's two tests are the same
 * on every supported line, so the wrap needs no version arm.
 *^/
@Mixin(BoatDispenseItemBehavior.class)
abstract class BoatDispenseItemBehaviorMixin {

    /^* The two {@code FluidState.is(WATER)} tests in {@code execute} (NeoForge's {@code canBoatInFluid(fluidState)}). *^/
    @WrapOperation(method = "execute",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$boatsFloatOnBoatableWater(FluidState state, TagKey<Fluid> tag,
                                                         Operation<Boolean> original) {
        return original.call(state, tag)
                && (tag != FluidTags.WATER || FabricFluidPhysics.boatsFloatOn(state.getType()));
    }
}
*///?}
