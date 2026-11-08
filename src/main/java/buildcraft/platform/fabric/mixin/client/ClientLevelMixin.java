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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.fabric.client.FabricBlockParticles;

/^*
 * NeoForge's {@code IClientBlockExtensions#addDestroyEffects} and {@code #addHitEffects} calls in its patched
 * {@code ClientLevel}, on Fabric: a block registered through {@code ClientRegistrationSinks#blockParticles} spawns its
 * own break and hit particles ({@link FabricBlockParticles}), and vanilla's are skipped when it says it handled them.
 *^/
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    /^* NeoForge's check sits in the method's first condition, after the air test: the same order here. *^/
    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
    private void buildcraft$destroyEffects(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!state.isAir() && FabricBlockParticles.addDestroyEffects(state, (ClientLevel) (Object) this, pos,
                minecraft.particleEngine)) {
            ci.cancel();
        }
    }

    //? if >=26.3 {
    /^* Only the particle call is conditional, so the hit sound before it still plays. *^/
    @WrapWithCondition(method = "addBreakingBlockEffects", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;addBreakingParticles(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private boolean buildcraft$hitEffects(ClientLevel level, BlockPos pos, Direction face, BlockState state) {
        return !FabricBlockParticles.addHitEffects(state, level, pos, face, minecraft.particleEngine);
    }
    //?} else {
    /^// Before 26.3 the hit particles are the whole method (the sound plays elsewhere), so it is skipped outright.
    @Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
    private void buildcraft$hitEffects(BlockPos pos, Direction face, CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && FabricBlockParticles.addHitEffects(state, level, pos, face, minecraft.particleEngine)) {
            ci.cancel();
        }
    }
    ^///?}
}
*///?}
