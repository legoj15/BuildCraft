/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
//? if >=1.21.11 {
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
//?} elif >=1.21.10 {
/^import net.minecraft.world.entity.vehicle.AbstractBoat;
^///?} else {
/^import net.minecraft.world.entity.vehicle.Boat;
^///?}
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import buildcraft.platform.BoatFluidMotion;
import buildcraft.platform.fabric.FabricFluidPhysics;

/^*
 * NeoForge's {@code canBoatInFluid} on Fabric: a boat floats on a fluid whose type supports boating, which BuildCraft's
 * fluid types do for every liquid and not for a gas ({@code FluidProperties#supportsBoating}). Vanilla asks the water
 * tag at five places, and every BuildCraft fluid is in it, so a gas would hold a boat up there. Those five (the water
 * level above the boat, whether it is in water, under water, whether its fall ends in water, and whether a passenger
 * may board with its eyes in water) leave out a BuildCraft fluid that declares no boating and count every other
 * water-tag fluid as before. The end of the boat's tick then runs {@link BoatFluidMotion}, the drag of a sticky fluid
 * that NeoForge applies from an entity-tick listener.
 *^/
//? if >=1.21.10 {
@Mixin(AbstractBoat.class)
//?} else {
/^@Mixin(Boat.class)
^///?}
abstract class AbstractBoatMixin {

    /^* The four {@code FluidState.is(WATER)} tests (NeoForge's {@code canBoatInFluid(fluidState)}). *^/
    @WrapOperation(method = {"getWaterLevelAbove", "checkInWater", "isUnderwater", "checkFallDamage"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$boatsFloatOnBoatableWater(FluidState state, TagKey<Fluid> tag,
                                                         Operation<Boolean> original) {
        return original.call(state, tag)
                && (tag != FluidTags.WATER || FabricFluidPhysics.boatsFloatOn(state.getType()));
    }

    /^* The boarding test (NeoForge's {@code isEyeInFluidMatching(canBoatInFluid)}): eyes in a fluid boats float on. *^/
    //? if >=1.21.11 {
    @WrapOperation(method = "canAddPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    //?} elif >=1.21.10 {
    /^@WrapOperation(method = "canAddPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/AbstractBoat;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    ^///?} else {
    /^@WrapOperation(method = "canAddPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/Boat;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    ^///?}
    //? if >=1.21.10 {
    private boolean buildcraft$eyeInBoatableFluid(AbstractBoat boat, TagKey<Fluid> tag, Operation<Boolean> original) {
    //?} else {
    /^private boolean buildcraft$eyeInBoatableFluid(Boat boat, TagKey<Fluid> tag, Operation<Boolean> original) {
    ^///?}
        return tag == FluidTags.WATER
                ? FabricFluidPhysics.interaction(boat).buildcraft$eyeInBoatableWater()
                : original.call(boat, tag);
    }

    /^* The end of the boat's tick (NeoForge: {@code EntityTickEvent.Post}), after it has floated and moved. *^/
    @Inject(method = "tick", at = @At("TAIL"))
    private void buildcraft$stickyFluidDrag(CallbackInfo ci) {
        BoatFluidMotion.tick((net.minecraft.world.entity.Entity) (Object) this);
    }
}
*///?}
