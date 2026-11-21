/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.10 {
/^import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.platform.forge.client.ForgeBlockParticles;
^///?}
import net.minecraft.client.particle.ParticleEngine;

/^*
 * The 1.21.1 twin of {@code ClientLevelMixin}: NeoForge's {@code IClientBlockExtensions#addDestroyEffects} and
 * {@code #addHitEffects} for a block registered through {@code ClientRegistrationSinks#blockParticles}
 * ({@code ForgeBlockParticles} says why Forge's own copy of that hook is not used). This class carries nothing on 26.3.
 *
 * <p><b>Injection points.</b> The heads of {@code destroy} (vanilla's break particles, which Forge 52 patches to ask
 * {@code IClientBlockExtensions} first) and of {@code addBlockHitEffects} (Forge 52's hit-particle entry point, called
 * from {@code MultiPlayerGameMode} in place of {@code crack}, which asks the same extension and then cracks): a handled
 * call is cancelled before Forge's own test.
 *^/
//? if <1.21.10 {
/^@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
    @Shadow
    protected ClientLevel level;

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void buildcraft$destroyEffects(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!state.isAir() && ForgeBlockParticles.addDestroyEffects(state, level, pos, (ParticleEngine) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "addBlockHitEffects", at = @At("HEAD"), cancellable = true)
    private void buildcraft$hitEffects(BlockPos pos, BlockHitResult target, CallbackInfo ci) {
        BlockState state = level.getBlockState(pos);
        if (ForgeBlockParticles.addHitEffects(state, level, target, (ParticleEngine) (Object) this)) {
            ci.cancel();
        }
    }
}
^///?} else {
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
}
//?}
*///?}
