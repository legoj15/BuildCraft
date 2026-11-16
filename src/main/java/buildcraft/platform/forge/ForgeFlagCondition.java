/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.BooleanSupplier;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;

import net.minecraftforge.common.crafting.conditions.ICondition;

/^*
 * The Forge twin of {@code NeoForgeFlagCondition}: a no-data datapack condition backed by a test from common code
 * ({@code buildcraftunofficial:rf_enabled} drops the RF pipe recipes when {@code disableRfPipe} is set). It serialises
 * as the empty object {@code { "type": "<id>" }}, and is a load-time snapshot of the test.
 *
 * <p>Forge's {@link ICondition#test} takes the condition context <i>and</i> the ops the JSON is being decoded with
 * (NeoForge's takes the context alone); this condition reads neither, so the class needs no Stonecutter directives
 * even though {@link IContext} differs by Forge line.
 *
 * <p>Where a condition is written is not NeoForge's. NeoForge reads a {@code neoforge:conditions} list at the top of a
 * datum; Forge 26.3 reads one {@code forge:condition} object at the top of any datum (its patched
 * {@code ResourceManagerRegistryLoadTask} and {@code SimpleJsonResourceReloadListener} take it off before the codec runs,
 * recipes included), with the codec registry this file's codec goes in naming the type. The 18 RF pipe recipes carry
 * {@code neoforge:conditions}, so turning that into the Forge key is the data pipeline's job (the Forge build's resource
 * rewrite, {@code DataForge}), not this class's.
 *^/
final class ForgeFlagCondition implements ICondition {
    private final String name;
    private final BooleanSupplier test;
    /^* No-data condition: always decodes to this instance and encodes nothing. ^/
    final MapCodec<ForgeFlagCondition> codec = MapCodec.unit(this);

    ForgeFlagCondition(String name, BooleanSupplier test) {
        this.name = name;
        this.test = test;
    }

    @Override
    public boolean test(IContext context, DynamicOps<?> ops) {
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
*///?}
