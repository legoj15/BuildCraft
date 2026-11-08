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
//? if >=1.21.10 {
import net.minecraft.world.level.redstone.Orientation;
//?}

import buildcraft.platform.fabric.FabricGameHooks;

/^*
 * NeoForge's {@code NeighborNotifyEvent} on Fabric ({@link FabricGameHooks#onNeighborNotify}), at the two server-level
 * methods NeoForge posts it from, before the neighbours are told ({@code DiodeBlockMixin} is the third site). Before
 * 26.1 also NeoForge's entity-join point for a player ({@code FabricGameHooks.fireEntityJoin}).
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
                                                 //? if >=1.21.10 {
                                                 @Nullable Orientation orientation,
                                                 //?}
                                                 CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        FabricGameHooks.fireNeighborNotify(self, pos, self.getBlockState(pos));
    }

    //? if <26.1 {
    /^// NeoForge's entity-join point for a player (a new, respawned or teleported one), before it is added; other
    // entities join through PersistentEntitySectionManagerMixin. 26.1's Fabric API posts ALLOW_LOAD here itself.
    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void buildcraft$playerJoin(net.minecraft.server.level.ServerPlayer player, CallbackInfo ci) {
        if (!FabricGameHooks.fireEntityJoin(player, (ServerLevel) (Object) this, false)) {
            ci.cancel();
        }
    }
    ^///?}
}
*///?}
