/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.fabric.FabricGameHooks;

/^* The dug position for the break-speed hook ({@link FabricGameHooks#digSpeedAt}): NeoForge's destroy-progress maths
 *  calls its position-aware {@code getDestroySpeed(state, pos)} where vanilla's has none. *^/
@Mixin(BlockBehaviour.class)
abstract class BlockBehaviourMixin {

    @WrapOperation(method = "getDestroyProgress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F"))
    private float buildcraft$digSpeedAt(Player player, BlockState state, Operation<Float> original,
            @Local(argsOnly = true) BlockPos pos) {
        return FabricGameHooks.digSpeedAt(pos, () -> original.call(player, state));
    }
}
*///?}
