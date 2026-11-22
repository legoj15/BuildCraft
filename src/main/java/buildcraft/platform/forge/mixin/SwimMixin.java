/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
//? if >=26.3 {
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.level.material.Fluid;

import buildcraft.platform.forge.ForgeFluidPhysics;

/^*
 * {@code FloatGoalMixin} for the brain-driven mobs: vanilla's {@code Swim} behaviour (villagers, frogs, allays...) makes
 * the same floatable-tag test as the goal, and NeoForge replaces it by the fluid type's {@code canSwim} in both. Same
 * answer, same replaced call; Forge's patch of {@code Swim} is the same disjunct it adds to the goal.
 *
 * <p><b>Injection point.</b> The call to {@code Mob.isInFluidDeeperThan(double, TagKey)} inside
 * {@code checkExtraStartConditions}, named by its full descriptor because the generic base class gives the method a
 * bridge with another one. No ordinal, no local.
 *^/
@Mixin(Swim.class)
abstract class SwimMixin {
    @WrapOperation(
            method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;isInFluidDeeperThan(DLnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$floats(Mob mob, double depth, TagKey<Fluid> tag, Operation<Boolean> original) {
        return tag == FluidTags.ENTITY_FLOATABLE
                ? ForgeFluidPhysics.interaction(mob).buildcraft$inSwimmableDeeperThan(depth, tag)
                : original.call(mob, depth, tag);
    }
}
//?} else {
/^import net.minecraft.world.entity.ai.behavior.Swim;

// Forge 1.21.1's and 1.21.11's (Forge 52, 61) Swim ORs in canSwimInFluidType, which answers from the fluid type's own
// swim flag. Forge 64/65's (26.1, 26.2) does too, and its tag test is isInWater() && getFluidHeight(WATER), which
// EntityFluidInteractionMixin already answers per type there (see FloatGoalMixin). Empty, so the mixin config names the
// same classes on every Forge line.
@Mixin(Swim.class)
abstract class SwimMixin {
}
^///?}
*///?}
