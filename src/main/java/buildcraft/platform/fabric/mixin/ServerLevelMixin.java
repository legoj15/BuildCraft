/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.redstone.Orientation;

import buildcraft.platform.fabric.FabricGameHooks;

/^*
 * NeoForge's {@code NeighborNotifyEvent} on Fabric ({@link FabricGameHooks#onNeighborNotify}), at the two server-level
 * methods NeoForge posts it from, before the neighbours are told ({@code DiodeBlockMixin} is the third site).
 *^/
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    @Inject(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V",
            at = @At("HEAD"))
    private void buildcraft$neighborNotify(BlockPos pos, Block sourceBlock, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        FabricGameHooks.fireNeighborNotify(self, pos, self.getBlockState(pos));
    }

    @Inject(method = "updateNeighborsAtExceptFromFacing", at = @At("HEAD"))
    private void buildcraft$neighborNotifyExcept(BlockPos pos, Block sourceBlock, Direction skip,
                                                 @Nullable Orientation orientation, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        FabricGameHooks.fireNeighborNotify(self, pos, self.getBlockState(pos));
    }
}
*///?}
