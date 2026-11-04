/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import buildcraft.platform.hook.ExplosionArmouredBlock;

/^*
 * NeoForge's 4-arg {@code getExplosionResistance} on Fabric: an {@link ExplosionArmouredBlock} adjusts the resistance
 * vanilla worked out for the blast. Vanilla has already folded in the fluid's resistance by then (the larger of the
 * two); the adjustment only ever raises it, so the answer is NeoForge's.
 *^/
@Mixin(ExplosionDamageCalculator.class)
abstract class ExplosionDamageCalculatorMixin {

    @Inject(method = "getBlockExplosionResistance", at = @At("RETURN"), cancellable = true)
    private void buildcraft$armouredResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state,
                                               FluidState fluid, CallbackInfoReturnable<Optional<Float>> cir) {
        Optional<Float> resistance = cir.getReturnValue();
        if (resistance.isPresent() && state.getBlock() instanceof ExplosionArmouredBlock block) {
            cir.setReturnValue(Optional.of(block.explosionResistance(level, pos, explosion, resistance.get())));
        }
    }
}
*///?}
