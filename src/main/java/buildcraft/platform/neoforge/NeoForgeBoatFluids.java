/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import net.minecraft.world.entity.Entity;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import buildcraft.platform.BoatFluidMotion;

/**
 * The NeoForge half of {@link BoatFluidMotion}: a listener on the end of every boat's tick, on both logical sides.
 * Whether a boat floats on a BuildCraft fluid at all is NeoForge's own {@code canBoatInFluid}, answered by the fluid
 * type ({@link NeoForgeFlowingFluids}); NeoForge has no hook for how fast it sails there, so this adds the drag.
 * Fabric does the same from its boat mixin.
 */
final class NeoForgeBoatFluids {
    private NeoForgeBoatFluids() {}

    /** Registers the listener on the game bus; the entrypoint calls it once. */
    static void register() {
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class, event -> {
            Entity entity = event.getEntity();
            if (isBoat(entity)) {
                BoatFluidMotion.tick(entity);
            }
        });
    }

    /** Boats and chest boats, rafts included: the vanilla boat class, which moved twice (1.21.10, 1.21.11). */
    private static boolean isBoat(Entity entity) {
        //? if >=1.21.11 {
        return entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat;
        //?} elif >=1.21.10 {
        /*return entity instanceof net.minecraft.world.entity.vehicle.AbstractBoat;
        *///?} else {
        /*return entity instanceof net.minecraft.world.entity.vehicle.Boat;
        *///?}
    }
}
//?}
