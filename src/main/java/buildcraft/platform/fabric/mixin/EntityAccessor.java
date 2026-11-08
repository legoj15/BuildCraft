/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.Entity;

/^* The entity's fluid tracking (NeoForge's {@code getFluidInteraction}), for {@code FabricFluidPhysics#interaction}.
 *  Nothing before 26.1, where the tracking is the entity's own. ^/
@Mixin(Entity.class)
public interface EntityAccessor {
    //? if >=26.1 {
    @Accessor("fluidInteraction")
    net.minecraft.world.entity.EntityFluidInteraction buildcraft$fluidInteraction();
    //?}
}
*///?}
