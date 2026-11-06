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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.fabric.FabricLoaderHookRoutes;
import buildcraft.platform.fabric.FabricProtection;

/^*
 * NeoForge's block-snapshot capture on Fabric, for {@link FabricProtection#useItemOn} alone: while a machine's item use
 * runs, each block it sets is recorded as it stood before, so the placement can be asked about and put back. And
 * NeoForge's onLoad point: a server level's block entities that joined it are told at the head of its block-entity
 * tick ({@link FabricLoaderHookRoutes#levelTicksBlockEntities}).
 *^/
@Mixin(Level.class)
abstract class LevelMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void buildcraft$captureBeforeSetBlock(BlockPos pos, BlockState state, int flags, int limit,
                                                  CallbackInfoReturnable<Boolean> cir) {
        FabricProtection.beforeSetBlock((Level) (Object) this, pos);
    }

    /^* Where NeoForge runs its fresh block entities' onLoad: the head of a level's block-entity tick. *^/
    @Inject(method = "tickBlockEntities()V", at = @At("HEAD"))
    private void buildcraft$joinedBeforeBlockEntitiesTick(CallbackInfo ci) {
        Level level = (Level) (Object) this;
        if (!level.isClientSide()) {
            FabricLoaderHookRoutes.levelTicksBlockEntities(level);
        }
    }
}
*///?}
