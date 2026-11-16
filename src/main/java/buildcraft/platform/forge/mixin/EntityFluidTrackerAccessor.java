/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.level.material.Fluid;

/^*
 * One fluid an entity's {@code EntityFluidInteraction} tracks this tick, for {@code EntityFluidInteractionMixin}: the
 * private nested {@code Tracker} that vanilla keeps one of per fluid (Forge adds its own per-type trackers beside them,
 * which carry no fluid, and are held in another map).
 *
 * <p>An accessor needs no injection point, so nothing here can drift with Forge's patch of the class: the three fields
 * are vanilla's and Forge only makes {@code fluidType} nullable (for its per-type trackers, which this list never holds).
 *^/
@Mixin(targets = "net.minecraft.world.entity.EntityFluidInteraction$Tracker")
public interface EntityFluidTrackerAccessor {
    @Accessor("fluidType")
    Holder<Fluid> buildcraft$fluid();

    @Accessor("height")
    double buildcraft$height();

    @Accessor("eyesInside")
    boolean buildcraft$eyesInside();
}
*///?}
