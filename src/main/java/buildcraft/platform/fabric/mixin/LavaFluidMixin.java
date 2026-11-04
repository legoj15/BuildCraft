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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.LavaFluid;

import buildcraft.platform.hook.FireReactiveBlock;

/^* NeoForge's {@code ignitedByLava} on Fabric: lava asks a {@link FireReactiveBlock} whether it lights above it. *^/
@Mixin(LavaFluid.class)
abstract class LavaFluidMixin {

    @Inject(method = "isFlammable(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    // The very guard the target method opens with; LevelReader has no undeprecated chunk-presence query (hasChunk too).
    @SuppressWarnings("deprecation")
    private void buildcraft$lavaIgnites(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Vanilla's own guard first: never load a chunk to answer.
        if (level.isInsideBuildHeight(pos.getY()) && !level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FireReactiveBlock block) {
            cir.setReturnValue(block.lavaIgnites(state));
        }
    }
}
*///?}
