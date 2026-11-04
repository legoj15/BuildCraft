/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.function.BooleanSupplier;

import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * A no-data recipe/datapack condition (formerly BuildCraft's {@code RfEnabledCondition}), backed by a test from
 * common code: {@code buildcraftunofficial:rf_enabled} drops the RF pipe recipes when {@code disableRfPipe} is set.
 *
 * <p>Conditions are evaluated server-side during the datapack reload ({@code RecipeManager.prepare}), after configs
 * are loaded, and the filtered recipe set is what syncs to clients, so the gate is server-authoritative. It is a
 * load-time snapshot: a config change takes effect on the next world load or {@code /reload}.
 *
 * <p>The condition carries no data, so it serialises as the empty object {@code { "type": "<id>" }}. It never
 * touches {@link IContext} (whose shape diverges across MC lines), so the class needs no Stonecutter directives.
 */
final class NeoForgeFlagCondition implements ICondition {
    private final String name;
    private final BooleanSupplier test;
    /** No-data condition: always decodes to this instance and encodes nothing. */
    final MapCodec<NeoForgeFlagCondition> codec = MapCodec.unit(this);

    NeoForgeFlagCondition(String name, BooleanSupplier test) {
        this.name = name;
        this.test = test;
    }

    @Override
    public boolean test(IContext context) {
        return test.getAsBoolean();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return codec;
    }

    @Override
    public String toString() {
        return name;
    }
}
//?}
