/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FlowingFluid;

/^* Vanilla's protected infinite-source test, for {@code FabricVanillaExtensions#canConvertToSource}. ^/
@Mixin(FlowingFluid.class)
public interface FlowingFluidAccessor {
    @Invoker("canConvertToSource")
    //? if >=1.21.10 {
    boolean buildcraft$canConvertToSource(ServerLevel level);
    //?} else {
    /^boolean buildcraft$canConvertToSource(net.minecraft.world.level.Level level); // any Level on 1.21.1
    ^///?}
}
*///?}
