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

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import buildcraft.platform.fabric.FabricVanillaExtensions;

/^*
 * NeoForge's {@code getExpDrop} on Fabric: while {@code FabricVanillaExtensions#expDrop} runs a block's after-break
 * step, the experience it pops is counted instead of spawned. Every other call spawns its orbs as usual.
 *^/
@Mixin(Block.class)
abstract class BlockExperienceMixin {

    @Inject(method = "popExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD"), cancellable = true)
    private void buildcraft$catchExperience(ServerLevel level, BlockPos pos, int amount, CallbackInfo ci) {
        if (FabricVanillaExtensions.catchExperience(amount)) {
            ci.cancel();
        }
    }
}
*///?}
