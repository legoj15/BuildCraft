/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.level.material.Fluid;

/^* One fluid an entity's {@code EntityFluidInteraction} tracks this tick, for {@code EntityFluidInteractionMixin}. On
 *  26.1 and 26.2 a tracker stands for a whole tag, and the mixin keeps its own per-fluid record instead; before 26.1
 *  there is no tracker class, and this targets {@code Entity} with nothing in it. ^/
//? if >=26.1 {
@Mixin(targets = "net.minecraft.world.entity.EntityFluidInteraction$Tracker")
//?} else {
/^@Mixin(net.minecraft.world.entity.Entity.class)
^///?}
public interface EntityFluidTrackerAccessor {
    //? if >=26.3 {
    @Accessor("fluidType")
    Holder<Fluid> buildcraft$fluid();

    @Accessor("height")
    double buildcraft$height();

    @Accessor("eyesInside")
    boolean buildcraft$eyesInside();
    //?}
}
*///?}
