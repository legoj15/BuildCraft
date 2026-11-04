/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core;

import java.util.function.Supplier;

import net.neoforged.bus.api.IEventBus;

import buildcraft.core.gen.SpringFeature;
import buildcraft.platform.registry.BCFeatureRegistrar;

public class BCCoreFeatures {
    // The registry SPRING lands in (feature types from 26.3, features below) is BCFeatureRegistrar's business.
    public static final BCFeatureRegistrar FEATURES = new BCFeatureRegistrar(BCCore.MODID);

    public static final Supplier<?> SPRING = FEATURES.register("spring", SpringFeature::new);

    public static void init(IEventBus modEventBus) {
        FEATURES.subscribe();
    }
}
