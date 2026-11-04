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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.hook.FireReactiveBlock;

/^*
 * NeoForge's {@code getFlammability}, {@code getFireSpreadSpeed} and {@code onCaughtFire} on Fabric, for a
 * {@link FireReactiveBlock}. Fabric's {@code FlammableBlockRegistry} holds fixed numbers per block, and BuildCraft's
 * answers follow a config option, so the fire block asks the block itself.
 *
 * <p>Gap: NeoForge also lights a flammable block from a dispensed flint and steel ({@code onCaughtFire} there); vanilla
 * and Fabric do not, so a dispenser does not detonate a BuildCraft gas.
 *^/
@Mixin(FireBlock.class)
abstract class FireBlockMixin {

    @Inject(method = "getBurnOdds(Lnet/minecraft/world/level/block/state/BlockState;)I", at = @At("HEAD"),
            cancellable = true)
    private void buildcraft$burnOdds(BlockState state, CallbackInfoReturnable<Integer> cir) {
        if (state.getBlock() instanceof FireReactiveBlock block) {
            cir.setReturnValue(block.fireBurnOdds(state));
        }
    }

    @Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/block/state/BlockState;)I", at = @At("HEAD"),
            cancellable = true)
    private void buildcraft$igniteOdds(BlockState state, CallbackInfoReturnable<Integer> cir) {
        if (state.getBlock() instanceof FireReactiveBlock block) {
            cir.setReturnValue(block.fireIgniteOdds(state));
        }
    }

    /^* Where NeoForge calls onCaughtFire: the burn roll passed, before the fire replaces or removes the block. *^/
    @Inject(method = "checkBurnOut", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I",
            ordinal = 1))
    private void buildcraft$catchFire(Level level, BlockPos pos, int chance, RandomSource random, int age,
                                      CallbackInfo ci) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FireReactiveBlock block) {
            block.catchFire(state, level, pos);
        }
    }
}
*///?}
