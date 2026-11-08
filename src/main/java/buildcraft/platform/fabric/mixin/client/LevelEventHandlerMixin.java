/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin.client;

//? if fabric {
/*import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.multiplayer.ClientLevel;
//? if >=1.21.10 {
import net.minecraft.client.renderer.LevelEventHandler;
//?} else {
/^import net.minecraft.client.renderer.LevelRenderer;
^///?}
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.fabric.FabricVanillaExtensions;
import buildcraft.platform.hook.PositionalSoundBlock;

/^*
 * NeoForge's position-aware block-break sound (level event 2001, {@code getSoundType(level, pos, null)}): a
 * {@link PositionalSoundBlock} breaks with its own sound while it can still tell it.
 *^/
//? if >=1.21.10 {
@Mixin(LevelEventHandler.class)
//?} else {
/^// Before 1.21.10 the level events are played by LevelRenderer itself.
@Mixin(LevelRenderer.class)
^///?}
abstract class LevelEventHandlerMixin {

    //? if >=1.21.10 {
    @Shadow
    @Final
    private ClientLevel level;
    //?} else {
    /^@Shadow
    private ClientLevel level;
    ^///?}

    @WrapOperation(method = "levelEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType buildcraft$breakSound(BlockState state, Operation<SoundType> original,
                                            @Local(argsOnly = true) BlockPos pos) {
        return state.getBlock() instanceof PositionalSoundBlock
                ? FabricVanillaExtensions.positionalSoundType(state, level, pos, null)
                : original.call(state);
    }
}
*///?}
