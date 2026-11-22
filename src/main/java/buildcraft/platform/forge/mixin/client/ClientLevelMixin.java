/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.10 {
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.3 {
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
//?}

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
//? if >=26.3 {
import net.minecraft.core.Direction;
//?}
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.forge.client.ForgeBlockParticles;
//?} else {
/^import net.minecraft.client.multiplayer.ClientLevel;
^///?}

/^*
 * NeoForge's {@code IClientBlockExtensions#addDestroyEffects} and {@code #addHitEffects} for a block registered through
 * {@code ClientRegistrationSinks#blockParticles} ({@link ForgeBlockParticles} says why Forge's own copy of that hook is
 * not used).
 *
 * <p><b>Injection points.</b> {@code addDestroyBlockEffect}: the head, before Forge's patched-in
 * {@code IClientBlockExtensions} test (which then never runs for a cancelled call). {@code addBreakingBlockEffects}: the
 * one call to {@code addBreakingParticles}, which Forge's patch leaves in place behind its own extension test and the
 * vanilla render-shape test; wrapped so the hit sound before it still plays and another mod's wrap composes.
 *
 * <p>On 1.21.1 it carries nothing: there the block particles go through {@code ParticleEngineMixin}.
 *^/
//? if >=1.21.10 {
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
    private void buildcraft$destroyEffects(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!state.isAir() && ForgeBlockParticles.addDestroyEffects(state, (ClientLevel) (Object) this, pos,
                minecraft.particleEngine)) {
            ci.cancel();
        }
    }

    //? if >=26.3 {
    @WrapWithCondition(method = "addBreakingBlockEffects", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;addBreakingParticles(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private boolean buildcraft$hitEffects(ClientLevel level, BlockPos pos, Direction face, BlockState state) {
        return !ForgeBlockParticles.addHitEffects(state, level, pos, face, minecraft.particleEngine);
    }
    //?} else {
    /^// Forge 61, 64 and 65 (1.21.11, 26.1, 26.2): the head of Forge's own addBreakingBlockEffect(BlockPos, BlockHitResult),
    // the overload Minecraft#continueAttack calls (with the crosshair hit), before Forge's IClientBlockExtensions test and
    // vanilla's particles (the (BlockPos, Direction) overload it then calls). The hit sound plays elsewhere on these lines.
    @Inject(method = "addBreakingBlockEffect(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;)V",
            at = @At("HEAD"), cancellable = true)
    private void buildcraft$hitEffects(BlockPos pos, net.minecraft.world.phys.BlockHitResult hit, CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && ForgeBlockParticles.addHitEffects(state, level, hit, minecraft.particleEngine)) {
            ci.cancel();
        }
    }
    ^///?}
}
//?} else {
/^@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
}
^///?}
*///?}
