/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin.client;

//? if fabric {
/*import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
//? if <1.21.10 {
/^import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.fabric.client.FabricBlockParticles;
^///?}

import buildcraft.platform.fabric.FabricGameHooks;

/^*
 * NeoForge's client {@code LevelEvent.Unload} on Fabric ({@code FabricGameHooks#onLevelUnload}): the three places the
 * client lets go of its level (another level set, a disconnect, a cleared level), just before it does. On 1.21.1 also
 * NeoForge's {@code addHitEffects} check ({@code ParticleEngine.addBlockHitEffects} there): a block registered through
 * {@code ClientRegistrationSinks#blockParticles} spawns its own hit particles in place of vanilla's {@code crack}.
 *^/
@Mixin(Minecraft.class)
abstract class MinecraftMixin {

    @Shadow
    public ClientLevel level;

    // 1.21.10's disconnect has no third flag (1.21.11 added it, the two-flag one forwarding to it).
    //? if >=1.21.11 {
    @Inject(method = {"setLevel", "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", "clearClientLevel"},
    //?} else {
    /^@Inject(method = {"setLevel", "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", "clearClientLevel"},
    ^///?}
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;",
                    opcode = Opcodes.PUTFIELD))
    private void buildcraft$levelUnload(CallbackInfo ci) {
        if (level != null) {
            FabricGameHooks.fireClientLevelUnload(level);
        }
    }

    //? if <1.21.10 {
    /^@WrapWithCondition(method = "continueAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;crack(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)V"))
    private boolean buildcraft$hitEffects(ParticleEngine engine, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !FabricBlockParticles.addHitEffects(state, level, pos, face, engine);
    }
    ^///?}
}
*///?}
