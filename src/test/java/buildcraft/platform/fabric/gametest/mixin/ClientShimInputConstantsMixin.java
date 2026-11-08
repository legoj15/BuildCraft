/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.gametest.mixin;

//? if fabric && <1.21.10 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.InputConstants;

import buildcraft.platform.fabric.gametest.ClientGameTestShim;

/^*
 * The client game test's input on 1.21.1 (the game-test mod only, on Loom's clientGameTest run): what Fabric API's own
 * runner does from 1.21.4. Vanilla never attaches the real keyboard and mouse callbacks and never grabs the cursor, so
 * a person using the machine cannot steer the run (or lose their cursor to it), and a key reads as held only while
 * {@code ClientGameTestShim.TestInput} holds it. Listed in the gametest mixin config only on the pre-1.21.10 nodes.
 *^/
@Mixin(InputConstants.class)
abstract class ClientShimInputConstantsMixin {

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void buildcraft$testKeysOnly(long window, int keyCode, CallbackInfoReturnable<Boolean> cir) {
        if (ClientGameTestShim.ENABLED) {
            cir.setReturnValue(ClientGameTestShim.TestInput.isKeyDown(keyCode));
        }
    }

    @Inject(method = {"setupKeyboardCallbacks", "setupMouseCallbacks"}, at = @At("HEAD"), cancellable = true)
    private static void buildcraft$noRealInput(CallbackInfo ci) {
        if (ClientGameTestShim.ENABLED) {
            ci.cancel();
        }
    }

    @Inject(method = "grabOrReleaseMouse", at = @At("HEAD"), cancellable = true)
    private static void buildcraft$noCursorGrab(CallbackInfo ci) {
        if (ClientGameTestShim.ENABLED) {
            ci.cancel();
        }
    }
}
*///?}
