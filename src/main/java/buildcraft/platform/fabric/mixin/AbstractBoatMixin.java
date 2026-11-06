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

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import buildcraft.platform.fabric.FabricFlowingFluids;
import buildcraft.platform.fabric.FabricFluidPhysics;

/^*
 * NeoForge's {@code canBoatInFluid} on Fabric: a boat floats only on a fluid whose type supports boating, which
 * BuildCraft's fluid types do not (the default is off), so a boat sinks in oil and fuel. Vanilla asks the water tag at
 * five places, and every BuildCraft fluid is in it, so there a boat would sail on them. Those five (the water level
 * above the boat, whether it is in water, under water, whether its fall ends in water, and whether a passenger may
 * board with its eyes in water) now leave BuildCraft's fluids out.
 *^/
@Mixin(AbstractBoat.class)
abstract class AbstractBoatMixin {

    /^* The four {@code FluidState.is(WATER)} tests (NeoForge's {@code canBoatInFluid(fluidState)}). *^/
    @WrapOperation(method = {"getWaterLevelAbove", "checkInWater", "isUnderwater", "checkFallDamage"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$boatsFloatOnWaterOnly(FluidState state, TagKey<Fluid> tag,
                                                     Operation<Boolean> original) {
        return original.call(state, tag)
                && !(tag == FluidTags.WATER && FabricFlowingFluids.declared(state.getType()) != null);
    }

    /^* The boarding test (NeoForge's {@code isEyeInFluidMatching(canBoatInFluid)}): eyes in a fluid boats float on. *^/
    @WrapOperation(method = "canAddPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$eyeInBoatableFluid(AbstractBoat boat, TagKey<Fluid> tag, Operation<Boolean> original) {
        return tag == FluidTags.WATER
                ? ((FabricFluidPhysics.Interaction) ((EntityAccessor) boat).buildcraft$fluidInteraction())
                        .buildcraft$eyeInBoatableWater()
                : original.call(boat, tag);
    }
}
*///?}
