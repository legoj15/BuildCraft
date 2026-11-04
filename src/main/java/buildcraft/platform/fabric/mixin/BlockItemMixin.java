/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.fabric.FabricVanillaExtensions;
import buildcraft.platform.hook.PositionalSoundBlock;

/^*
 * NeoForge's position-aware placing sound ({@code BlockItem.place}'s {@code getSoundType(level, pos, player)} and
 * {@code getPlaceSound(state, level, pos, player)}): a {@link PositionalSoundBlock} is placed with its own sound.
 *^/
@Mixin(BlockItem.class)
abstract class BlockItemMixin {

    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType buildcraft$placedSoundType(BlockState state, Operation<SoundType> original,
                                                 @Local Level level, @Local BlockPos pos, @Local Player player) {
        return state.getBlock() instanceof PositionalSoundBlock
                ? FabricVanillaExtensions.positionalSoundType(state, level, pos, player)
                : original.call(state);
    }

    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;getPlaceSound(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/sounds/SoundEvent;"))
    private SoundEvent buildcraft$placeSound(BlockItem item, BlockState state, Operation<SoundEvent> original,
                                             @Local Level level, @Local BlockPos pos, @Local Player player) {
        return state.getBlock() instanceof PositionalSoundBlock
                ? FabricVanillaExtensions.positionalSoundType(state, level, pos, player).getPlaceSound()
                : original.call(item, state);
    }
}
*///?}
