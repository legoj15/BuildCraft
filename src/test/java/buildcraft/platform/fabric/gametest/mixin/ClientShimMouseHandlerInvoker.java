/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.gametest.mixin;

//? if fabric && <1.21.10 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.MouseHandler;

/^*
 * Vanilla's mouse callbacks (the game-test mod only), through which {@code ClientGameTestShim.TestInput} sends the
 * client game test's button presses and cursor moves on 1.21.1, as Fabric API's runner does from 1.21.4. Listed in the
 * gametest mixin config only on the pre-1.21.10 nodes.
 *^/
@Mixin(MouseHandler.class)
public interface ClientShimMouseHandlerInvoker {

    @Invoker("onPress")
    void buildcraft$onPress(long window, int button, int action, int modifiers);

    @Invoker("onMove")
    void buildcraft$onMove(long window, double x, double y);
}
*///?}
