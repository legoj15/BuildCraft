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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.fabric.FabricGameHooks;

/^* NeoForge's {@code NeighborNotifyEvent} for a repeater or comparator updating the block in front ({@code ServerLevelMixin}). *^/
@Mixin(DiodeBlock.class)
abstract class DiodeBlockMixin {

    @Inject(method = "updateNeighborsInFront", at = @At("HEAD"))
    private void buildcraft$neighborNotify(Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        FabricGameHooks.fireNeighborNotify(level, pos, level.getBlockState(pos));
    }
}
*///?}
