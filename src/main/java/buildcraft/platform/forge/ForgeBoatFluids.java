/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.Map;

import com.google.common.collect.MapMaker;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;

import buildcraft.platform.BoatFluidMotion;

/^*
 * The Forge half of {@link BoatFluidMotion}: the drag on a boat that sails a sticky BuildCraft fluid, on both logical
 * sides, once per tick of the boat. Whether a boat floats on the fluid at all is Forge's own: its boat patches ask the
 * fluid type ({@code canBoatInFluid}), which {@link ForgeFlowingFluids.Type} answers from the declared properties.
 *
 * <p><b>No per-boat hook, so the level's tick.</b> NeoForge ticks the drag from {@code EntityTickEvent.Post}; Forge has
 * no entity tick event (its living tick event is for living entities, and a boat is not one), and Fabric uses a mixin.
 * Without a mixin this keeps the boats of each level (a boat joining the level adds itself, leaving removes it, both
 * events fire on both sides) and runs {@link BoatFluidMotion#tick} for each of them on {@code LevelTickEvent.Post}, which
 * runs after every entity of the level has ticked, so the factor lands on a boat that has moved this tick, as the
 * other two loaders do. The difference is the order within the tick: it comes after the whole level's entities rather
 * than right after the boat, so a rider's own tick has already run. The drag is a plain factor on the horizontal speed,
 * so nothing reads the boat's motion in between.
 *
 * <p><b>Only a boat that ticked.</b> The level's tick runs whether or not a boat did (a frozen {@code /tick freeze}
 * level, a chunk without entity ticking), where the per-boat hooks of the other loaders do not. Each tracked boat
 * remembers its {@code tickCount}, which the entity's own {@code tick} bumps once, and is dragged only when it moved
 * on since the last level tick.
 *
 * <p>The maps hold boats weakly and by identity: a client entity and a server one share an id and compare equal, and
 * a boat whose join another listener cancelled (it gets no leave event) must not be pinned. A boat found removed is
 * skipped.
 ^/
final class ForgeBoatFluids {
    /^* Per level, each boat with the {@code tickCount} it was last dragged at. ^/
    private static final Map<Level, Map<Entity, Integer>> BOATS = new MapMaker().weakKeys().makeMap();
    private static boolean registered;

    private ForgeBoatFluids() {}

    /^* Registers the listeners on the three events' own buses; once, however many fluid registrars subscribe. ^/
    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        EntityJoinLevelEvent.BUS.addListener((EntityJoinLevelEvent event) -> {
            if (event.getEntity() instanceof AbstractBoat boat) {
                BOATS.computeIfAbsent(event.getLevel(), level -> new MapMaker().weakKeys().<Entity, Integer>makeMap())
                        .put(boat, boat.tickCount);
            }
        });
        EntityLeaveLevelEvent.BUS.addListener((EntityLeaveLevelEvent event) -> {
            Map<Entity, Integer> boats = BOATS.get(event.getLevel());
            if (boats != null) {
                boats.remove(event.getEntity());
            }
        });
        TickEvent.LevelTickEvent.Post.BUS.addListener((TickEvent.LevelTickEvent.Post event) -> {
            Map<Entity, Integer> boats = BOATS.get(event.level());
            if (boats == null) {
                return;
            }
            for (Map.Entry<Entity, Integer> tracked : boats.entrySet()) {
                Entity boat = tracked.getKey();
                if (boat.isRemoved() || tracked.getValue() == boat.tickCount) {
                    continue;
                }
                boats.put(boat, boat.tickCount);
                BoatFluidMotion.tick(boat);
            }
        });
    }
}
*///?}
