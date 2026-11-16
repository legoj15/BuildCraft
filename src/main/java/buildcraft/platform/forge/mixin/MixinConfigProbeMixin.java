/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

import buildcraft.api.core.BCLog;

/^*
 * The proof that BuildCraft's Forge mixin config is read (docs/forge-port.md, "Mixins"): one harmless injection at the
 * head of the server's main loop that logs one debug line. It exists so every later Forge mixin has a known-good
 * declaration to follow, and so a broken declaration shows up as a missing line here and not as a missing hook somewhere
 * else. Nothing depends on it; it can stay as the canary or go once a real mixin carries the same proof.
 *^/
@Mixin(MinecraftServer.class)
abstract class MixinConfigProbeMixin {

    @Inject(method = "runServer", at = @At("HEAD"))
    private void buildcraft$mixinConfigProbe(CallbackInfo ci) {
        BCLog.logger.debug("BuildCraft's Forge mixin config is applied: MinecraftServer#runServer entered");
    }
}
*///?}
