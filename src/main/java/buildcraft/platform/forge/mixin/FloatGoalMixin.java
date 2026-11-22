/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
//? if >=26.1 {
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.material.Fluid;

import buildcraft.platform.forge.ForgeFluidPhysics;

/^*
 * NeoForge's {@code FloatGoal.canUse} for BuildCraft's fluids ({@link ForgeFluidPhysics}): a mob floats only in a fluid
 * it can swim in, deeper than its jump threshold, rather than in anything the floatable tag (and so the water tag, which
 * every BuildCraft fluid is in) names. A fluid that is not BuildCraft's is still judged by that tag, as vanilla does.
 * Forge's own patch already ORs in a test of every fluid type's {@code canSwimInFluidType}; the tag test it leaves in
 * front of that is the one that lets a mob float in crude oil, so it is the one replaced.
 *
 * <p><b>Injection point.</b> The call to {@code Mob.isInFluidDeeperThan(double, TagKey)} inside {@code canUse}, the call
 * Forge's patch keeps and adds a disjunct after. Named call, no ordinal, no local.
 *^/
@Mixin(FloatGoal.class)
abstract class FloatGoalMixin {
    @WrapOperation(method = "canUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;isInFluidDeeperThan(DLnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$floats(Mob mob, double depth, TagKey<Fluid> tag, Operation<Boolean> original) {
        // A goal built for another tag (a mob that floats on something else) keeps vanilla's test.
        return tag == FluidTags.ENTITY_FLOATABLE
                ? ForgeFluidPhysics.interaction(mob).buildcraft$inSwimmableDeeperThan(depth, tag)
                : original.call(mob, depth, tag);
    }
}
//?} else {
/^import net.minecraft.world.entity.ai.goal.FloatGoal;

// Forge 1.21.1's and 1.21.11's FloatGoal ORs in canSwimInFluidType, which answers from the fluid type's own swim flag.
// Empty, so the mixin config names the same classes on every Forge line.
@Mixin(FloatGoal.class)
abstract class FloatGoalMixin {
}
^///?}
*///?}
