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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.material.Fluid;

import buildcraft.platform.fabric.FabricFluidPhysics;

/^*
 * NeoForge's {@code FloatGoal.canUse} for BuildCraft's fluids ({@link FabricFluidPhysics}): a mob floats only in a fluid
 * it can swim in, deeper than its jump threshold, rather than in anything the floatable tag (and so the water tag,
 * which every BuildCraft fluid is in) names. A fluid that is not BuildCraft's is still judged by that tag, as vanilla
 * does.
 *^/
@Mixin(FloatGoal.class)
abstract class FloatGoalMixin {

    @WrapOperation(method = "canUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;isInFluidDeeperThan(DLnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$floats(Mob mob, double depth, TagKey<Fluid> tag, Operation<Boolean> original) {
        // A goal built for another tag (a mob that floats on something else) keeps vanilla's test.
        return tag == FluidTags.ENTITY_FLOATABLE
                ? ((FabricFluidPhysics.Interaction) ((EntityAccessor) mob).buildcraft$fluidInteraction())
                        .buildcraft$inSwimmableDeeperThan(depth, tag)
                : original.call(mob, depth, tag);
    }
}
*///?}
