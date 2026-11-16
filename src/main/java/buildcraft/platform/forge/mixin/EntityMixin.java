/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.Fluid;

import buildcraft.platform.forge.ForgeFluidPhysics;

/^*
 * NeoForge's per-type water-like checks in {@code Entity.baseTick} and {@code updateFluidInteraction}, for BuildCraft's
 * fluids on Forge ({@link ForgeFluidPhysics}): the entity is "in water", and its eyes are "in water", only in a
 * water-like fluid, not in any water-tag fluid (every BuildCraft fluid is in the tag); a fluid that is not water-like
 * damps the fall distance by its type's modifier, as NeoForge's {@code getFallDistanceModifier} does.
 *
 * <p><b>Injection points.</b> Each is a named call in a vanilla method that Forge's patch of {@code Entity} leaves in place,
 * and none takes an ordinal or a captured local:
 * <ul>
 * <li>{@code baseTick}'s {@code isEyeInFluid(WATER)}, the {@code wasEyeInWater} read (Forge patches no line near it);</li>
 * <li>{@code updateFluidInteraction}'s {@code fluidInteraction.isInFluid(WATER)}, the {@code inWater} read that feeds the
 * splash, the fall reset and {@code wasTouchingWater} (Forge's change to that method is the current-pushing block after
 * it, which it replaces with {@code applyCurrentTo(this)}: Forge applies each fluid type's own current by its own motion
 * scale, so unlike Fabric no current needs adding here);</li>
 * <li>{@code baseTick}'s call to {@code checkBelowWorld}, which follows Forge's {@code getFluidFallDistanceModifier(LAVA)}
 * line (Forge applies only lava's modifier, where NeoForge applies every non-water-like type's): the same point NeoForge
 * has its {@code else if (!isInWater())} branch at.</li>
 * </ul>
 *^/
@Mixin(Entity.class)
abstract class EntityMixin {
    @Shadow
    public double fallDistance;

    @Shadow
    public abstract boolean isInWater();

    @Shadow
    public abstract boolean isInLava();

    @WrapOperation(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$eyeInWater(Entity self, TagKey<Fluid> tag, Operation<Boolean> original) {
        return tag == FluidTags.WATER
                ? ForgeFluidPhysics.interaction(self).buildcraft$eyeInWaterLike()
                : original.call(self, tag);
    }

    @WrapOperation(method = "updateFluidInteraction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityFluidInteraction;isInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$inWater(EntityFluidInteraction interaction, TagKey<Fluid> tag,
                                       Operation<Boolean> original) {
        return tag == FluidTags.WATER
                ? ((ForgeFluidPhysics.Interaction) interaction).buildcraft$inWaterLike()
                : original.call(interaction, tag);
    }

    /^* NeoForge's {@code fallDistance *= getFallDistanceModifier}, after the lava halving and before the void check. ^/
    @Inject(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;checkBelowWorld()V"))
    private void buildcraft$fluidFallDistance(CallbackInfo ci) {
        if (!isInLava() && !isInWater()) {
            Entity self = (Entity) (Object) this;
            fallDistance *= ForgeFluidPhysics.interaction(self).buildcraft$fallDistanceModifier(self);
        }
    }
}
*///?}
