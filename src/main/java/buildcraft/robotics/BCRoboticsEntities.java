/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import buildcraft.robotics.entity.EntityRobot;

public class BCRoboticsEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BCRobotics.MODID);

    /** The robot. {@code MobCategory.MISC} keeps it out of mob-cap/spawning entirely; the 0.25 cube matches
     * 7.1.x's model and {@code fireImmune} matches its lava behaviour (a robot flying over lava should not
     * catch fire). Tracking mirrors the quarry rig: a short range with a per-tick update interval, because a
     * robot's visible position is driven by its own movement rather than interpolation from a block entity. */
    public static final Supplier<EntityType<EntityRobot>> ROBOT = ENTITIES.register(
            "robot",
            (key) -> EntityType.Builder.<EntityRobot>of(EntityRobot::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .fireImmune()
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    //? if >=1.21.10 {
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, key)));
                    //?} else {
                    /*.build(key.toString()));*/
                    //?}

    public static void init(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
